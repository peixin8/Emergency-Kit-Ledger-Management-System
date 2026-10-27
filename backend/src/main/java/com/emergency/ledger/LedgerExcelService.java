package com.emergency.ledger;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
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
    private static final String[] HEADERS = {
        "台账编号", "原始地址", "目标地址", "进出申请方式", "申请联系人", "联系电话", "应急宝设备状况", "进出方向", "申请日期",
        "接入机房", "纤芯", "制式", "BBU型号", "RRU型号", "BBU-RRU功耗(W)", "网管IP", "网管VLAN", "业务IP", "业务VLAN", "设备序列号", "资产归属", "备注", "纬度", "经度"
    };
    private static final Map<String,String> METHOD = Map.of("ONLINE","线上","PAPER","纸质","PHONE","电话","OTHER","其他");
    private static final Map<String,String> CONDITION = Map.of("GOOD","正常","REPAIR","维修中","FAULT","故障","RETIRED","退役");
    private static final Map<String,String> DIRECTION = Map.of("IN","进","OUT","出","TRANSFER","调拨");
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
            for(int i=0;i<HEADERS.length;i++) header.createCell(i).setCellValue(HEADERS[i]);
            int rowNumber=1;
            for(LedgerRecord record:result.records()) {
                Row row=sheet.createRow(rowNumber++);
                String[] values={record.ledgerCode(),record.originalAddress(),record.destinationAddress(),
                    METHOD.get(record.applicationMethod()),record.applicantContact(),record.applicantPhone(),
                    CONDITION.get(record.deviceCondition()),DIRECTION.get(record.direction()),record.applicationDate().toString(),
                    record.accessRoom(),record.fiberCore(),record.networkStandard(),record.bbuModel(),record.rruModel(),
                    record.bbuRruPowerWatts(),record.managementIp(),number(record.managementVlan()),record.businessIp(),
                    number(record.businessVlan()),record.deviceSerialNo(),record.assetOwner(),record.remarks(),record.latitude(),record.longitude()};
                for(int i=0;i<values.length;i++) row.createCell(i).setCellValue(values[i]==null?"":values[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("Excel 导出失败",e); }
    }

    @Transactional
    public int importFile(CurrentUser user,MultipartFile file) {
        if(file==null||file.isEmpty()) throw bad("Excel 文件不能为空");
        if(file.getSize()>MAX_BYTES) throw bad("Excel 文件不能超过 2 MB");
        if(file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) throw bad("仅支持 .xlsx 文件");
        try(XSSFWorkbook workbook=new XSSFWorkbook(file.getInputStream())) {
            if(workbook.getNumberOfSheets()!=1) throw bad("Excel 只能包含一个工作表");
            var sheet=workbook.getSheetAt(0);
            Row header=sheet.getRow(0);
            if(header==null) throw bad("Excel 表头缺失");
            DataFormatter formatter=new DataFormatter();
            boolean oldTemplate=header.getLastCellNum()==HEADERS.length-2;
            int columns=oldTemplate?HEADERS.length-2:HEADERS.length;
            if(header.getLastCellNum()!=columns) throw bad("Excel 表头与导出模板不一致");
            for(int i=0;i<columns;i++) if(!HEADERS[i].equals(value(header,i,formatter))) throw bad("Excel 表头与导出模板不一致");
            if(sheet.getLastRowNum()>MAX_IMPORT_ROWS) throw bad("Excel 不能超过 1000 行数据");
            int imported=0;
            for(int index=1;index<=sheet.getLastRowNum();index++) {
                Row row=sheet.getRow(index);
                int line=index+1;
                if(row==null) continue;
                try { if(isBlank(row,formatter,columns)) continue; }
                catch(IllegalArgumentException e) { throw bad("第 "+line+" 行："+e.getMessage()); }
                LedgerRequest request=parse(row,formatter,line,columns);
                Set<ConstraintViolation<LedgerRequest>> violations=validator.validate(request);
                if(!violations.isEmpty()) {
                    String detail=violations.stream().map(v -> v.getPropertyPath()+" "+v.getMessage()).sorted().collect(Collectors.joining("；"));
                    throw bad("第 "+line+" 行："+detail);
                }
                try { ledgers.create(user,request); }
                catch(DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","第 "+line+" 行：台账编号已存在"); }
                catch(ApiException e) { throw new ApiException(e.status(),e.code(),"第 "+line+" 行："+e.getMessage()); }
                imported++;
            }
            if(imported==0) throw bad("Excel 没有可导入的数据");
            return imported;
        } catch(ApiException e) { throw e; }
        catch(Exception e) { throw bad("Excel 文件格式无效或无法读取"); }
    }

    private LedgerRequest parse(Row row,DataFormatter formatter,int line,int columns) {
        try {
            String[] v=new String[HEADERS.length];
            for(int i=0;i<columns;i++) v[i]=value(row,i,formatter);
            return new LedgerRequest(v[0],v[1],v[2],decode(METHOD,v[3]),v[4],v[5],decode(CONDITION,v[6]),
                decode(DIRECTION,v[7]),LocalDate.parse(v[8]),v[9],v[10],v[11],v[12],v[13],v[14],v[15],
                integer(v[16]),v[17],integer(v[18]),v[19],v[20],v[21],v[22],v[23],"0");
        } catch(RuntimeException e) { throw bad("第 "+line+" 行：日期、VLAN 或字段格式无效"); }
    }
    private static String value(Row row,int col,DataFormatter formatter) {
        Cell cell=row.getCell(col,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if(cell==null) return "";
        if(cell.getCellType()==CellType.FORMULA) throw new IllegalArgumentException("不接受公式单元格");
        return formatter.formatCellValue(cell).trim();
    }
    private static boolean isBlank(Row row,DataFormatter formatter,int columns) {
        for(int i=0;i<columns;i++) if(!value(row,i,formatter).isBlank()) return false;
        return true;
    }
    private static String decode(Map<String,String> values,String label) {
        for(var entry:values.entrySet()) if(entry.getValue().equals(label)) return entry.getKey();
        return label;
    }
    private static Integer integer(String value) { return value.isBlank()?null:Integer.valueOf(value); }
    private static String number(Integer value) { return value==null?"":value.toString(); }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
}
