package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 无缓存预览确认：文件及全局版本快照均由服务端重新取得。 */
@Service
public class LedgerImportService {
    private final LedgerExcelService excel;
    private final LedgerRepository repository;
    private final ObjectMapper json;
    public LedgerImportService(LedgerExcelService excel,LedgerRepository repository,ObjectMapper json) {
        this.excel=excel; this.repository=repository; this.json=json;
    }
    @Transactional
    public Preview preview(CurrentUser user,MultipartFile file) {
        var parsed=excel.parseFile(file);
        repository.lockSharedWorkspace(user.organizationId());
        String fileHash=fileHash(file); String fingerprint=fingerprint(fileHash);
        List<PreviewRow> rows=new ArrayList<>(); Set<Long> targetIds=new HashSet<>(); int created=0,unchanged=0;
        for(var row:parsed.rows()) {
            var request=row.request(); var matches=repository.codeMatches(request.ledgerCode().trim());
            if(matches.size()>1) throw new ApiException(HttpStatus.CONFLICT,"AMBIGUOUS_LEDGER_CODE","第 "+row.line()+" 行：台账编号对应多条历史记录，无法确定覆盖目标");
            LedgerRecord old=null;
            if(!matches.isEmpty()) {
                var match=matches.get(0);
                if(match.deleted()) throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","第 "+row.line()+" 行「台账编号」：编号属于已删除的台账，无法覆盖");
                if(!targetIds.add(match.id())) throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_LEDGER_CODE","第 "+row.line()+" 行「台账编号」：文件内编号重复");
                old=repository.find(match.id());
            } else created++;
            List<String> changed=new ArrayList<>(),cleared=new ArrayList<>();
            JsonNode before=old==null?json.createObjectNode():json.valueToTree(old);
            JsonNode after=json.valueToTree(request);
            for(var field:LedgerRequest.class.getRecordComponents()) {
                String key=field.getName(); if("version".equals(key)) continue;
                String a=normalized(before.path(key),key),b=normalized(after.path(key),key);
                if(!Objects.equals(a,b)) { changed.add(key); if(a!=null&&b==null) cleared.add(key); }
            }
            String action=old==null?"CREATE":changed.isEmpty()?"UNCHANGED":"UPDATE";
            if("UNCHANGED".equals(action)) unchanged++;
            rows.add(new PreviewRow(row.line(),request.ledgerCode().trim(),action,old==null?null:old.id(),old==null?null:old.version(),changed,cleared));
        }
        return new Preview(fileHash,fingerprint,rows.size(),created,rows.size()-created-unchanged,unchanged,parsed.missingOptionalColumns(),rows);
    }
    @Transactional
    public LedgerExcelService.ImportCounts confirm(CurrentUser user,MultipartFile file,String expected) {
        if(expected==null||!expected.matches("[0-9a-fA-F]{64}")) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","预览指纹格式无效，请重新预览");
        repository.lockSharedWorkspace(user.organizationId());
        var parsed=excel.parseFile(file);
        if(!MessageDigest.isEqual(fingerprint(fileHash(file)).getBytes(StandardCharsets.US_ASCII),expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
            throw new ApiException(HttpStatus.CONFLICT,"IMPORT_PREVIEW_STALE","文件或共享台账已变化，请重新预览");
        return excel.applyRows(user,parsed);
    }
    private String fingerprint(String fileHash) {
        return hash(("ledger-import-preview-v1\n"+fileHash+"\n"+String.join("\n",repository.importState())).getBytes(StandardCharsets.UTF_8));
    }
    private static String fileHash(MultipartFile file) {
        try { return hash(file.getBytes()); } catch(java.io.IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Excel 文件无法读取"); }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String normalized(JsonNode value,String field) {
        if(value.isMissingNode()||value.isNull()) return null;
        String text=value.asText().trim(); if(text.isEmpty()) return null;
        if(Set.of("bbuRruPowerWatts","latitude","longitude").contains(field)) return new BigDecimal(text).stripTrailingZeros().toPlainString();
        return text;
    }
    public record Preview(String fileHash,String previewFingerprint,int importedCount,int createdCount,int updatedCount,int unchangedCount,List<String> missingOptionalColumns,List<PreviewRow> rows) {}
    public record PreviewRow(int line,String ledgerCode,String action,String targetId,String targetVersion,List<String> changedFields,List<String> clearedFields) {}
}
