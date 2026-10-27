package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SharedLedgerApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String body(String code, String destination, String version) throws Exception {
        return json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode", code), Map.entry("originalAddress", "A机房"),
            Map.entry("destinationAddress", destination), Map.entry("applicationMethod", "ONLINE"),
            Map.entry("applicantContact", "张三"), Map.entry("deviceCondition", "GOOD"),
            Map.entry("direction", "IN"), Map.entry("applicationDate", "2026-10-27"),
            Map.entry("bbuRruPowerWatts", "12.50"), Map.entry("version", version)));
    }

    @Test void anonymousChangesKeepHistoryAfterSoftDelete() throws Exception {
        String code = "SHARED-" + System.nanoTime();
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/ledgers")
            .contentType(MediaType.APPLICATION_JSON).content(body(code, "B机房", "0")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        String id = created.path("id").asText();
        mvc.perform(get("/api/v1/ledgers/{id}", id)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/ledgers/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content(body(code, "C机房", "0"))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/ledgers/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ledgers/{id}", id)).andExpect(status().isNotFound());
        String response = mvc.perform(get("/api/v1/ledgers/{id}/history", id))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode history = json.readTree(response).path("data");
        assertThat(history.path("total").asInt()).isEqualTo(3);
        assertThat(history.path("records").get(0).path("action").asText()).isEqualTo("DELETE");
        assertThat(history.path("records").get(1).path("before").path("destinationAddress").asText()).isEqualTo("B机房");
        assertThat(history.path("records").get(1).path("after").path("destinationAddress").asText()).isEqualTo("C机房");
        assertThat(history.path("records").get(1).path("actorName").asText()).isEqualTo("共享用户");
        mvc.perform(get("/api/v1/ledgers/{id}/history?page=0", id)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ledgers/999999999/history")).andExpect(status().isNotFound());
    }

    @Test void existingOtherOrganizationLedgerRemainsVisibleEditableAndAuditedInPlace() throws Exception {
        mvc.perform(get("/api/v1/ledgers")).andExpect(status().isOk());
        String organization = "LEGACY-ORG-" + System.nanoTime();
        String code = "LEGACY-" + System.nanoTime();
        jdbc.update("INSERT INTO organization(name) VALUES (?)", organization);
        long orgId = jdbc.queryForObject("SELECT id FROM organization WHERE name=?", Long.class, organization);
        jdbc.update("INSERT INTO emergency_device_ledger(organization_id,ledger_code,original_address,destination_address,application_method,applicant_contact,device_condition,direction,application_date) VALUES (?,?,?,?,?,?,?,?,?)",
            orgId,code,"旧址","新址","ONLINE","旧联系人","GOOD","IN",java.sql.Date.valueOf("2026-10-27"));
        long id = jdbc.queryForObject("SELECT id FROM emergency_device_ledger WHERE ledger_code=?", Long.class, code);
        mvc.perform(get("/api/v1/ledgers").param("keyword",code))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/ledgers/{id}",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.organizationId").value(Long.toString(orgId)));
        byte[] export=mvc.perform(get("/api/v1/ledgers/export").param("keyword",code))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(code);
        }
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"B机房","0"))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/ledgers/{id}",id).contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"C机房","0"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT organization_id FROM emergency_device_ledger WHERE id=?",Long.class,id)).isEqualTo(orgId);
        mvc.perform(delete("/api/v1/ledgers/{id}",id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ledgers/{id}/history",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit WHERE ledger_id=? AND organization_id=?",Integer.class,id,orgId)).isEqualTo(2);
    }
}
