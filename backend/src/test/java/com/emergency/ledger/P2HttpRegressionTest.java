package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Exercises JSON binding and Servlet multipart parsing over real sockets. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.datasource.hikari.minimum-idle=0", "spring.datasource.hikari.maximum-pool-size=3"})
class P2HttpRegressionTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource pool;
    private static final String ROOT="/api/v1/ledgers";

    private ObjectNode body(String code) {
        return json.createObjectNode().put("ledgerCode",code).put("originalAddress","原机房")
            .put("destinationAddress","目标机房").put("applicationMethod","ONLINE")
            .put("applicantContact","共享联系人").put("deviceCondition","GOOD").put("direction","IN")
            .put("applicationDate","2026-10-27").put("bbuRruPowerWatts","12.50").put("version","0");
    }
    private String code() { return "P2-"+System.nanoTime(); }
    private ResponseEntity<JsonNode> send(HttpMethod method,String path,String body) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private JsonNode create(ObjectNode body) {
        var response=send(HttpMethod.POST,ROOT,body.toString());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody().path("data");
    }
    private List<Integer> counts() {
        return List.of(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger",Integer.class),
            jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit",Integer.class));
    }
    private void error(ResponseEntity<JsonNode> response,int status,String code) {
        assertThat(response.getStatusCode().value()).as(response.toString()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("data").isNull()).isTrue();
        assertThat(response.getBody().path("message").asText()).isNotBlank();
        assertThat(response.getBody().path("traceId").asText()).isNotBlank();
    }

    @ParameterizedTest(name="{0} rejects token {1} for POST and PUT without writes")
    @CsvSource({"managementVlan,1.9","managementVlan,4094.9","managementVlan,1.0","managementVlan,1e0",
        "businessVlan,1.9","businessVlan,4094.9","businessVlan,1.0","businessVlan,1E0",
        "managementVlan,0","managementVlan,4095","businessVlan,0","businessVlan,4095"})
    void invalidVlanDoesNotWrite(String field,String token) {
        var request=body(code()); var existing=create(request); String id=existing.path("id").asText();
        var before=counts();
        String invalidCreate=body(code()).toString().replace("\"version\":\"0\"","\"version\":\"0\",\""+field+"\":"+token);
        String invalidUpdate=request.toString().replace("\"version\":\"0\"","\"version\":\"0\",\""+field+"\":"+token);
        assertAll("POST and PUT reject the original token", () -> {
            error(send(HttpMethod.POST,ROOT,invalidCreate),400,"VALIDATION_ERROR");
            assertThat(counts()).isEqualTo(before);
        }, () -> {
            error(send(HttpMethod.PUT,ROOT+"/"+id,invalidUpdate),400,"VALIDATION_ERROR");
            assertThat(http.getForObject(ROOT+"/"+id,JsonNode.class).path("data")).isEqualTo(existing);
            assertThat(counts()).isEqualTo(before);
        });
    }

    @Test void integerBoundsNullAndOmittedVlansRemainSupported() {
        var request=body(code()).put("managementVlan",1).put("businessVlan",4094);
        var created=create(request); String id=created.path("id").asText();
        assertThat(created.path("managementVlan").asInt()).isEqualTo(1);
        assertThat(created.path("businessVlan").asInt()).isEqualTo(4094);
        request.put("managementVlan",4094).put("businessVlan",1);
        var updated=send(HttpMethod.PUT,ROOT+"/"+id,request.toString());
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().path("data").path("managementVlan").asInt()).isEqualTo(4094);
        assertThat(updated.getBody().path("data").path("businessVlan").asInt()).isEqualTo(1);
        request.put("version","1").putNull("managementVlan").remove("businessVlan");
        updated=send(HttpMethod.PUT,ROOT+"/"+id,request.toString());
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().path("data").path("managementVlan").isNull()).isTrue();
        assertThat(updated.getBody().path("data").path("businessVlan").isNull()).isTrue();
        var nullable=body(code()).putNull("managementVlan");
        var result=create(nullable);
        assertThat(result.path("managementVlan").isNull()).isTrue();
        assertThat(result.path("businessVlan").isNull()).isTrue();
    }

    @Test void createUpdateAndHistoryRepresentActualInstants() {
        Instant start=Instant.now().minusSeconds(2);
        var request=body(code()); var created=create(request); String id=created.path("id").asText();
        Instant end=Instant.now().plusSeconds(2);
        inWindow(created,"createdAt",start,end); inWindow(created,"updatedAt",start,end);
        assertThat(created.path("applicationDate").asText()).isEqualTo("2026-10-27");
        Instant updateStart=Instant.now().minusSeconds(2);
        request.put("destinationAddress","更新机房");
        var updated=send(HttpMethod.PUT,ROOT+"/"+id,request.toString()).getBody().path("data");
        Instant updateEnd=Instant.now().plusSeconds(2);
        inWindow(updated,"updatedAt",updateStart,updateEnd);
        assertThat(updated.path("createdAt")).isEqualTo(created.path("createdAt"));
        var records=http.getForObject(ROOT+"/"+id+"/history",JsonNode.class).path("data").path("records");
        assertThat(records.size()).isEqualTo(2);
        inWindow(records.get(0),"occurredAt",updateStart,updateEnd);
        inWindow(records.get(1),"occurredAt",start,end);
    }
    private void inWindow(JsonNode value,String key,Instant start,Instant end) {
        assertThat(OffsetDateTime.parse(value.path(key).asText()).toInstant()).isBetween(start,end);
    }

    @Test void subsequentPhysicalConnectionsAlsoUseShanghaiSession() throws Exception {
        try(Connection first=pool.getConnection(); Connection second=pool.getConnection()) {
            assertSession(first); assertSession(second);
        }
        pool.getHikariPoolMXBean().softEvictConnections();
        try(Connection replacement=pool.getConnection()) { assertSession(replacement); }
    }
    private void assertSession(Connection connection) throws Exception {
        try(var statement=connection.createStatement(); var result=statement.executeQuery("SELECT CURRENT_TIMESTAMP")) {
            result.next();
            assertThat(result.getObject(1,OffsetDateTime.class).getOffset().getId()).isEqualTo("+08:00");
        }
    }

    @Test void legacyTimestampAndAuditSnapshotsRetainTheirExistingInterpretation() {
        var created=create(body(code())); String id=created.path("id").asText();
        String snapshot="{\"createdAt\":\"2020-01-02T03:04:05+08:00\",\"marker\":\"原样快照\"}";
        jdbc.update("UPDATE emergency_device_ledger SET created_at=TIMESTAMP '2020-01-02 03:04:05',updated_at=TIMESTAMP '2020-02-03 04:05:06' WHERE id=?",id);
        jdbc.update("UPDATE ledger_audit SET new_value=?,created_at=TIMESTAMP '2020-03-04 05:06:07' WHERE ledger_id=?",snapshot,id);
        var record=http.getForObject(ROOT+"/"+id,JsonNode.class).path("data");
        assertThat(record.path("createdAt").asText()).isEqualTo("2020-01-02T03:04:05+08:00");
        assertThat(record.path("updatedAt").asText()).isEqualTo("2020-02-03T04:05:06+08:00");
        var history=http.getForObject(ROOT+"/"+id+"/history",JsonNode.class).path("data").path("records").get(0);
        assertThat(history.path("occurredAt").asText()).isEqualTo("2020-03-04T05:06:07+08:00");
        assertThat(history.path("after").path("createdAt").asText()).isEqualTo("2020-01-02T03:04:05+08:00");
        assertThat(jdbc.queryForObject("SELECT new_value FROM ledger_audit WHERE ledger_id=?",String.class,id)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT created_at FROM emergency_device_ledger WHERE id=?",LocalDateTime.class,id))
            .isEqualTo(LocalDateTime.of(2020,1,2,3,4,5));
    }

    private ResponseEntity<JsonNode> upload(String filename,byte[] file,byte[] extra) {
        return upload(filename,file,extra,JsonNode.class);
    }
    private <T> ResponseEntity<T> upload(String filename,byte[] file,byte[] extra,Class<T> responseType) {
        var parts=new LinkedMultiValueMap<String,Object>();
        if(file!=null) parts.add("file",new ByteArrayResource(file) { @Override public String getFilename() { return filename; } });
        if(extra!=null) parts.add("extra",new ByteArrayResource(extra) { @Override public String getFilename() { return "extra.bin"; } });
        if(parts.isEmpty()) parts.add("other","没有file");
        var headers=new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(ROOT+"/import",new HttpEntity<>(parts,headers),responseType);
    }
    @ParameterizedTest(name="413 response for file={0} and extra={1} has complete byte length")
    @CsvSource({"2097153,0","1600000,1600000"})
    void oversizedUploadReturnsCompleteLengthDelimitedJson(int fileBytes,int extraBytes) throws Exception {
        var before=counts();
        var response=upload("x.xlsx",new byte[fileBytes],extraBytes==0?null:new byte[extraBytes],byte[].class);
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        byte[] payload=response.getBody();
        assertThat(payload).isNotEmpty();
        assertThat(response.getHeaders().getContentLength()).isEqualTo(payload.length);
        assertThat(response.getHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING)).isFalse();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode envelope=json.readTree(payload);
        assertThat(envelope.path("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
        assertThat(envelope.path("message").asText()).isNotBlank();
        assertThat(envelope.path("data").isNull()).isTrue();
        assertThat(envelope.path("traceId").asText()).isNotBlank().isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        assertThat(counts()).isEqualTo(before);
    }
    @Test void missingFileIsValidationErrorWithoutWrites() {
        var before=counts(); error(upload("x.xlsx",null,null),400,"VALIDATION_ERROR"); assertThat(counts()).isEqualTo(before);
    }
    @Test void emptyWrongExtensionAndCorruptFilesAreValidationErrorsWithoutWrites() {
        for(var file:List.of(new byte[0],"坏文件".getBytes(StandardCharsets.UTF_8))) {
            var before=counts(); error(upload("x.xlsx",file,null),400,"VALIDATION_ERROR"); assertThat(counts()).isEqualTo(before);
        }
        var before=counts(); error(upload("x.txt",new byte[]{1},null),400,"VALIDATION_ERROR"); assertThat(counts()).isEqualTo(before);
    }
    @Test void fileOverTwoMiBIs413WithoutWrites() {
        var before=counts(); error(upload("x.xlsx",new byte[2097153],null),413,"UPLOAD_TOO_LARGE"); assertThat(counts()).isEqualTo(before);
    }
    @Test void requestOverThreeMiBIs413WithoutWrites() {
        var before=counts(); error(upload("x.xlsx",new byte[1600000],new byte[1600000]),413,"UPLOAD_TOO_LARGE"); assertThat(counts()).isEqualTo(before);
    }
    @Test void validWorkbookAtExactlyTwoMiBSucceeds() throws Exception {
        byte[] workbook=workbook(code(),false,false);
        byte[] padded=padZip(workbook,2097152);
        assertThat(padded.length).isEqualTo(2097152);
        try(var parsed=new XSSFWorkbook(new ByteArrayInputStream(padded))) { assertThat(parsed.getNumberOfSheets()).isEqualTo(1); }
        var before=counts(); var response=upload("台账.xlsx",padded,null);
        assertThat(response.getStatusCode().value()).as(response.toString()).isEqualTo(200);
        assertThat(response.getBody().path("data").path("importedCount").asInt()).isEqualTo(1);
        assertThat(counts()).containsExactly(before.get(0)+1,before.get(1)+1);
    }
    @Test void normalWorkbookImportsAndLaterInvalidOrInFileDuplicateRowRollsBackLedgerAndAudit() throws Exception {
        byte[] normal=workbook(code(),false,false);
        var before=counts(); var response=upload("台账.xlsx",normal,null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("data").path("importedCount").asInt()).isEqualTo(1);
        assertThat(counts()).containsExactly(before.get(0)+1,before.get(1)+1);
        for(boolean duplicate:List.of(false,true)) {
            byte[] workbook=workbook(code(),true,duplicate); before=counts();
            error(upload("台账.xlsx",workbook,null),duplicate?409:400,duplicate?"DUPLICATE_LEDGER_CODE":"VALIDATION_ERROR");
            assertThat(counts()).isEqualTo(before);
        }
    }
    @Test void existingCodeImportOverwritesAndPreservesIdentityWithUpdateAudit() throws Exception {
        var original=create(body(code())); String id=original.path("id").asText();
        byte[] exported=http.getForObject(ROOT+"/export?keyword="+original.path("ledgerCode").asText(),byte[].class);
        byte[] replacement;
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exported)); var out=new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).getRow(1).getCell(4).setCellValue("导入覆盖联系人");
            workbook.write(out); replacement=out.toByteArray();
        }
        var before=counts(); var response=upload("台账.xlsx",replacement,null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var result=response.getBody().path("data");
        assertThat(result.path("importedCount").asInt()).isEqualTo(1);
        assertThat(result.path("createdCount").asInt()).isZero();
        assertThat(result.path("updatedCount").asInt()).isEqualTo(1);
        assertThat(counts()).containsExactly(before.get(0),before.get(1)+1);
        var updated=http.getForObject(ROOT+"/"+id,JsonNode.class).path("data");
        assertThat(updated.path("id")).isEqualTo(original.path("id"));
        assertThat(updated.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(updated.path("version").asText()).isEqualTo("1");
        assertThat(updated.path("applicantContact").asText()).isEqualTo("导入覆盖联系人");
        var history=http.getForObject(ROOT+"/"+id+"/history",JsonNode.class).path("data").path("records");
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).path("action").asText()).isEqualTo("UPDATE");
        assertThat(history.get(0).path("before")).isEqualTo(original);
        assertThat(history.get(0).path("after")).isEqualTo(updated);
    }
    @Test void legacyOrganizationImportOverwritesOriginalRecordAndKeepsAuditOrganization() throws Exception {
        var original=moveToLegacyOrganization(create(body(code())));
        String id=original.path("id").asText();
        var before=counts();
        var response=upload("台账.xlsx",workbookWithCodes(original,original.path("ledgerCode").asText()),null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("data").path("createdCount").asInt()).isZero();
        assertThat(response.getBody().path("data").path("updatedCount").asInt()).isEqualTo(1);
        assertThat(counts()).containsExactly(before.get(0),before.get(1)+1);
        var updated=http.getForObject(ROOT+"/"+id,JsonNode.class).path("data");
        assertThat(updated.path("id")).isEqualTo(original.path("id"));
        assertThat(updated.path("organizationId")).isEqualTo(original.path("organizationId"));
        assertThat(updated.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(updated.path("applicantContact").asText()).isEqualTo("导入覆盖联系人");
        assertThat(updated.path("version").asText()).isEqualTo("1");
        var entry=http.getForObject(ROOT+"/"+id+"/history",JsonNode.class).path("data").path("records").get(0);
        assertThat(entry.path("action").asText()).isEqualTo("UPDATE");
        assertThat(entry.path("before")).isEqualTo(original);
        assertThat(entry.path("after")).isEqualTo(updated);
        assertThat(jdbc.queryForObject("SELECT organization_id FROM ledger_audit WHERE id=?",Long.class,entry.path("id").asText()))
            .isEqualTo(Long.parseLong(original.path("organizationId").asText()));
    }
    @ParameterizedTest(name="ambiguous legacy code rejects whole file: bothLegacy={0}, deletedOther={1}")
    @CsvSource({"false,false","true,false","false,true"})
    void ambiguousSharedCodeRollsBackEarlierCreateUpdateAndAudit(boolean bothLegacy,boolean deletedOther) throws Exception {
        var original=create(body(code()));
        if(bothLegacy) original=moveToLegacyOrganization(original);
        var other=moveToLegacyOrganization(create(body(code())));
        jdbc.update("UPDATE emergency_device_ledger SET ledger_code=? WHERE id=?",original.path("ledgerCode").asText(),other.path("id").asText());
        if(deletedOther) http.delete(ROOT+"/"+other.path("id").asText()+"?version="+other.path("version").asText());
        var earlier=create(body(code()));
        byte[] file=workbookWithCodes(original,code(),earlier.path("ledgerCode").asText(),original.path("ledgerCode").asText());
        var before=counts(); var response=upload("台账.xlsx",file,null);
        error(response,409,"AMBIGUOUS_LEDGER_CODE");
        assertThat(response.getBody().path("message").asText()).contains("第 4 行","台账编号对应多条历史记录，无法确定覆盖目标");
        assertThat(counts()).isEqualTo(before);
        assertThat(http.getForObject(ROOT+"/"+original.path("id").asText(),JsonNode.class).path("data")).isEqualTo(original);
        assertThat(http.getForObject(ROOT+"/"+earlier.path("id").asText(),JsonNode.class).path("data")).isEqualTo(earlier);
    }
    @Test void deletedLegacyCodeRollsBackEarlierCreateUpdateAndAudit() throws Exception {
        var deleted=moveToLegacyOrganization(create(body(code())));
        var earlier=create(body(code()));
        byte[] file=workbookWithCodes(deleted,code(),earlier.path("ledgerCode").asText(),deleted.path("ledgerCode").asText());
        http.delete(ROOT+"/"+deleted.path("id").asText()+"?version="+deleted.path("version").asText());
        var before=counts(); var response=upload("台账.xlsx",file,null);
        error(response,409,"DUPLICATE_LEDGER_CODE");
        assertThat(response.getBody().path("message").asText()).contains("第 4 行","已删除");
        assertThat(counts()).isEqualTo(before);
        assertThat(http.getForObject(ROOT+"/"+earlier.path("id").asText(),JsonNode.class).path("data")).isEqualTo(earlier);
        assertThat(jdbc.queryForObject("SELECT deleted FROM emergency_device_ledger WHERE id=?",Boolean.class,deleted.path("id").asText())).isTrue();
    }
    private JsonNode moveToLegacyOrganization(JsonNode record) {
        String name="旧组织-"+code(); jdbc.update("INSERT INTO organization(name) VALUES (?)",name);
        Long organization=jdbc.queryForObject("SELECT id FROM organization WHERE name=?",Long.class,name);
        String id=record.path("id").asText();
        jdbc.update("UPDATE emergency_device_ledger SET organization_id=? WHERE id=?",organization,id);
        var moved=http.getForObject(ROOT+"/"+id,JsonNode.class).path("data");
        jdbc.update("UPDATE ledger_audit SET organization_id=?,new_value=? WHERE ledger_id=?",organization,moved.toString(),id);
        return moved;
    }
    private byte[] workbookWithCodes(JsonNode source,String... codes) throws Exception {
        byte[] exported=http.getForObject(ROOT+"/export?keyword="+source.path("ledgerCode").asText(),byte[].class);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exported)); var out=new ByteArrayOutputStream()) {
            var sheet=workbook.getSheetAt(0); var template=sheet.getRow(1);
            for(int i=0;i<codes.length;i++) {
                var row=i==0?template:sheet.createRow(i+1);
                if(i>0) for(int col=0;col<template.getLastCellNum();col++) row.createCell(col).setCellValue(template.getCell(col).getStringCellValue());
                row.getCell(0).setCellValue(codes[i]); row.getCell(4).setCellValue("导入覆盖联系人");
            }
            while(sheet.getLastRowNum()>codes.length) sheet.removeRow(sheet.getRow(sheet.getLastRowNum()));
            workbook.write(out); return out.toByteArray();
        }
    }
    private byte[] workbook(String code,boolean second,boolean duplicate) throws Exception {
        var source=create(body(code));
        byte[] exported=http.getForObject(ROOT+"/export?keyword="+source.path("ledgerCode").asText(),byte[].class);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exported)); var out=new ByteArrayOutputStream()) {
            var sheet=workbook.getSheetAt(0); var first=sheet.getRow(1); first.getCell(0).setCellValue(code());
            if(second) {
                var next=sheet.createRow(2);
                for(int i=0;i<first.getLastCellNum();i++) next.createCell(i).setCellValue(first.getCell(i).getStringCellValue());
                next.getCell(0).setCellValue(duplicate?first.getCell(0).getStringCellValue():code());
                if(!duplicate) next.getCell(8).setCellValue("非法日期");
            }
            workbook.write(out); return out.toByteArray();
        }
    }
    private byte[] padZip(byte[] original,int target) throws Exception {
        var entries=new LinkedMultiValueMap<String,byte[]>();
        try(var input=new ZipInputStream(new ByteArrayInputStream(original))) {
            ZipEntry entry; while((entry=input.getNextEntry())!=null) entries.add(entry.getName(),input.readAllBytes());
        }
        String types=new String(entries.getFirst("[Content_Types].xml"),StandardCharsets.UTF_8);
        entries.set("[Content_Types].xml",types.replace("</Types>",
            "<Override PartName=\"/padding.xml\" ContentType=\"application/xml\"/></Types>").getBytes(StandardCharsets.UTF_8));
        byte[] base=storedZip(entries,new byte[0]);
        return storedZip(entries,new byte[target-base.length]);
    }
    private byte[] storedZip(LinkedMultiValueMap<String,byte[]> entries,byte[] padding) throws Exception {
        try(var bytes=new ByteArrayOutputStream(); var zip=new ZipOutputStream(bytes)) {
            for(var entry:entries.entrySet()) zipEntry(zip,entry.getKey(),entry.getValue().get(0));
            if(padding.length>0) {
                java.util.Arrays.fill(padding,(byte)' ');
                System.arraycopy("<padding/>".getBytes(StandardCharsets.UTF_8),0,padding,0,10);
            }
            zipEntry(zip,"padding.xml",padding); zip.finish(); return bytes.toByteArray();
        }
    }
    private void zipEntry(ZipOutputStream zip,String name,byte[] bytes) throws Exception {
        var entry=new ZipEntry(name); entry.setMethod(ZipEntry.STORED); entry.setSize(bytes.length);
        var crc=new CRC32(); crc.update(bytes); entry.setCrc(crc.getValue()); zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
}
