package com.emergency.ledger;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LedgerExcelService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 1000;
    private static final int MAX_EXPORT_ROWS = 10000;
    private static final List<String> BASE = List.of(
        "台账编号", "原始地址", "目标地址", "进出申请方式", "申请联系人", "联系电话", "应急宝设备状况", "进出方向", "申请日期",
        "接入机房", "纤芯", "制式", "BBU型号", "RRU型号", "BBU-RRU功耗(W)", "网管IP", "网管VLAN", "业务IP", "业务VLAN", "设备序列号", "资产归属", "备注");
    private static final List<String> HEADERS = List.of(
        "台账编号", "原始地址", "目标地址", "进出申请方式", "申请联系人", "联系电话", "应急宝设备状况", "进出方向", "申请日期",
        "PTN端口（网元+槽口）", "接入机房", "纤芯", "制式", "BBU型号", "RRU型号", "BBU-RRU功耗(W)", "网管IP", "网管VLAN", "业务IP", "业务VLAN", "设备序列号", "资产归属", "备注", "设备状况说明", "纬度", "经度");
    private static final Map<String,String> METHOD = Map.of("ONLINE","线上","PAPER","纸质","PHONE","电话","OTHER","其他");
    private static final Map<String,String> CONDITION = Map.of("GOOD","正常","REPAIR","维修中","FAULT","故障","RETIRED","退役");
    private static final Map<String,String> DIRECTION = Map.of("IN","进","OUT","出","TRANSFER","调拨","UNSPECIFIED","未填写");
    private static final String SOURCE_FAULT = "开关电源坏（需要2000W交转直）";
    private final LedgerService ledgers;
    private final LedgerRepository repository;
    private final Validator validator;

    public LedgerExcelService(LedgerService ledgers, LedgerRepository repository, Validator validator) {
        this.ledgers=ledgers; this.repository=repository; this.validator=validator;
    }

    public byte[] export(CurrentUser user,String keyword,String status,String direction) {
        // Reuse the list endpoint's filter validation and bound the in-memory workbook.
        ledgers.list(user,1,1,keyword,status,direction);
        LedgerRepository.Page result=repository.list(1,MAX_EXPORT_ROWS+1,keyword,status,direction);
        if(result.total()>MAX_EXPORT_ROWS) throw bad("导出超过 10000 条，请缩小筛选范围");
        try(XSSFWorkbook workbook=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("应急宝台账");
            Row header=sheet.createRow(0);
            for(int i=0;i<HEADERS.size();i++) header.createCell(i).setCellValue(HEADERS.get(i));
            int rowNumber=1;
            for(LedgerRecord record:result.records()) {
                Row row=sheet.createRow(rowNumber++);
                String[] values={record.ledgerCode(),record.originalAddress(),record.destinationAddress(),
                    METHOD.get(record.applicationMethod()),record.applicantContact(),record.applicantPhone(),
                    CONDITION.get(record.deviceCondition()),DIRECTION.get(record.direction()),record.applicationDate().toString(),
                    record.ptnPort(),record.accessRoom(),record.fiberCore(),record.networkStandard(),record.bbuModel(),record.rruModel(),
                    record.bbuRruPowerWatts(),record.managementIp(),number(record.managementVlan()),record.businessIp(),
                    number(record.businessVlan()),record.deviceSerialNo(),record.assetOwner(),record.remarks(),record.deviceConditionDetail(),record.latitude(),record.longitude()};
                for(int i=0;i<values.length;i++) row.createCell(i).setCellValue(values[i]==null?"":values[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("Excel 导出失败",e); }
    }

    @Transactional
    public ImportCounts importFile(CurrentUser user,MultipartFile file) {
        if(file==null||file.isEmpty()) throw bad("Excel 文件不能为空");
        if(file.getSize()>MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","Excel 文件不能超过 2 MB");
        if(file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) throw bad("仅支持 .xlsx 文件");
        try(XSSFWorkbook workbook=new XSSFWorkbook(file.getInputStream())) {
            if(workbook.getNumberOfSheets()!=1) throw bad("Excel 只能包含一个工作表");
            var sheet=workbook.getSheetAt(0);
            Row header=sheet.getRow(0);
            if(header==null) throw bad("Excel 表头缺失");
            DataFormatter formatter=new DataFormatter();
            Map<String,Integer> columns=columns(header,formatter);
            if(sheet.getLastRowNum()>MAX_IMPORT_ROWS) throw bad("Excel 不能超过 1000 行数据");
            int created=0;
            int updated=0;
            Set<String> seenCodes=new HashSet<>();
            for(int index=1;index<=sheet.getLastRowNum();index++) {
                Row row=sheet.getRow(index);
                int line=index+1;
                if(row==null) continue;
                if(isBlank(row,formatter,columns,line)) continue;
                LedgerRequest request=parse(row,formatter,columns,line);
                Set<ConstraintViolation<LedgerRequest>> violations=validator.validate(request);
                if(!violations.isEmpty()) {
                    String detail=violations.stream().map(v -> label(v.getPropertyPath().toString())+" "+v.getMessage()).sorted().collect(Collectors.joining("；"));
                    throw bad("第 "+line+" 行："+detail);
                }
                validateImportedFields(request,line);
                if(!seenCodes.add(request.ledgerCode().trim())) throw duplicateInFile(line);
                try {
                    LedgerService.ImportOutcome outcome=ledgers.importUpsert(user,request);
                    if(outcome==LedgerService.ImportOutcome.CREATED) created++;
                    else updated++;
                }
                catch(DuplicateKeyException e) { throw duplicate(line); }
                catch(ApiException e) {
                    if("DUPLICATE_LEDGER_CODE".equals(e.code())) throw duplicate(line);
                    throw new ApiException(e.status(),e.code(),"第 "+line+" 行："+e.getMessage());
                }
            }
            if(created+updated==0) throw bad("Excel 没有可导入的数据");
            return new ImportCounts(created+updated,created,updated);
        } catch(ApiException e) { throw e; }
        catch(Exception e) { throw bad("Excel 文件格式无效或无法读取"); }
    }

    private Map<String,Integer> columns(Row header,DataFormatter formatter) {
        Map<String,Integer> result=new HashMap<>();
        Set<String> allowed=new HashSet<>(HEADERS);
        for(int i=0;i<header.getLastCellNum();i++) {
            String name;
            try { name=raw(header,i,formatter); }
            catch(IllegalArgumentException e) { throw bad("Excel 表头第 "+(i+1)+" 列："+e.getMessage()); }
            if(name.isBlank()) throw bad("Excel 表头第 "+(i+1)+" 列为空");
            if(!allowed.contains(name)) throw bad("Excel 表头第 "+(i+1)+" 列未知："+name);
            if(result.putIfAbsent(name,i)!=null) throw bad("Excel 表头重复："+name);
        }
        List<String> missing=new ArrayList<>();
        for(String name:BASE) if(!result.containsKey(name)) missing.add(name);
        if(!missing.isEmpty()) throw bad("Excel 缺少表头："+String.join("、",missing));
        boolean coordinates=result.containsKey("纬度")&&result.containsKey("经度");
        boolean ptn=result.containsKey("PTN端口（网元+槽口）");
        boolean detail=result.containsKey("设备状况说明");
        boolean old22=result.size()==22&&!coordinates&&!ptn&&!detail;
        boolean old24=result.size()==24&&coordinates&&!ptn&&!detail;
        boolean source25=result.size()==25&&coordinates&&ptn&&!detail;
        boolean new26=result.size()==26&&coordinates&&ptn&&detail;
        if(!(old22||old24||source25||new26)) throw bad("Excel 表头组合无效，需使用旧模板、源台账或新版导出文件");
        return result;
    }

    private LedgerRequest parse(Row row,DataFormatter formatter,Map<String,Integer> columns,int line) {
        String conditionRaw=value(row,columns,"应急宝设备状况",formatter,line);
        String condition=SOURCE_FAULT.equals(conditionRaw)?"FAULT":decode(CONDITION,conditionRaw,"应急宝设备状况",line);
        String detail=value(row,columns,"设备状况说明",formatter,line);
        if(detail.isBlank()&&SOURCE_FAULT.equals(conditionRaw)) detail=conditionRaw;
        String directionRaw=value(row,columns,"进出方向",formatter,line);
        String direction=directionRaw.isBlank()?"UNSPECIFIED":decode(DIRECTION,directionRaw,"进出方向",line);
        return new LedgerRequest(
            value(row,columns,"台账编号",formatter,line),value(row,columns,"原始地址",formatter,line),value(row,columns,"目标地址",formatter,line),
            applicationMethod(value(row,columns,"进出申请方式",formatter,line),line),
            value(row,columns,"申请联系人",formatter,line),value(row,columns,"联系电话",formatter,line),condition,detail,direction,
            date(value(row,columns,"申请日期",formatter,line),line),value(row,columns,"PTN端口（网元+槽口）",formatter,line),
            value(row,columns,"接入机房",formatter,line),value(row,columns,"纤芯",formatter,line),value(row,columns,"制式",formatter,line),
            value(row,columns,"BBU型号",formatter,line),value(row,columns,"RRU型号",formatter,line),
            power(value(row,columns,"BBU-RRU功耗(W)",formatter,line),line),value(row,columns,"网管IP",formatter,line),
            integer(value(row,columns,"网管VLAN",formatter,line),"网管VLAN",line),value(row,columns,"业务IP",formatter,line),
            integer(value(row,columns,"业务VLAN",formatter,line),"业务VLAN",line),value(row,columns,"设备序列号",formatter,line),
            value(row,columns,"资产归属",formatter,line),value(row,columns,"备注",formatter,line),
            value(row,columns,"纬度",formatter,line),value(row,columns,"经度",formatter,line),"0");
    }
    private String value(Row row,Map<String,Integer> columns,String name,DataFormatter formatter,int line) {
        Integer index=columns.get(name);
        if(index==null) return "";
        try { return raw(row,index,formatter); }
        catch(IllegalArgumentException e) { throw bad("第 "+line+" 行「"+name+"」："+e.getMessage()); }
    }
    private static String raw(Row row,int col,DataFormatter formatter) {
        Cell cell=row.getCell(col,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if(cell==null) return "";
        if(cell.getCellType()==CellType.FORMULA) throw new IllegalArgumentException("不接受公式单元格");
        return formatter.formatCellValue(cell).trim();
    }
    private boolean isBlank(Row row,DataFormatter formatter,Map<String,Integer> columns,int line) {
        for(String name:columns.keySet()) if(!value(row,columns,name,formatter,line).isBlank()) return false;
        return true;
    }
    private static String decode(Map<String,String> values,String label,String field,int line) {
        if(values.containsKey(label)) return label;
        for(var entry:values.entrySet()) if(entry.getValue().equals(label)) return entry.getKey();
        throw bad("第 "+line+" 行「"+field+"」：无法识别的值「"+label+"」");
    }
    private static String applicationMethod(String value,int line) {
        if("线上联系申请".equals(value)) return "ONLINE";
        return decode(METHOD,value,"进出申请方式",line);
    }
    private static LocalDate date(String value,int line) {
        try { return LocalDate.parse(value); }
        catch(RuntimeException e) { throw bad("第 "+line+" 行「申请日期」：日期格式无效，需为 yyyy-MM-dd"); }
    }
    private static Integer integer(String value,String field,int line) {
        if(value.isBlank()) return null;
        try { return Integer.valueOf(value); }
        catch(NumberFormatException e) { throw bad("第 "+line+" 行「"+field+"」：须为整数"); }
    }
    private static String power(String value,int line) {
        if(value.isBlank()) return null;
        String normalized=value.replaceAll("(?i)\\s*W$","").trim();
        if(!normalized.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw bad("第 "+line+" 行「BBU-RRU功耗(W)」：须为数值或带 W 的瓦数");
        return normalized;
    }
    private static String label(String property) {
        return switch(property) {
            case "ledgerCode" -> "台账编号";
            case "originalAddress" -> "原始地址";
            case "destinationAddress" -> "目标地址";
            case "applicationMethod" -> "进出申请方式";
            case "applicantContact" -> "申请联系人";
            case "applicantPhone" -> "联系电话";
            case "deviceCondition" -> "应急宝设备状况";
            case "deviceConditionDetail" -> "设备状况说明";
            case "direction" -> "进出方向";
            case "applicationDate" -> "申请日期";
            case "ptnPort" -> "PTN端口（网元+槽口）";
            case "accessRoom" -> "接入机房";
            case "fiberCore" -> "纤芯";
            case "networkStandard" -> "制式";
            case "bbuModel" -> "BBU型号";
            case "rruModel" -> "RRU型号";
            case "bbuRruPowerWatts" -> "BBU-RRU功耗(W)";
            case "managementIp" -> "网管IP";
            case "managementVlan" -> "网管VLAN";
            case "businessIp" -> "业务IP";
            case "businessVlan" -> "业务VLAN";
            case "deviceSerialNo" -> "设备序列号";
            case "assetOwner" -> "资产归属";
            case "remarks" -> "备注";
            case "latitude" -> "纬度";
            case "longitude" -> "经度";
            default -> property;
        };
    }
    private void validateImportedFields(LedgerRequest request,int line) {
        check(line,"网管IP",() -> ledgers.ip(request.managementIp()));
        check(line,"业务IP",() -> ledgers.ip(request.businessIp()));
        check(line,"纬度/经度",() -> ledgers.coordinates(request.latitude(),request.longitude()));
        check(line,"BBU-RRU功耗(W)",() -> { ledgers.power(request.bbuRruPowerWatts()); });
    }
    private static void check(int line,String field,Runnable validation) {
        try { validation.run(); }
        catch(ApiException e) { throw new ApiException(e.status(),e.code(),"第 "+line+" 行「"+field+"」："+e.getMessage()); }
    }
    private static ApiException duplicate(int line) { return new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","第 "+line+" 行「台账编号」：编号属于已删除的台账，无法覆盖"); }
    private static ApiException duplicateInFile(int line) { return new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","第 "+line+" 行「台账编号」：文件内编号重复"); }
    public record ImportCounts(int importedCount,int createdCount,int updatedCount) {}
    private static String number(Integer value) { return value==null?"":value.toString(); }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
}
