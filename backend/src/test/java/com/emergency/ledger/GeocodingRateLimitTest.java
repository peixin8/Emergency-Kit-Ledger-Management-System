package com.emergency.ledger;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class GeocodingRateLimitTest {
    private static final AtomicInteger upstreamCalls = new AtomicInteger();
    private static final HttpServer upstream = startUpstream();
    @Autowired MockMvc mvc;

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/geocode/geo", exchange -> {
                upstreamCalls.incrementAndGet();
                byte[] body = "{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\",\"count\":\"0\",\"geocodes\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
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
        registry.add("app.geocoding.min-interval-ms", () -> "1000");
    }

    @AfterAll static void stopUpstream() { upstream.stop(0); }

    @Test void distinctRapidQueriesShareOneUpstreamRequestWhileCachedQueryStillWorks() throws Exception {
        mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门软件园A"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门软件园A"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geocoding/search").param("q", "厦门软件园B"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("GEOCODE_BUSY"));
        assertThat(upstreamCalls.get()).isEqualTo(1);
    }
}
