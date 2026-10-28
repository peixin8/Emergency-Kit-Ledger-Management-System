package com.emergency.ledger;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class GeocodingApiTest {
    private static final AtomicInteger upstreamCalls = new AtomicInteger();
    private static final AtomicReference<String> upstreamQuery = new AtomicReference<>();
    private static final HttpServer upstream = startUpstream();
    @Autowired MockMvc mvc;

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/geocode/geo", exchange -> {
                upstreamCalls.incrementAndGet();
                upstreamQuery.set(exchange.getRequestURI().getRawQuery());
                java.util.regex.Matcher code = java.util.regex.Pattern.compile("code(\\d{5})").matcher(upstreamQuery.get());
                byte[] body = (upstreamQuery.get().contains("offline") ? "upstream error" :
                    code.find() ? "{\"status\":\"0\",\"info\":\"PROVIDER_ERROR\",\"infocode\":\"" + code.group(1) + "\"}" :
                    upstreamQuery.get().contains("invalidkey") ? "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}" :
                    "{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\",\"count\":\"1\",\"geocodes\":[{\"formatted_address\":\"北京市天安门广场\",\"location\":\"116.404,39.915\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
                int status = upstreamQuery.get().contains("offline") ? 503 : 200;
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void geocodingProperties(DynamicPropertyRegistry registry) {
        registry.add("app.geocoding.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("app.geocoding.key", () -> "test-key");
        registry.add("app.geocoding.min-interval-ms", () -> "0");
    }

    @AfterAll static void stopUpstream() { upstream.stop(0); }

    @Test void explicitSearchUsesAmapContractConvertsCoordinatesAndCachesSameAddress() throws Exception {
        mvc.perform(get("/api/v1/geocoding/search").param("q", "  厦门 软件园  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[0].displayName").value("北京市天安门广场"));
        assertThat(upstreamQuery.get()).contains("address=%E5%8E%A6%E9%97%A8%20%E8%BD%AF%E4%BB%B6%E5%9B%AD")
            .contains("city=%E5%8E%A6%E9%97%A8").contains("key=test-key").contains("output=JSON");
        JsonNode body = new ObjectMapper().readTree(mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门 软件园"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        double lat = body.path("data").path("results").get(0).path("latitude").asDouble();
        double lon = body.path("data").path("results").get(0).path("longitude").asDouble();
        assertThat(lat).isBetween(39.9134, 39.9138);
        assertThat(lon).isBetween(116.3975, 116.3980);
        assertThat(body.path("data").path("results").get(0).path("latitude").asText()).matches("-?\\d+\\.\\d{1,7}");
        int before = upstreamCalls.get();
        mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门 软件园"))
            .andExpect(status().isOk());
        assertThat(upstreamCalls.get()).isEqualTo(before);
    }

    @Test void invalidQueryIsRejectedWithoutSendingAddressUpstream() throws Exception {
        int before = upstreamCalls.get();
        mvc.perform(get("/api/v1/geocoding/search")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/geocoding/search").param("q", "  x  "))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/geocoding/search").param("q", "a".repeat(256)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(upstreamCalls.get()).isEqualTo(before);
    }

    @Test void upstreamFailureAndInvalidKeyAreSanitized() throws Exception {
        mvc.perform(get("/api/v1/geocoding/search").param("q", "offline-location"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GEOCODE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("地址服务暂不可用，请稍后重试"));
        mvc.perform(get("/api/v1/geocoding/search").param("q", "invalidkey-place"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GEOCODE_INVALID_KEY"))
            .andExpect(jsonPath("$.message").value("高德密钥无效或无地理编码权限，请检查服务端配置"));
    }

    @Test void allDocumentedCredentialAndPermissionFailuresHaveActionableStatus() throws Exception {
        for (String code : new String[] {"10009", "10012", "10013", "10041"}) {
            mvc.perform(get("/api/v1/geocoding/search").param("q", "code" + code))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEOCODE_INVALID_KEY"));
        }
    }

    @Test void documentedQuotaAndRateFailuresReturnBusyStatus() throws Exception {
        for (String code : new String[] {"10014", "10015", "10019", "10020", "10021", "10029", "10044", "10045"}) {
            mvc.perform(get("/api/v1/geocoding/search").param("q", "code" + code))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("GEOCODE_BUSY"));
        }
    }

    @Test void blockedAccountOrExhaustedBalanceRequiresAdministratorAction() throws Exception {
        for (String code : new String[] {"10010", "10026", "40000", "40002", "40003"}) {
            mvc.perform(get("/api/v1/geocoding/search").param("q", "code" + code))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEOCODE_ACCOUNT_ISSUE"))
                .andExpect(jsonPath("$.message").value("请联系管理员检查高德账号状态或配额"));
        }
    }
}
