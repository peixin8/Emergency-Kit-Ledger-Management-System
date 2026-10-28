package com.emergency.ledger;

import java.math.BigDecimal;
import java.net.InetAddress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final LedgerRepository repository;
    private final ObjectMapper json;
    public LedgerService(LedgerRepository repository,ObjectMapper json) { this.repository=repository; this.json=json; }
    public LedgerRepository.Page list(CurrentUser user,int page,int pageSize,String keyword,String status,String direction) {
        if(page<1||pageSize<1||pageSize>100||((long)page-1)*pageSize>Integer.MAX_VALUE) throw bad("分页参数无效");
        if(keyword!=null&&keyword.length()>100) throw bad("搜索词过长");
        if(status!=null&&!status.isBlank()&&!status.matches("GOOD|REPAIR|FAULT|RETIRED")) throw bad("设备状况无效");
        if(direction!=null&&!direction.isBlank()&&!direction.matches("IN|OUT|TRANSFER|UNSPECIFIED")) throw bad("进出方向无效");
        return repository.list(page,pageSize,keyword,status,direction);
    }
    public LedgerRecord get(CurrentUser user,long id) {
        LedgerRecord record=repository.find(id);
        if(record==null) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","台账不存在");
        return record;
    }
    public LedgerRepository.MapPoints mapPoints(CurrentUser user) { return repository.mapPoints(); }
    public LedgerRepository.HistoryPage history(CurrentUser user,long id,int page,int pageSize) {
        validatePage(page,pageSize);
        if(!repository.existsIncludingDeleted(id))
            throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","台账不存在");
        return repository.history(id,page,pageSize);
    }
    private void validatePage(int page,int pageSize) {
        if(page<1||pageSize<1||pageSize>100||((long)page-1)*pageSize>Integer.MAX_VALUE) throw bad("分页参数无效");
    }
    @Transactional
    public LedgerRecord create(CurrentUser user,LedgerRequest request) {
        canEdit(user);
        BigDecimal power=validate(request,false);
        repository.lockSharedWorkspace(user.organizationId());
        if(repository.codeExists(request.ledgerCode().trim(),-1)) throw duplicateCode();
        long id=repository.insert(user.organizationId(),request,power);
        LedgerRecord result=get(user,id);
        repository.audit(user.organizationId(),id,user.id(),"CREATE",null,serialize(result));
        return result;
    }
    @Transactional
    public LedgerRecord update(CurrentUser user,long id,LedgerRequest request) {
        canEdit(user);
        BigDecimal power=validate(request,true);
        repository.lockSharedWorkspace(user.organizationId());
        LedgerRecord old=get(user,id);
        if(!old.ledgerCode().equals(request.ledgerCode().trim())&&repository.codeExists(request.ledgerCode().trim(),id)) throw duplicateCode();
        return updateLocked(user,old,request,power,parseVersion(request.version()));
    }
    @Transactional
    public ImportOutcome importUpsert(CurrentUser user,LedgerRequest request) {
        canEdit(user);
        BigDecimal power=validate(request,false);
        repository.lockSharedWorkspace(user.organizationId());
        var matches=repository.codeMatches(request.ledgerCode().trim());
        if(matches.size()>1) throw new ApiException(HttpStatus.CONFLICT,"AMBIGUOUS_LEDGER_CODE","台账编号对应多条历史记录，无法确定覆盖目标");
        if(matches.isEmpty()) {
            long id=repository.insert(user.organizationId(),request,power);
            LedgerRecord created=get(user,id);
            repository.audit(user.organizationId(),id,user.id(),"CREATE",null,serialize(created));
            return ImportOutcome.CREATED;
        }
        LedgerRepository.CodeMatch match=matches.get(0);
        if(match.deleted()) throw duplicateCode();
        LedgerRecord old=get(user,match.id());
        updateLocked(user,old,request,power,Long.parseLong(old.version()));
        return ImportOutcome.UPDATED;
    }
    private LedgerRecord updateLocked(CurrentUser user,LedgerRecord old,LedgerRequest request,BigDecimal power,long version) {
        long id=Long.parseLong(old.id());
        long recordOrg=Long.parseLong(old.organizationId());
        int rows=repository.update(recordOrg,id,version,request,power);
        if(rows!=1) throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","台账已被修改，请刷新后重试");
        LedgerRecord result=get(user,id);
        repository.audit(recordOrg,id,user.id(),"UPDATE",serialize(old),serialize(result));
        return result;
    }
    public enum ImportOutcome { CREATED, UPDATED }
    @Transactional
    public void delete(CurrentUser user,long id) {
        if(!"ADMIN".equals(user.role())) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","仅管理员可删除台账");
        LedgerRecord old=get(user,id);
        long recordOrg=Long.parseLong(old.organizationId());
        int rows=repository.delete(recordOrg,id,Long.parseLong(old.version()));
        if(rows!=1) throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","台账已被修改，请刷新后重试");
        repository.audit(recordOrg,id,user.id(),"DELETE",serialize(old),null);
    }
    private void canEdit(CurrentUser user) {
        if(!"ADMIN".equals(user.role())&&!"EDITOR".equals(user.role())) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权修改台账");
    }
    private BigDecimal validate(LedgerRequest r,boolean update) {
        if(update) parseVersion(r.version());
        ip(r.managementIp()); ip(r.businessIp());
        coordinates(r.latitude(),r.longitude());
        return power(r.bbuRruPowerWatts());
    }
    void coordinates(String latitude,String longitude) {
        boolean hasLatitude=latitude!=null&&!latitude.isBlank();
        boolean hasLongitude=longitude!=null&&!longitude.isBlank();
        if(hasLatitude!=hasLongitude) throw bad("纬度和经度必须同时填写");
        if(hasLatitude) {
            coordinate(latitude,"纬度",90);
            coordinate(longitude,"经度",180);
        }
    }
    BigDecimal power(String input) {
        if(input==null||input.isBlank()) return null;
        try {
            BigDecimal value=new BigDecimal(input);
            if(value.signum()<0||value.scale()>2||value.precision()-value.scale()>10) throw bad("功耗须为非负数，最多两位小数");
            return value.setScale(2);
        } catch(NumberFormatException e) { throw bad("功耗格式无效"); }
    }
    private void coordinate(String input,String label,int bound) {
        String trimmed=input.trim();
        if(!trimmed.matches("[+-]?(?:[0-9]+)(?:\\.[0-9]{1,7})?")) throw bad(label+"格式无效，最多七位小数");
        try {
            if(new BigDecimal(trimmed).abs().compareTo(BigDecimal.valueOf(bound))>0) throw bad(label+"超出范围");
        } catch(NumberFormatException e) { throw bad(label+"格式无效"); }
    }
    void ip(String input) {
        if(input==null||input.isBlank()) return;
        String ip=input.trim();
        if(ip.contains(".")) {
            String[] p=ip.split("\\.",-1);
            if(p.length==4) { boolean ipv4=true; for(String s:p) { if(!s.matches("[0-9]{1,3}")||Integer.parseInt(s)>255) ipv4=false; } if(ipv4) return; }
        }
        if(ip.contains(":")&&ip.matches("[0-9a-fA-F:.]+")) {
            try { if(InetAddress.getByName(ip).getAddress().length==16) return; } catch(Exception ignored) { }
        }
        throw bad("IP 地址格式无效");
    }
    private long parseVersion(String version) {
        try { long v=Long.parseLong(version); if(v>=0) return v; } catch(Exception ignored) { }
        throw bad("version 必须为非负整数的十进制字符串");
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException("审计序列化失败",e); }
    }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
    private ApiException duplicateCode() { return new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","台账编号已存在"); }
}
