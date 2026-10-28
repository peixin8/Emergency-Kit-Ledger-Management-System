package com.emergency.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {
    private static final long CACHE_TTL_NANOS = Duration.ofHours(1).toNanos();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;
    private final long minIntervalNanos;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) { return size() > 256; }
    };
    private long lastUpstreamAt = Long.MIN_VALUE;

    public GeocodingService(ObjectMapper json,
        @Value("${app.geocoding.base-url:https://restapi.amap.com}") String baseUrl,
        @Value("${app.geocoding.key:}") String key,
        @Value("${app.geocoding.min-interval-ms:1000}") long minIntervalMs) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key.trim();
        this.minIntervalNanos = Duration.ofMillis(Math.max(0, minIntervalMs)).toNanos();
    }

    public synchronized SearchResults search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2 || query.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "地址长度须为 2 至 255 个字符");
        }
        if (key.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODE_NOT_CONFIGURED", "地址搜索未配置，请设置高德 Web 服务 Key");
        }
        String cacheKey = query.replaceAll("\\s+", " ");
        long now = System.nanoTime();
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && now - hit.cachedAt < CACHE_TTL_NANOS) return hit.results;
        if (lastUpstreamAt != Long.MIN_VALUE && now - lastUpstreamAt < minIntervalNanos) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GEOCODE_BUSY", "地址查询较频繁，请稍后重试");
        }
        lastUpstreamAt = now;
        SearchResults results = requestUpstream(query);
        cache.put(cacheKey, new CacheEntry(results, System.nanoTime()));
        return results;
    }

    private SearchResults requestUpstream(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String url = baseUrl + "/v3/geocode/geo?address=" + encoded
            + "&city=%E5%8E%A6%E9%97%A8&output=JSON&key=" + encodedKey;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw unavailable();
            JsonNode data = json.readTree(response.body());
            if ("0".equals(data.path("status").asText())) {
                String infocode = data.path("infocode").asText();
                if (List.of("10001", "10002", "10005", "10006", "10007", "10008", "10009", "10012", "10013", "10041").contains(infocode)) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODE_INVALID_KEY", "高德密钥无效或无地理编码权限，请检查服务端配置");
                }
                if (List.of("10010", "10026", "40000", "40002", "40003").contains(infocode)) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODE_ACCOUNT_ISSUE", "请联系管理员检查高德账号状态或配额");
                }
                if (List.of("10003", "10004", "10014", "10015", "10019", "10020", "10021", "10029", "10044", "10045").contains(infocode)) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GEOCODE_BUSY", "地址查询较频繁，请稍后重试");
                }
                throw unavailable();
            }
            JsonNode geocodes = data.path("geocodes");
            if (!"1".equals(data.path("status").asText()) || !geocodes.isArray()) throw unavailable();
            List<SearchResult> results = new ArrayList<>();
            for (JsonNode candidate : geocodes) {
                if (results.size() == 5) break;
                String name = candidate.path("formatted_address").asText("").trim();
                String[] coordinate = candidate.path("location").asText("").split(",", -1);
                if (name.isEmpty() || coordinate.length != 2) continue;
                try {
                    double gcjLongitude = Double.parseDouble(coordinate[0]);
                    double gcjLatitude = Double.parseDouble(coordinate[1]);
                    if (!Double.isFinite(gcjLatitude) || !Double.isFinite(gcjLongitude)
                        || gcjLatitude < -90 || gcjLatitude > 90 || gcjLongitude < -180 || gcjLongitude > 180) continue;
                    double[] wgs = Gcj02.toWgs84(gcjLongitude, gcjLatitude);
                    results.add(new SearchResult(name, decimal(wgs[1]), decimal(wgs[0])));
                } catch (NumberFormatException ignored) { /* Skip an unusable provider candidate. */ }
            }
            return new SearchResults(List.copyOf(results));
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException e) {
            throw unavailable();
        }
    }

    private String decimal(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODE_UNAVAILABLE", "地址服务暂不可用，请稍后重试");
    }

    public record SearchResult(String displayName, String latitude, String longitude) {}
    public record SearchResults(List<SearchResult> results) {}
    private record CacheEntry(SearchResults results, long cachedAt) {}
}
