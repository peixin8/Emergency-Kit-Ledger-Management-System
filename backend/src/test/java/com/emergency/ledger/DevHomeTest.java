package com.emergency.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-url=http://127.0.0.1:5173/")
@AutoConfigureMockMvc
class DevHomeTest {
    @Autowired MockMvc mvc;

    @Test void backendHomeRedirectsToFrontend() throws Exception {
        mvc.perform(get("/").with(request -> { request.setServerName("127.0.0.1"); return request; }))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://127.0.0.1:5173/"));
    }

    @Test void backendHomeUsesLanHostForRedirect() throws Exception {
        mvc.perform(get("/").with(request -> { request.setServerName("192.168.1.2"); return request; }))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://192.168.1.2:5173/"));
    }

    @Test void ledgerApiIsAvailableWithoutLogin() throws Exception {
        mvc.perform(get("/api/v1/ledgers"))
            .andExpect(status().isOk());
    }
}
