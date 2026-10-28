package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LedgerApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private String login(String username,String password) throws Exception {
        String response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",username,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("token").asText();
    }
    private String body(String code,String original,String destination,String version) throws Exception {
        return json.writeValueAsString(Map.ofEntries(
            Map.entry("ledgerCode",code),Map.entry("originalAddress",original),Map.entry("destinationAddress",destination),
            Map.entry("applicationMethod","ONLINE"),Map.entry("applicantContact","张三"),Map.entry("deviceCondition","GOOD"),
            Map.entry("direction","IN"),Map.entry("applicationDate","2026-10-26"),Map.entry("bbuRruPowerWatts","12.50"),Map.entry("version",version)));
    }
    private String create(String token,String code) throws Exception {
        String result=mvc.perform(post("/api/v1/ledgers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"A机房","B机房","0"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).path("data").path("id").asText();
    }
    @Test void crudValidationConflictAndSoftDelete() throws Exception {
        String token=login("testadmin","TestOnlyPassword123!");
        String code="TEST-"+System.nanoTime();
        String id=create(token,code);
        mvc.perform(get("/api/v1/ledgers/{id}",id).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ledgerCode").value(code)).andExpect(jsonPath("$.data.bbuRruPowerWatts").value("12.50"))
            .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+08:00")));
        mvc.perform(post("/api/v1/ledgers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"A机房","B机房","0"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/ledgers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body("SAME-"+System.nanoTime(),"A机房","A机房","0"))).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/ledgers/{id}",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"A机房","C机房","0"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("1"));
        mvc.perform(put("/api/v1/ledgers/{id}",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(code,"A机房","D机房","0"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/ledgers/{id}",id).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ledgers/{id}",id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit WHERE ledger_id=?",Integer.class,Long.parseLong(id))).isEqualTo(3);
    }
    @Test void sharedAccessNeedsNoTokenAndKeepsTrace() throws Exception {
        var result=mvc.perform(get("/api/v1/ledgers"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).path("traceId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        String id=create("","SHARED-SCOPE-"+System.nanoTime());
        mvc.perform(get("/api/v1/ledgers/{id}",id)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/ledgers/{id}",id)).andExpect(status().isOk());
    }
    @Test void blankOptionalPowerBecomesNull() throws Exception {
        String token=login("testadmin","TestOnlyPassword123!");
        String body=body("BLANK-"+System.nanoTime(),"X","Y","0").replace("\"bbuRruPowerWatts\":\"12.50\"","\"bbuRruPowerWatts\":\"\"");
        String result=mvc.perform(post("/api/v1/ledgers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(result).path("data").path("bbuRruPowerWatts").isNull()).isTrue();
    }
    @Test void malformedPathAndPageReturnBadRequest() throws Exception {
        String token=login("testadmin","TestOnlyPassword123!");
        mvc.perform(get("/api/v1/ledgers/abc").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ledgers?page=abc").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ledgers?page=2147483647&pageSize=100").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }
}
