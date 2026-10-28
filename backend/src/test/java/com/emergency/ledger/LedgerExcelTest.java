package com.emergency.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LedgerExcelTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerService ledgerService;
    @Autowired SharedIdentity identity;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void deleteWaitsForTheSameWorkspaceLockUsedByImport() throws Exception {
        String code="DELETE-LOCK-"+System.nanoTime();
        String body=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",code),Map.entry("originalAddress","原址"),Map.entry("destinationAddress","目标"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","联系人"),Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-27")));
        String created=mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=Long.parseLong(json.readTree(created).path("data").path("id").asText());
        CountDownLatch locked=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("SELECT id FROM organization WHERE id=? FOR UPDATE",Long.class,identity.current().organizationId());
                locked.countDown();
                try { assertThat(release.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            CountDownLatch started=new CountDownLatch(1);
            var deletion=pool.submit(() -> { started.countDown(); ledgerService.delete(identity.current(),id,"0"); });
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deletion.get(500,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(jdbc.queryForObject("SELECT deleted FROM emergency_device_ledger WHERE id=?",Boolean.class,id)).isFalse();
            release.countDown();
            holder.get(5,TimeUnit.SECONDS);
            deletion.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT deleted FROM emergency_device_ledger WHERE id=?",Boolean.class,id)).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test void coordinatesRoundTripAndOldTemplateStillImports() throws Exception {
        String source="COORD-"+System.nanoTime();
        String request=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",source),Map.entry("originalAddress","源机房"),Map.entry("destinationAddress","目标机房"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","李四"),Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-27"),
            Map.entry("latitude","24.4798341"),Map.entry("longitude","118.0894257")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",source))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String newCode="COORD-COPY-"+System.nanoTime(); String oldCode="OLD-COPY-"+System.nanoTime();
        byte[] newSheet; byte[] oldSheet;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet=xlsx.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(24).getStringCellValue()).isEqualTo("纬度");
            assertThat(sheet.getRow(0).getCell(25).getStringCellValue()).isEqualTo("经度");
            assertThat(sheet.getRow(1).getCell(24).getStringCellValue()).isEqualTo("24.4798341");
            sheet.getRow(1).getCell(0).setCellValue(newCode);
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); newSheet=out.toByteArray();
            try(XSSFWorkbook oldXlsx=new XSSFWorkbook();ByteArrayOutputStream oldOut=new ByteArrayOutputStream()) {
                var oldTab=oldXlsx.createSheet("旧模板"); var oldHeader=oldTab.createRow(0); var oldRow=oldTab.createRow(1);
                for(int col=0;col<22;col++) {
                    int sourceCol=col<9?col:col+1;
                    oldHeader.createCell(col).setCellValue(sheet.getRow(0).getCell(sourceCol).getStringCellValue());
                    oldRow.createCell(col).setCellValue(sheet.getRow(1).getCell(sourceCol).getStringCellValue());
                }
                oldRow.getCell(0).setCellValue(oldCode);
                oldXlsx.write(oldOut); oldSheet=oldOut.toByteArray();
            }
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(newSheet))).andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(oldSheet))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT latitude FROM emergency_device_ledger WHERE ledger_code=?",java.math.BigDecimal.class,newCode))
            .isEqualByComparingTo("24.4798341");
        assertThat(jdbc.queryForObject("SELECT latitude FROM emergency_device_ledger WHERE ledger_code=?",java.math.BigDecimal.class,oldCode)).isNull();
    }

    @Test void exportImportsNewCodeAndRejectsDuplicateWithinFileWithoutPartialWrite() throws Exception {
        String original = "EXCEL-" + System.nanoTime();
        String request = json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",original), Map.entry("originalAddress","源机房"),
            Map.entry("destinationAddress","目标机房"), Map.entry("applicationMethod","ONLINE"),
            Map.entry("applicantContact","李四"), Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"), Map.entry("applicationDate","2026-10-27"),
            Map.entry("bbuRruPowerWatts","13.25"), Map.entry("version","0")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated());
        byte[] exported = mvc.perform(get("/api/v1/ledgers/export").param("keyword", original))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        String imported = "COPY-" + System.nanoTime();
        byte[] workbook;
        try (XSSFWorkbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(xlsx.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("台账编号");
            assertThat(xlsx.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(original);
            xlsx.getSheetAt(0).getRow(1).getCell(0).setCellValue(imported);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); xlsx.write(out); workbook = out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(1))
            .andExpect(jsonPath("$.data.createdCount").value(1))
            .andExpect(jsonPath("$.data.updatedCount").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,imported)).isEqualTo(1);

        String valid = "ROLLBACK-" + System.nanoTime();
        byte[] invalidFile;
        try (XSSFWorkbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = xlsx.getSheetAt(0);
            sheet.getRow(1).getCell(0).setCellValue(valid);
            var duplicate = sheet.createRow(2);
            for (int col=0; col<sheet.getRow(1).getLastCellNum(); col++) {
                duplicate.createCell(col).setCellValue(sheet.getRow(1).getCell(col).getStringCellValue());
            }
            duplicate.getCell(0).setCellValue(valid);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); xlsx.write(out); invalidFile = out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(invalidFile)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 3 行")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,valid)).isZero();
    }

    @Test void reimportOverwritesEveryBusinessFieldButPreservesIdentityAndHistory() throws Exception {
        String code="OVERWRITE-"+System.nanoTime();
        String absent="ABSENT-"+System.nanoTime();
        String body=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",code),Map.entry("originalAddress","旧原址"),Map.entry("destinationAddress","旧目标"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","旧联系人"),Map.entry("applicantPhone","13800000000"),
            Map.entry("deviceCondition","GOOD"),Map.entry("deviceConditionDetail","旧说明"),Map.entry("direction","IN"),
            Map.entry("applicationDate","2026-10-27"),Map.entry("ptnPort","旧端口"),Map.entry("latitude","24.4"),Map.entry("longitude","118.1")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace(code,absent))).andExpect(status().isCreated());
        var before=jdbc.queryForMap("SELECT id,created_at,version FROM emergency_device_ledger WHERE ledger_code=?",code);
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] replacement;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var row=xlsx.getSheetAt(0).getRow(1);
            row.getCell(1).setCellValue("新原址"); row.getCell(2).setCellValue("新目标");
            row.getCell(4).setCellValue("新联系人"); row.getCell(5).setCellValue("");
            row.getCell(9).setCellValue(""); row.getCell(23).setCellValue("");
            row.getCell(24).setCellValue(""); row.getCell(25).setCellValue("");
            xlsx.write(out); replacement=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(replacement)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(1))
            .andExpect(jsonPath("$.data.createdCount").value(0))
            .andExpect(jsonPath("$.data.updatedCount").value(1));
        var after=jdbc.queryForMap("SELECT id,created_at,version,original_address,destination_address,applicant_contact,applicant_phone,ptn_port,device_condition_detail,latitude,longitude FROM emergency_device_ledger WHERE ledger_code=?",code);
        assertThat(after.get("id")).isEqualTo(before.get("id"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(((Number)after.get("version")).longValue()).isEqualTo(((Number)before.get("version")).longValue()+1);
        assertThat(after.get("original_address")).isEqualTo("新原址");
        assertThat(after.get("destination_address")).isEqualTo("新目标");
        assertThat(after.get("applicant_contact")).isEqualTo("新联系人");
        assertThat(after.get("applicant_phone")).isNull();
        assertThat(after.get("ptn_port")).isNull();
        assertThat(after.get("device_condition_detail")).isNull();
        assertThat(after.get("latitude")).isNull();
        assertThat(after.get("longitude")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,absent)).isEqualTo(1);
        var actions=jdbc.queryForList("SELECT action FROM ledger_audit WHERE ledger_id=? ORDER BY id",String.class,before.get("id"));
        assertThat(actions).containsExactly("CREATE","UPDATE");
    }

    @Test void deletedCodeRejectsBatchAndRollsBackEarlierUpdatesAndCreates() throws Exception {
        String active="BATCH-ACTIVE-"+System.nanoTime();
        String deleted="BATCH-DELETED-"+System.nanoTime();
        String created="BATCH-NEW-"+System.nanoTime();
        String template=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",active),Map.entry("originalAddress","旧地址"),Map.entry("destinationAddress","目标"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","联系人"),Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-27")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(template)).andExpect(status().isCreated());
        String deletedResponse=mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(template.replace(active,deleted))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String deletedId=json.readTree(deletedResponse).path("data").path("id").asText();
        mvc.perform(delete("/api/v1/ledgers/"+deletedId).param("version","0")).andExpect(status().isOk());
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",active))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] workbook;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sheet=xlsx.getSheetAt(0); var first=sheet.getRow(1);
            first.getCell(1).setCellValue("覆盖地址");
            var second=sheet.createRow(2); var third=sheet.createRow(3);
            for(int col=0;col<first.getLastCellNum();col++) {
                String value=first.getCell(col).getStringCellValue();
                second.createCell(col).setCellValue(value); third.createCell(col).setCellValue(value);
            }
            second.getCell(0).setCellValue(created); third.getCell(0).setCellValue(deleted);
            xlsx.write(out); workbook=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 4 行")));
        assertThat(jdbc.queryForObject("SELECT original_address FROM emergency_device_ledger WHERE ledger_code=?",String.class,active)).isEqualTo("旧地址");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,created)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit WHERE ledger_id=(SELECT id FROM emergency_device_ledger WHERE ledger_code=?)",Integer.class,active)).isEqualTo(1);
    }

    @Test void olderTemplateClearsFieldsThatHaveNoColumns() throws Exception {
        String code="OLD-CLEAR-"+System.nanoTime();
        String body=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",code),Map.entry("originalAddress","原址"),Map.entry("destinationAddress","目标"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","联系人"),Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-27"),Map.entry("ptnPort","旧 PTN"),
            Map.entry("deviceConditionDetail","旧说明"),Map.entry("latitude","24.4"),Map.entry("longitude","118.1")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] oldWorkbook;
        try(XSSFWorkbook source=new XSSFWorkbook(new ByteArrayInputStream(exported));XSSFWorkbook old=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sourceSheet=source.getSheetAt(0); var oldSheet=old.createSheet("旧版");
            for(int line=0;line<=1;line++) {
                var row=oldSheet.createRow(line);
                for(int col=0;col<22;col++) {
                    int sourceCol=col<9?col:col+1;
                    row.createCell(col).setCellValue(sourceSheet.getRow(line).getCell(sourceCol).getStringCellValue());
                }
            }
            old.write(out); oldWorkbook=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(oldWorkbook)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(1));
        var row=jdbc.queryForMap("SELECT ptn_port,device_condition_detail,latitude,longitude FROM emergency_device_ledger WHERE ledger_code=?",code);
        assertThat(row.values()).containsOnlyNulls();
    }

    @Test void reimportReplacesEveryBusinessColumnIncludingStatusNetworkAndAssetFields() throws Exception {
        String prefix="ALL-FIELDS-"+System.nanoTime()+"-";
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(sourceWorkbook(prefix))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdCount").value(11));
        String code=prefix+"2";
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] replacement;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var row=xlsx.getSheetAt(0).getRow(1);
            String[] values={code,"新原址","新目标","纸质","新联系人","13900000000","维修中","出","2026-10-28",
                "NEW-PTN","新机房","24","5G","NEW-BBU","NEW-RRU","12.50","192.0.2.10","100",
                "198.51.100.10","200","NEW-SN","NEW-OWNER","NEW-REMARKS","NEW-DETAIL","24.5","118.2"};
            for(int col=0;col<values.length;col++) row.getCell(col).setCellValue(values[col]);
            xlsx.write(out); replacement=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(replacement)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(1));
        String id=jdbc.queryForObject("SELECT CAST(id AS VARCHAR) FROM emergency_device_ledger WHERE ledger_code=?",String.class,code);
        mvc.perform(get("/api/v1/ledgers/"+id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ledgerCode").value(code))
            .andExpect(jsonPath("$.data.originalAddress").value("新原址"))
            .andExpect(jsonPath("$.data.destinationAddress").value("新目标"))
            .andExpect(jsonPath("$.data.applicationMethod").value("PAPER"))
            .andExpect(jsonPath("$.data.applicantContact").value("新联系人"))
            .andExpect(jsonPath("$.data.applicantPhone").value("13900000000"))
            .andExpect(jsonPath("$.data.deviceCondition").value("REPAIR"))
            .andExpect(jsonPath("$.data.deviceConditionDetail").value("NEW-DETAIL"))
            .andExpect(jsonPath("$.data.direction").value("OUT"))
            .andExpect(jsonPath("$.data.applicationDate").value("2026-10-28"))
            .andExpect(jsonPath("$.data.ptnPort").value("NEW-PTN"))
            .andExpect(jsonPath("$.data.accessRoom").value("新机房"))
            .andExpect(jsonPath("$.data.fiberCore").value("24"))
            .andExpect(jsonPath("$.data.networkStandard").value("5G"))
            .andExpect(jsonPath("$.data.bbuModel").value("NEW-BBU"))
            .andExpect(jsonPath("$.data.rruModel").value("NEW-RRU"))
            .andExpect(jsonPath("$.data.bbuRruPowerWatts").value("12.50"))
            .andExpect(jsonPath("$.data.managementIp").value("192.0.2.10"))
            .andExpect(jsonPath("$.data.managementVlan").value(100))
            .andExpect(jsonPath("$.data.businessIp").value("198.51.100.10"))
            .andExpect(jsonPath("$.data.businessVlan").value(200))
            .andExpect(jsonPath("$.data.deviceSerialNo").value("NEW-SN"))
            .andExpect(jsonPath("$.data.assetOwner").value("NEW-OWNER"))
            .andExpect(jsonPath("$.data.remarks").value("NEW-REMARKS"))
            .andExpect(jsonPath("$.data.latitude").value("24.5"))
            .andExpect(jsonPath("$.data.longitude").value("118.2"));
    }

    @Test void identicalReimportDoesNotChangeVersionTimestampOrHistoryAndEquivalentNumbersAreUnchanged() throws Exception {
        String prefix="NO-CHANGE-"+System.nanoTime()+"-";
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(sourceWorkbook(prefix))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdCount").value(11));
        String code=prefix+"2";
        var before=jdbc.queryForMap("SELECT id,version,updated_at FROM emergency_device_ledger WHERE ledger_code=?",code);
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(exported)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(1))
            .andExpect(jsonPath("$.data.createdCount").value(0))
            .andExpect(jsonPath("$.data.updatedCount").value(0))
            .andExpect(jsonPath("$.data.unchangedCount").value(1));
        byte[] equivalent;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var row=xlsx.getSheetAt(0).getRow(1);
            row.getCell(15).setCellValue("2000W");
            row.getCell(24).setCellValue("024.4798341");
            row.getCell(25).setCellValue("0118.0894257");
            xlsx.write(out); equivalent=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(equivalent)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unchangedCount").value(1));
        var after=jdbc.queryForMap("SELECT id,version,updated_at FROM emergency_device_ledger WHERE ledger_code=?",code);
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit WHERE ledger_id=?",Integer.class,before.get("id"))).isEqualTo(1);
    }

    @Test void mixedImportCountsCreatedUpdatedAndUnchangedRows() throws Exception {
        String prefix="MIXED-"+System.nanoTime()+"-";
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(sourceWorkbook(prefix))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdCount").value(11));
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",prefix))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] mixed;
        String newCode=prefix+"NEW";
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sheet=xlsx.getSheetAt(0); var first=sheet.getRow(1);
            first.getCell(4).setCellValue("更新联系人");
            var added=sheet.createRow(sheet.getLastRowNum()+1);
            for(int col=0;col<first.getLastCellNum();col++) added.createCell(col).setCellValue(first.getCell(col).getStringCellValue());
            added.getCell(0).setCellValue(newCode);
            xlsx.write(out); mixed=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(mixed)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(12))
            .andExpect(jsonPath("$.data.createdCount").value(1))
            .andExpect(jsonPath("$.data.updatedCount").value(1))
            .andExpect(jsonPath("$.data.unchangedCount").value(10));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,newCode)).isEqualTo(1);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file","台账.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,bytes);
    }

    @Test void emptyOversizedAndCorruptFilesAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(new byte[0])))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(new byte[2*1024*1024+1])))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/ledgers/import").file(file("not xlsx".getBytes())))
            .andExpect(status().isBadRequest());
    }

    @Test void businessSizeGuardAlsoReturnsCompleteLengthDelimitedJson() throws Exception {
        var response=mvc.perform(multipart("/api/v1/ledgers/import").file(file(new byte[2097153])))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"))
            .andReturn().getResponse();
        assertThat(response.getHeader("Content-Length")).isEqualTo(Integer.toString(response.getContentAsByteArray().length));
        assertThat(response.getHeader("Content-Type")).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test void formulaInLaterRowReportsLineAndRollsBackEarlierRows() throws Exception {
        String code="FORMULA-SOURCE-"+System.nanoTime();
        String body=json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",code),Map.entry("originalAddress","A机房"),Map.entry("destinationAddress","B机房"),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","李四"),
            Map.entry("deviceCondition","GOOD"),Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-27")));
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String valid="FORMULA-VALID-"+System.nanoTime();
        byte[] workbook;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet=xlsx.getSheetAt(0);
            var first=sheet.getRow(1);
            first.getCell(0).setCellValue(valid);
            var second=sheet.createRow(2);
            for(int col=0;col<first.getLastCellNum();col++) second.createCell(col).setCellValue(first.getCell(col).getStringCellValue());
            second.getCell(0).setCellFormula("1+1");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); workbook=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 3 行")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,valid)).isZero();
    }

    @Test void sourceShapeImportsElevenRowsAndPreservesNewFields() throws Exception {
        String prefix="SOURCE-"+System.nanoTime()+"-";
        byte[] workbook=sourceWorkbook(prefix);
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(11));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isEqualTo(11);
        var row=jdbc.queryForMap("SELECT ptn_port,device_condition_detail,direction,bbu_rru_power_watts,original_address,destination_address FROM emergency_device_ledger WHERE ledger_code=?",prefix+"2");
        assertThat(row.get("ptn_port")).isEqualTo("PTN-2/3");
        assertThat(row.get("device_condition_detail")).isEqualTo("开关电源坏（需要2000W交转直）");
        assertThat(row.get("direction")).isEqualTo("UNSPECIFIED");
        assertThat(row.get("bbu_rru_power_watts")).isEqualTo(new java.math.BigDecimal("2000.00"));
        assertThat(row.get("original_address")).isEqualTo(row.get("destination_address"));
        assertThat(jdbc.queryForObject("SELECT applicant_phone FROM emergency_device_ledger WHERE ledger_code=?",String.class,prefix+"2"))
            .isEqualTo("13800000000");
        String audit=jdbc.queryForObject("SELECT a.new_value FROM ledger_audit a JOIN emergency_device_ledger l ON l.id=a.ledger_id WHERE l.ledger_code=?",String.class,prefix+"2");
        assertThat(json.readTree(audit).path("ptnPort").asText()).isEqualTo("PTN-2/3");
        assertThat(json.readTree(audit).path("deviceConditionDetail").asText()).isEqualTo("开关电源坏（需要2000W交转直）");
        byte[] exported=mvc.perform(get("/api/v1/ledgers/export").param("keyword",prefix))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(xlsx.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)26);
            assertThat(xlsx.getSheetAt(0).getRow(0).getCell(9).getStringCellValue()).isEqualTo("PTN端口（网元+槽口）");
            for(var record:xlsx.getSheetAt(0)) if(record.getRowNum()>0) record.getCell(0).setCellValue("ROUND-"+record.getCell(0).getStringCellValue());
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out);
            mvc.perform(multipart("/api/v1/ledgers/import").file(file(out.toByteArray())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(11));
        }
    }

    @Test void sourceShapeInvalidValueAndDuplicateRollBackWholeFile() throws Exception {
        String prefix="BAD-SOURCE-"+System.nanoTime()+"-";
        byte[] bad;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)))) {
            xlsx.getSheetAt(0).getRow(2).getCell(15).setCellValue("2kW");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); bad=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(bad)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("第 3 行"),org.hamcrest.Matchers.containsString("BBU-RRU功耗(W)"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isZero();
        byte[] duplicate;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)))) {
            xlsx.getSheetAt(0).getRow(3).getCell(0).setCellValue(prefix+"1");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); duplicate=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(duplicate)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 4 行")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isZero();
    }

    @Test void oldTwentyFourColumnsAndReorderedHeadersStillImport() throws Exception {
        String prefix="OLD24-"+System.nanoTime()+"-";
        byte[] workbook;
        try(XSSFWorkbook source=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)));
            XSSFWorkbook old=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sourceSheet=source.getSheetAt(0); var target=old.createSheet("旧模板");
            for(int line=0;line<=11;line++) {
                var targetRow=target.createRow(line);
                for(int col=0;col<24;col++) {
                    int sourceCol=col<9?col:col+1;
                    var input=sourceSheet.getRow(line).getCell(sourceCol);
                    if(input.getCellType()==org.apache.poi.ss.usermodel.CellType.NUMERIC) targetRow.createCell(col).setCellValue(input.getNumericCellValue());
                    else targetRow.createCell(col).setCellValue(input.getStringCellValue());
                }
                if(line>0) {
                    var a=targetRow.getCell(0); var b=targetRow.getCell(5);
                    String code=a.getStringCellValue(); double phone=b.getNumericCellValue();
                    a.setCellValue(phone); b.setCellValue(code);
                }
            }
            String headerCode=target.getRow(0).getCell(0).getStringCellValue();
            String headerPhone=target.getRow(0).getCell(5).getStringCellValue();
            target.getRow(0).getCell(0).setCellValue(headerPhone);
            target.getRow(0).getCell(5).setCellValue(headerCode);
            old.write(out); workbook=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(11));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isEqualTo(11);
    }

    @Test void unknownConditionAndDuplicateHeaderReturnSpecificErrors() throws Exception {
        String prefix="UNKNOWN-"+System.nanoTime()+"-";
        byte[] unknown;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)))) {
            xlsx.getSheetAt(0).getRow(2).getCell(6).setCellValue("无法判断");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); unknown=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(unknown)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("第 3 行"),org.hamcrest.Matchers.containsString("应急宝设备状况"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isZero();
        byte[] duplicateHeader;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)))) {
            xlsx.getSheetAt(0).getRow(0).getCell(9).setCellValue("台账编号");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); duplicateHeader=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(duplicateHeader)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("表头重复")));
    }

    @Test void validationErrorsNameTheSourceColumnInChinese() throws Exception {
        assertColumnError(10,"机房".repeat(61),"接入机房");
        assertColumnError(16,"not-an-ip","网管IP");
        assertColumnError(23,"91.0000000","纬度");
        assertColumnError(15,"99999999999W","BBU-RRU功耗(W)");
    }

    private void assertColumnError(int column,String value,String expectedColumn) throws Exception {
        String prefix="COLUMN-"+System.nanoTime()+"-";
        byte[] workbook;
        try(XSSFWorkbook xlsx=new XSSFWorkbook(new ByteArrayInputStream(sourceWorkbook(prefix)))) {
            xlsx.getSheetAt(0).getRow(2).getCell(column).setCellValue(value);
            ByteArrayOutputStream out=new ByteArrayOutputStream(); xlsx.write(out); workbook=out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(workbook)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("第 3 行"),org.hamcrest.Matchers.containsString(expectedColumn))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Integer.class,prefix+"%"))
            .isZero();
    }

    private byte[] sourceWorkbook(String prefix) throws Exception {
        String[] headers={"台账编号","原始地址","目标地址","进出申请方式","申请联系人","联系电话","应急宝设备状况","进出方向","申请日期","PTN端口（网元+槽口）","接入机房","纤芯","制式","BBU型号","RRU型号","BBU-RRU功耗(W)","网管IP","网管VLAN","业务IP","业务VLAN","设备序列号","资产归属","备注","纬度","经度"};
        try(XSSFWorkbook xlsx=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sheet=xlsx.createSheet("应急宝台账"); var head=sheet.createRow(0);
            for(int col=0;col<headers.length;col++) head.createCell(col).setCellValue(headers[col]);
            for(int index=1;index<=11;index++) {
                var row=sheet.createRow(index);
                String[] values={prefix+index,"同址机房","同址机房","线上联系申请","测试联系人","13800000000",index==2?"开关电源坏（需要2000W交转直）":"正常",index==2?"":"进","2026-10-27","PTN-2/3","测试接入机房","12","4G","BBU-X","RRU-Y",index==2?"2000W":"1000W","192.0.2.1","1947","192.0.2.2","947","SN-X","测试资产","测试备注","24.4798341","118.0894257"};
                for(int col=0;col<values.length;col++) {
                    if(col==5) row.createCell(col).setCellValue(13800000000d);
                    else row.createCell(col).setCellValue(values[col]);
                }
            }
            xlsx.write(out); return out.toByteArray();
        }
    }
}
