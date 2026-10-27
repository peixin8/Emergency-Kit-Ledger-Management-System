package com.emergency.ledger;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public LedgerRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    private static final String FIELDS = "id,organization_id,ledger_code,original_address,destination_address,application_method,applicant_contact,applicant_phone,device_condition,direction,application_date,access_room,fiber_core,network_standard,bbu_model,rru_model,bbu_rru_power_watts,management_ip,management_vlan,business_ip,business_vlan,device_serial_no,asset_owner,remarks,created_at,updated_at,version";
    private static final String VALUES = "ledger_code=?,original_address=?,destination_address=?,application_method=?,applicant_contact=?,applicant_phone=?,device_condition=?,direction=?,application_date=?,access_room=?,fiber_core=?,network_standard=?,bbu_model=?,rru_model=?,bbu_rru_power_watts=?,management_ip=?,management_vlan=?,business_ip=?,business_vlan=?,device_serial_no=?,asset_owner=?,remarks=?";
    private static final RowMapper<LedgerRecord> MAPPER = (rs,row) -> new LedgerRecord(
        Long.toString(rs.getLong("id")),Long.toString(rs.getLong("organization_id")),rs.getString("ledger_code"),rs.getString("original_address"),rs.getString("destination_address"),
        rs.getString("application_method"),rs.getString("applicant_contact"),rs.getString("applicant_phone"),rs.getString("device_condition"),rs.getString("direction"),
        rs.getObject("application_date",LocalDate.class),rs.getString("access_room"),rs.getString("fiber_core"),rs.getString("network_standard"),rs.getString("bbu_model"),rs.getString("rru_model"),
        decimal(rs,"bbu_rru_power_watts"),rs.getString("management_ip"),intOrNull(rs,"management_vlan"),rs.getString("business_ip"),intOrNull(rs,"business_vlan"),
        rs.getString("device_serial_no"),rs.getString("asset_owner"),rs.getString("remarks"),rs.getObject("created_at",LocalDateTime.class).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),rs.getObject("updated_at",LocalDateTime.class).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime(),Long.toString(rs.getLong("version")));
    private static String decimal(ResultSet rs,String key) throws SQLException { BigDecimal v=rs.getBigDecimal(key); return v==null?null:v.setScale(2).toPlainString(); }
    private static Integer intOrNull(ResultSet rs,String key) throws SQLException { int v=rs.getInt(key); return rs.wasNull()?null:v; }
    private Object[] values(LedgerRequest r, BigDecimal power) {
        return new Object[]{r.ledgerCode().trim(),r.originalAddress().trim(),r.destinationAddress().trim(),r.applicationMethod(),r.applicantContact().trim(),blank(r.applicantPhone()),r.deviceCondition(),r.direction(),r.applicationDate(),blank(r.accessRoom()),blank(r.fiberCore()),blank(r.networkStandard()),blank(r.bbuModel()),blank(r.rruModel()),power,blank(r.managementIp()),r.managementVlan(),blank(r.businessIp()),r.businessVlan(),blank(r.deviceSerialNo()),blank(r.assetOwner()),blank(r.remarks())};
    }
    private static String blank(String s) { return s==null||s.isBlank()?null:s.trim(); }
    public long insert(long organizationId, LedgerRequest r, BigDecimal power) {
        Object[] fields=values(r,power);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String sql="INSERT INTO emergency_device_ledger("+FIELDS.substring(3,FIELDS.indexOf(",created_at"))+") VALUES ("+"?,"+"?,".repeat(fields.length-1)+"?)";
        jdbc.update(connection -> { PreparedStatement ps=connection.prepareStatement(sql,new String[]{"id"}); ps.setLong(1,organizationId); for(int i=0;i<fields.length;i++) ps.setObject(i+2,fields[i]); return ps; },key);
        return key.getKey().longValue();
    }
    public LedgerRecord find(long id) {
        List<LedgerRecord> rows=jdbc.query("SELECT "+FIELDS+" FROM emergency_device_ledger WHERE id=? AND deleted=FALSE",MAPPER,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public boolean existsIncludingDeleted(long id) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE id=?",Long.class,id);
        return count!=null&&count>0;
    }
    public HistoryPage history(long id,int page,int pageSize) {
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit WHERE ledger_id=?",Long.class,id);
        List<HistoryEntry> records=jdbc.query("SELECT id,ledger_id,action,old_value,new_value,created_at FROM ledger_audit WHERE ledger_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs,row) -> new HistoryEntry(Long.toString(rs.getLong("id")),Long.toString(rs.getLong("ledger_id")),rs.getString("action"),
                "共享用户",parseSnapshot(rs.getString("old_value")),parseSnapshot(rs.getString("new_value")),
                rs.getObject("created_at",LocalDateTime.class).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime()),
            id,pageSize,((long)page-1)*pageSize);
        return new HistoryPage(records,total,page,pageSize);
    }
    private JsonNode parseSnapshot(String value) {
        if(value==null) return null;
        try { return json.readTree(value); } catch(Exception e) { throw new IllegalStateException("审计记录无法读取",e); }
    }
    public int update(long organizationId,long id,long version,LedgerRequest request,BigDecimal power) {
        Object[] fields=values(request,power);
        Object[] params=new Object[fields.length+3]; System.arraycopy(fields,0,params,0,fields.length);
        params[fields.length]=organizationId; params[fields.length+1]=id; params[fields.length+2]=version;
        return jdbc.update("UPDATE emergency_device_ledger SET "+VALUES+",version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND version=? AND deleted=FALSE",params);
    }
    public int delete(long organizationId,long id,long version) {
        return jdbc.update("UPDATE emergency_device_ledger SET deleted=TRUE,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND version=? AND deleted=FALSE",organizationId,id,version);
    }
    public Page list(int page,int pageSize,String keyword,String status,String direction) {
        StringBuilder where=new StringBuilder(" WHERE deleted=FALSE");
        List<Object> args=new ArrayList<>();
        if(keyword!=null&&!keyword.isBlank()) { where.append(" AND (ledger_code LIKE ? OR original_address LIKE ? OR destination_address LIKE ? OR applicant_contact LIKE ?)"); String k="%"+keyword.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; for(int i=0;i<4;i++) args.add(k); }
        if(status!=null&&!status.isBlank()) { where.append(" AND device_condition=?"); args.add(status); }
        if(direction!=null&&!direction.isBlank()) { where.append(" AND direction=?"); args.add(direction); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger"+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args); paged.add(pageSize); paged.add(((long)page-1)*pageSize);
        List<LedgerRecord> records=jdbc.query("SELECT "+FIELDS+" FROM emergency_device_ledger"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",MAPPER,paged.toArray());
        return new Page(records,total,page,pageSize);
    }
    public void audit(long org,long ledger,long actor,String action,String oldValue,String newValue) {
        jdbc.update("INSERT INTO ledger_audit(organization_id,ledger_id,actor_user_id,action,old_value,new_value,trace_id) VALUES (?,?,?,?,?,?,?)",org,ledger,actor,action,oldValue,newValue,Trace.id());
    }
    public void lockSharedWorkspace(long organizationId) {
        jdbc.queryForObject("SELECT id FROM organization WHERE id=? FOR UPDATE",Long.class,organizationId);
    }
    public boolean codeExists(String code,long exceptId) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=? AND id<>?",Long.class,code,exceptId);
        return count!=null&&count>0;
    }
    public record Page(List<LedgerRecord> records,long total,int page,int pageSize) {}
    public record HistoryEntry(String id,String ledgerId,String action,String actorName,JsonNode before,JsonNode after,OffsetDateTime occurredAt) {}
    public record HistoryPage(List<HistoryEntry> records,long total,int page,int pageSize) {}
}
