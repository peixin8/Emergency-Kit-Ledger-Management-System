package com.emergency.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LedgerExcelTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test void exportImportsNewCodeAndRejectsDuplicateWithoutPartialWrite() throws Exception {
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
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedCount").value(1));
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
            duplicate.getCell(0).setCellValue(original);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); xlsx.write(out); invalidFile = out.toByteArray();
        }
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(invalidFile)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第 3 行")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code=?",Integer.class,valid)).isZero();
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file","台账.xlsx",MediaType.APPLICATION_OCTET_STREAM_VALUE,bytes);
    }

    @Test void emptyOversizedAndCorruptFilesAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(new byte[0])))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/ledgers/import").file(file(new byte[2*1024*1024+1])))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/ledgers/import").file(file("not xlsx".getBytes())))
            .andExpect(status().isBadRequest());
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
}
