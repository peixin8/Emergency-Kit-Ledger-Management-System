package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LedgerLocationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private Map<String,Object> body(String code) {
        Map<String,Object> b=new HashMap<>();
        b.put("ledgerCode",code); b.put("originalAddress","A机房"); b.put("destinationAddress","厦门市思明区设备点");
        b.put("applicationMethod","ONLINE"); b.put("applicantContact","张三"); b.put("deviceCondition","GOOD");
        b.put("direction","IN"); b.put("applicationDate","2026-10-27"); b.put("version","0");
        return b;
    }
    private JsonNode create(Map<String,Object> b) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(b))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).path("data");
    }
    private int unlocatedCount() throws Exception {
        String response=mvc.perform(get("/api/v1/ledgers/map-points"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("unlocatedCount").asInt();
    }
    @Test void precisePointAppearsOnlyForActiveLocatedLedgerAndAuditKeepsCoordinates() throws Exception {
        String code="LOC-"+System.nanoTime(); Map<String,Object> b=body(code);
        b.put("latitude","24.4798341"); b.put("longitude","118.0894257");
        JsonNode created=create(b); String id=created.path("id").asText();
        assertThat(created.path("latitude").asText()).isEqualTo("24.4798341");
        mvc.perform(get("/api/v1/ledgers/map-points"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[?(@.id=='"+id+"')].latitude").value("24.4798341"));
        mvc.perform(get("/api/v1/ledgers/{id}/history",id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].after.longitude").value("118.0894257"));
        mvc.perform(delete("/api/v1/ledgers/{id}",id).param("version",json.readTree(mvc.perform(get("/api/v1/ledgers/{id}",id)).andReturn().getResponse().getContentAsString()).path("data").path("version").asText())).andExpect(status().isOk());
        String map=mvc.perform(get("/api/v1/ledgers/map-points"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for(JsonNode point:json.readTree(map).path("data").path("records")) assertThat(point.path("id").asText()).isNotEqualTo(id);
    }
    @Test void missingCoordinateIsCountedAndInvalidPairsOrRangesAreRejected() throws Exception {
        int before=unlocatedCount();
        String code="NOLOC-"+System.nanoTime(); JsonNode created=create(body(code));
        String id=created.path("id").asText();
        assertThat(unlocatedCount()).isEqualTo(before+1);
        mvc.perform(delete("/api/v1/ledgers/{id}",id).param("version",json.readTree(mvc.perform(get("/api/v1/ledgers/{id}",id)).andReturn().getResponse().getContentAsString()).path("data").path("version").asText())).andExpect(status().isOk());
        assertThat(unlocatedCount()).isEqualTo(before);
        Map<String,Object> invalid=body("BADLOC-"+System.nanoTime()); invalid.put("latitude","24.1");
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid.put("longitude","181");
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid.put("longitude","118.12345678");
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid.put("longitude","118.1"); invalid.put("latitude","91");
        mvc.perform(post("/api/v1/ledgers").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertThat(unlocatedCount()).isEqualTo(before);
    }

    @Test void updateCanLocateAndClearPointWithHistoricalSnapshots() throws Exception {
        String code="EDITLOC-"+System.nanoTime(); Map<String,Object> b=body(code);
        String id=create(b).path("id").asText();
        b.put("latitude","24.5"); b.put("longitude","118.1");
        mvc.perform(put("/api/v1/ledgers/{id}",id).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(b))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.latitude").value("24.5"));
        b.put("version","1"); b.remove("latitude"); b.remove("longitude");
        mvc.perform(put("/api/v1/ledgers/{id}",id).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(b))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.latitude").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/ledgers/{id}/history",id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].before.latitude").value("24.5"));
        mvc.perform(delete("/api/v1/ledgers/{id}",id).param("version",json.readTree(mvc.perform(get("/api/v1/ledgers/{id}",id)).andReturn().getResponse().getContentAsString()).path("data").path("version").asText())).andExpect(status().isOk());
    }
}
