package com.emergency.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.geocoding.key=") @AutoConfigureMockMvc
class GeocodingNoKeyTest {
    @Autowired MockMvc mvc;

    @Test void missingWebServiceKeyHasActionableErrorWithoutCallingProvider() throws Exception {
        mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门软件园"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GEOCODE_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.message").value("地址搜索未配置，请设置高德 Web 服务 Key"));
    }
}
