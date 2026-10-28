package com.emergency.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 使用独立内存数据库验证真实 SQL 相等语义，不修改生产库排序规则。
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ledger_alias_ci;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LedgerExcelDatabaseIdentityTest extends ExcelIdentityFixture {
    @BeforeEach void useCaseInsensitiveCodes() {
        jdbc.execute("ALTER TABLE emergency_device_ledger ALTER COLUMN ledger_code VARCHAR_IGNORECASE(64) NOT NULL");
    }

    @Test void aliasesOfExistingRecordRejectSecondRowAndRestoreLedgerAndAudit() throws Exception {
        String code="EXIST-"+System.nanoTime();
        create(code);
        rejectAndAssertRollback(3,code,code.toLowerCase(java.util.Locale.ROOT));
    }

    @Test void aliasesOfNewRecordRejectSecondRowAndRemoveCreatedLedgerAndAudit() throws Exception {
        String code="NEW-"+System.nanoTime();
        rejectAndAssertRollback(3,code,code.toLowerCase(java.util.Locale.ROOT));
    }

    @Test void aliasAfterOtherCreatesAndUpdatesRollsBackEntireBatch() throws Exception {
        String code="TARGET-"+System.nanoTime();
        String other="OTHER-"+System.nanoTime();
        create(code);
        create(other);
        rejectAndAssertRollback(5,other,"ADDED-"+System.nanoTime(),code,code.toLowerCase(java.util.Locale.ROOT));
    }

    @Test void previewRejectsExistingAliasesAndConfirmRollsBackNewDatabaseAliases() throws Exception {
        String existing="PREVIEW-EXIST-"+System.nanoTime(); create(existing);
        var before=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id");
        var audits=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
        mvc.perform(multipart("/api/v1/ledgers/import/preview").file(workbook(existing,existing.toLowerCase(java.util.Locale.ROOT))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("第 3 行")));
        String added="PREVIEW-NEW-"+System.nanoTime();
        var file=workbook(added,added.toLowerCase(java.util.Locale.ROOT));
        var preview=json.readTree(mvc.perform(multipart("/api/v1/ledgers/import/preview").file(file)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(multipart("/api/v1/ledgers/import/confirm").file(file).param("previewFingerprint",preview.path("previewFingerprint").asText()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_LEDGER_CODE")).andExpect(jsonPath("$.message").value(containsString("第 3 行")));
        assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(audits);
    }

    private void rejectAndAssertRollback(int line,String... codes) throws Exception {
        var ledgersBefore=jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id");
        var auditsBefore=jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id");
        mvc.perform(multipart("/api/v1/ledgers/import").file(workbook(codes)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_LEDGER_CODE"))
            .andExpect(jsonPath("$.message").value(containsString("第 "+line+" 行")))
            .andExpect(jsonPath("$.message").value(containsString("文件内编号重复")));
        assertThat(jdbc.queryForList("SELECT * FROM emergency_device_ledger ORDER BY id")).isEqualTo(ledgersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM ledger_audit ORDER BY id")).isEqualTo(auditsBefore);
    }
}

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ledger_alias_cs;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LedgerExcelCaseSensitiveIdentityTest extends ExcelIdentityFixture {
    @Test void databaseDistinctCaseVariantsRemainSeparateImportTargets() throws Exception {
        String code="DISTINCT-"+System.nanoTime();
        String alias=code.toLowerCase(java.util.Locale.ROOT);
        create(code);
        mvc.perform(multipart("/api/v1/ledgers/import").file(workbook(code,alias)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedCount").value(2))
            .andExpect(jsonPath("$.data.createdCount").value(1))
            .andExpect(jsonPath("$.data.updatedCount").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM emergency_device_ledger WHERE ledger_code IN (?,?)",Integer.class,code,alias)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT action FROM ledger_audit WHERE ledger_id IN (SELECT id FROM emergency_device_ledger WHERE ledger_code IN (?,?)) ORDER BY id",String.class,code,alias))
            .containsExactly("CREATE","UPDATE","CREATE");
    }
}

abstract class ExcelIdentityFixture {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    void create(String code) throws Exception {
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "ledgerCode",code,"originalAddress","原址","destinationAddress","目标","applicationMethod","ONLINE",
            "applicantContact","联系人","deviceCondition","GOOD","direction","IN","applicationDate","2026-10-28"))))
            .andExpect(status().isCreated());
    }

    MockMultipartFile workbook(String... codes) throws Exception {
        String[] headers={"台账编号","原始地址","目标地址","进出申请方式","申请联系人","联系电话","应急宝设备状况","进出方向","申请日期","接入机房","纤芯","制式","BBU型号","RRU型号","BBU-RRU功耗(W)","网管IP","网管VLAN","业务IP","业务VLAN","设备序列号","资产归属","备注"};
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("台账");
            var header=sheet.createRow(0);
            for(int col=0;col<headers.length;col++) header.createCell(col).setCellValue(headers[col]);
            for(int index=0;index<codes.length;index++) {
                var row=sheet.createRow(index+1);
                String[] values={codes[index],"覆盖原址"+index,"新目标","线上","新联系人","","正常","进","2026-10-28"};
                for(int col=0;col<headers.length;col++) row.createCell(col).setCellValue(col<values.length?values[col]:"");
            }
            book.write(out);
            return new MockMultipartFile("file","别名.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,out.toByteArray());
        }
    }
}
