package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.time.*;
import java.util.zip.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:usability;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LedgerUsabilityTest extends ExcelIdentityFixture {
    String unique() { return "UX-"+System.nanoTime(); }
    JsonNode record(String code) throws Exception {
        create(code);
        return json.readTree(mvc.perform(get("/api/v1/ledgers").param("keyword",code)).andReturn().getResponse().getContentAsString()).path("data").path("records").get(0);
    }
    JsonNode preview(MockMultipartFile file) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/v1/ledgers/import/preview").file(file))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test void missingInvalidAndStaleDeleteLeaveNewDataAndAuditIntact() throws Exception {
        var row=record(unique()); String id=row.path("id").asText();
        jdbc.update("UPDATE emergency_device_ledger SET destination_address='另一人新内容',version=1 WHERE id=?",id);
        var before=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
        mvc.perform(delete("/api/v1/ledgers/"+id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for(String version:List.of("-1","+1","1.0","","9223372036854775808"))
            mvc.perform(delete("/api/v1/ledgers/"+id).param("version",version)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/ledgers/"+id).param("version","0")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT destination_address FROM emergency_device_ledger WHERE id=? AND deleted=FALSE",String.class,id)).isEqualTo("另一人新内容");
        assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(before);
        mvc.perform(delete("/api/v1/ledgers/"+id).param("version","1")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/ledgers/"+id).param("version","1")).andExpect(status().isNotFound());
    }
    @Test void previewShowsClearsWithoutWritesAndConfirmUsesOriginalFileAndGlobalState() throws Exception {
        String code=unique(); var row=record(code); String id=row.path("id").asText();
        jdbc.update("UPDATE emergency_device_ledger SET ptn_port='旧端口',latitude=24,longitude=118,remarks='原备注' WHERE id=?",id);
        var file=workbook(code,unique()); var ledgers=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id");
        var audits=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
        var pre=preview(file);
        assertThat(pre.path("fileHash").asText()).matches("[0-9a-f]{64}");
        assertThat(pre.path("previewFingerprint").asText()).matches("[0-9a-f]{64}");
        assertThat(pre.path("createdCount").asInt()).isEqualTo(1); assertThat(pre.path("updatedCount").asInt()).isEqualTo(1);
        assertThat(pre.path("missingOptionalColumns").toString()).contains("PTN端口（网元+槽口）","设备状况说明","纬度","经度");
        var first=pre.path("rows").get(0);
        assertThat(first.path("targetId").asText()).isEqualTo(id); assertThat(first.path("targetVersion").asText()).isEqualTo("0");
        assertThat(first.path("clearedFields").toString()).contains("ptnPort","latitude","longitude","remarks").doesNotContain("deviceConditionDetail");
        assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(ledgers);
        assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(audits);
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(workbook(code)).param("previewFingerprint",pre.path("previewFingerprint").asText()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_PREVIEW_STALE"));
        create(unique());
        var state=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id");
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file).param("previewFingerprint",pre.path("previewFingerprint").asText()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_PREVIEW_STALE"));
        assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(state);
        var fresh=preview(file);
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file).param("previewFingerprint",fresh.path("previewFingerprint").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(2));
        assertThat(jdbc.queryForObject("SELECT ptn_port FROM emergency_device_ledger WHERE id=?",String.class,id)).isNull();
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file).param("previewFingerprint","x")).andExpect(status().isBadRequest());
    }
    @Test void targetUpdateDeleteAndAbsentTargetCreationInvalidatePreview() throws Exception {
        for(String change:List.of("update","delete","create")) {
            String code=unique(); if(!change.equals("create")) create(code);
            var file=workbook(code); var pre=preview(file);
            if(change.equals("update")) jdbc.update("UPDATE emergency_device_ledger SET version=version+1 WHERE ledger_code=?",code);
            else if(change.equals("delete")) jdbc.update("UPDATE emergency_device_ledger SET deleted=TRUE,version=version+1 WHERE ledger_code=?",code);
            else create(code);
            var before=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id"); var audits=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
            mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file).param("previewFingerprint",pre.path("previewFingerprint").asText()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_PREVIEW_STALE"));
            assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(audits);
        }
    }
    @Test void deletedSearchUsesDeleteAuditDateFallbackAndPagination() throws Exception {
        String code=unique(); var row=record(code); String id=row.path("id").asText();
        mvc.perform(delete("/api/v1/ledgers/"+id).param("version","0")).andExpect(status().isOk());
        jdbc.update("UPDATE ledger_audit SET created_at=TIMESTAMP '2026-10-01 23:59:59' WHERE ledger_id=? AND action='DELETE'",id);
        mvc.perform(get("/api/v1/ledgers/deleted").param("keyword",code).param("deletedFrom","2026-10-01").param("deletedTo","2026-10-01").param("pageSize","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].id").value(id))
            .andExpect(jsonPath("$.data.records[0].deletedAt").value("2026-10-01T23:59:59+08:00"));
        mvc.perform(get("/api/v1/ledgers/deleted").param("keyword",code).param("deletedFrom","2026-10-02")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/ledgers/deleted").param("deletedFrom","2026-10-02").param("deletedTo","2026-10-01")).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM ledger_audit WHERE ledger_id=?",id);
        jdbc.update("UPDATE emergency_device_ledger SET updated_at=TIMESTAMP '2026-09-30 01:02:03' WHERE id=?",id);
        mvc.perform(get("/api/v1/ledgers/deleted").param("keyword",code).param("deletedTo","2026-09-30")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void extendedSearchAndLocationFiltersApplyToListMapAndExports() throws Exception {
        String token=unique(); var row=record(unique()); String id=row.path("id").asText();
        jdbc.update("UPDATE emergency_device_ledger SET asset_owner=?,device_serial_no=?,access_room=?,management_ip='192.0.2.99',business_ip='192.0.2.98',latitude=24,longitude=118 WHERE id=?",token,token,token,id);
        for(String keyword:List.of(token,"192.0.2.99","192.0.2.98")) {
            mvc.perform(get("/api/v1/ledgers").param("keyword",keyword).param("locationStatus","LOCATED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/v1/ledgers/map-points").param("keyword",keyword).param("locationStatus","UNLOCATED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(0)).andExpect(jsonPath("$.data.unlocatedCount").value(0));
            var bytes=mvc.perform(get("/api/v1/ledgers/export").param("keyword",keyword).param("locationStatus","UNLOCATED")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) { assertThat(book.getSheetAt(0).getLastRowNum()).isZero(); }
        }
        for(String path:List.of("","/map-points","/export","/export-batches")) mvc.perform(get("/api/v1/ledgers"+path).param("locationStatus","BAD")).andExpect(status().isBadRequest());
    }
    @Test void batchesSplitAtThousandRowsAndEmptyProducesOneHeaderFile() throws Exception {
        String prefix=unique(); var row=record(prefix+"seed"); long org=row.path("organizationId").asLong();
        jdbc.batchUpdate("INSERT INTO emergency_device_ledger(organization_id,ledger_code,original_address,destination_address,application_method,applicant_contact,device_condition,direction,application_date) VALUES (?,?, '原址','目标','ONLINE','联系人','GOOD','IN','2026-10-28')",
            java.util.stream.IntStream.range(0,1000).mapToObj(i->new Object[]{org,prefix+i}).toList());
        for(String keyword:List.of(prefix,unique())) {
            var bytes=mvc.perform(get("/api/v1/ledgers/export-batches").param("keyword",keyword)).andExpect(status().isOk()).andExpect(content().contentType("application/zip")).andReturn().getResponse().getContentAsByteArray();
            int entries=0,total=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) { ZipEntry entry; while((entry=zip.getNextEntry())!=null) {
                entries++; assertThat(entry.getName()).isEqualTo(String.format("应急宝台账-%03d.xlsx",entries));
                byte[] file=zip.readAllBytes(); assertThat(file.length).isLessThanOrEqualTo(2097152);
                try(var book=new XSSFWorkbook(new ByteArrayInputStream(file))) { assertThat(book.getNumberOfSheets()).isEqualTo(1); int count=book.getSheetAt(0).getLastRowNum(); assertThat(count).isLessThanOrEqualTo(1000); total+=count; }
            }}
            assertThat(total).isEqualTo(keyword.equals(prefix)?1001:0); assertThat(entries).isEqualTo(keyword.equals(prefix)?2:1);
        }
    }
    @Test void nativeExcelDatesHonorBothDateSystems() throws Exception {
        for(boolean use1904:List.of(false,true)) {
            String code=unique(); var input=workbook(code); byte[] bytes;
            try(var book=new XSSFWorkbook(input.getInputStream());var out=new ByteArrayOutputStream()) {
                if(!book.getCTWorkbook().isSetWorkbookPr()) book.getCTWorkbook().addNewWorkbookPr(); book.getCTWorkbook().getWorkbookPr().setDate1904(use1904);
                var cell=book.getSheetAt(0).getRow(1).getCell(8); cell.setCellValue(use1904?44861d:46323d);
                var style=book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("m/d/yy")); cell.setCellStyle(style);
                book.write(out); bytes=out.toByteArray();
            }
            mvc.perform(multipart("/api/v1/ledgers/import").file(new MockMultipartFile("file","date.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,bytes))).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT application_date FROM emergency_device_ledger WHERE ledger_code=?",java.time.LocalDate.class,code)).isEqualTo(LocalDate.of(2026,10,28));
        }
    }
    @Test void previewRejectsInvalidFieldsAndDateFormulasWithoutAnyWrites() throws Exception {
        for(int col:List.of(1,8,15)) {
            byte[] bytes;
            try(var book=new XSSFWorkbook(workbook(unique()).getInputStream());var out=new ByteArrayOutputStream()) {
                var cell=book.getSheetAt(0).getRow(1).getCell(col);
                if(col==1) cell.setCellValue(""); else if(col==8) cell.setCellFormula("TODAY()"); else cell.setCellValue("非法IP");
                book.write(out); bytes=out.toByteArray();
            }
            var before=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id"); var audits=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
            mvc.perform(multipart("/api/v1/ledgers/import/preview").file(new MockMultipartFile("file","invalid.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,bytes)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 2 行")));
            assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(audits);
        }
    }
    @Test void batchesSplitBeforeTwoMiBEvenBelowThousandRows() throws Exception {
        String prefix=unique(); long org=record(unique()).path("organizationId").asLong();
        var random=new Random(42); var data=new ArrayList<Object[]>();
        for(int i=0;i<900;i++) {
            Object[] row=new Object[16]; row[0]=org; row[1]=prefix+i;
            int[] lengths={255,255,100,255,120,100,100,120,120,120,120,2000,255,40};
            for(int j=0;j<lengths.length;j++) { byte[] bytes=new byte[lengths[j]]; for(int k=0;k<bytes.length;k++) bytes[k]=(byte)(33+random.nextInt(90)); row[j+2]=new String(bytes,java.nio.charset.StandardCharsets.US_ASCII); }
            data.add(row);
        }
        jdbc.batchUpdate("INSERT INTO emergency_device_ledger(organization_id,ledger_code,original_address,destination_address,applicant_contact,ptn_port,access_room,fiber_core,network_standard,bbu_model,rru_model,device_serial_no,asset_owner,remarks,device_condition_detail,applicant_phone,application_method,device_condition,direction,application_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ONLINE','GOOD','IN','2026-10-28')",data);
        var bytes=mvc.perform(get("/api/v1/ledgers/export-batches").param("keyword",prefix)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        int parts=0,total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while(zip.getNextEntry()!=null) { parts++; byte[] file=zip.readAllBytes(); assertThat(file.length).isLessThanOrEqualTo(2097152);
                try(var book=new XSSFWorkbook(new ByteArrayInputStream(file))) { total+=book.getSheetAt(0).getLastRowNum(); }
            }
        }
        assertThat(parts).isGreaterThan(1); assertThat(total).isEqualTo(900);
    }

    @org.springframework.beans.factory.annotation.Autowired LedgerImportService imports;
    @org.springframework.beans.factory.annotation.Autowired SharedIdentity identity;
    @org.springframework.beans.factory.annotation.Autowired LedgerRepository repository;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void confirmationKeepsSharedWorkspaceLockedFromParsingThroughCommit() throws Exception {
        String code=unique(); var existing=record(code); String id=existing.path("id").asText();
        var file=workbook(code); var pre=preview(file); var user=identity.current();
        var parsing=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        var attempted=new java.util.concurrent.CountDownLatch(1); var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var blocking=new MockMultipartFile("file","lock.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,file.getBytes()) {
            @Override public java.io.InputStream getInputStream() throws IOException {
                parsing.countDown();
                try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("测试解析等待超时"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                return super.getInputStream();
            }
        };
        try {
            var confirmation=executor.submit(()->imports.confirm(user,blocking,pre.path("previewFingerprint").asText()));
            assertThat(parsing.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var contender=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                attempted.countDown(); repository.lockSharedWorkspace(user.organizationId());
                return jdbc.update("UPDATE emergency_device_ledger SET version=version+1 WHERE id=?",id);
            }));
            assertThat(attempted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,()->contender.get(250,java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown();
            assertThat(confirmation.get(5,java.util.concurrent.TimeUnit.SECONDS).updatedCount()).isEqualTo(1);
            assertThat(contender.get(5,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM emergency_device_ledger WHERE id=?",Long.class,id)).isEqualTo(2);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void batchesRejectMoreThanTenThousandAndDeletedPagingValidatesInputs() throws Exception {
        String prefix=unique(); long org=record(unique()).path("organizationId").asLong();
        jdbc.batchUpdate("INSERT INTO emergency_device_ledger(organization_id,ledger_code,original_address,destination_address,application_method,applicant_contact,device_condition,direction,application_date) VALUES (?,?,'原址','目标','ONLINE','联系人','GOOD','IN','2026-10-28')",
            java.util.stream.IntStream.range(0,10001).mapToObj(i->new Object[]{org,prefix+i}).toList());
        for(String path:List.of("/export","/export-batches")) mvc.perform(get("/api/v1/ledgers"+path).param("keyword",prefix)).andExpect(status().isBadRequest());
        for(String query:List.of("page=0","pageSize=101","deletedFrom=2026-02-30","keyword="+"长".repeat(101)))
            mvc.perform(get("/api/v1/ledgers/deleted?"+query)).andExpect(status().isBadRequest());
    }

}
