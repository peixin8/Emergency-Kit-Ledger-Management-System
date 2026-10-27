package com.emergency.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private final byte[] secret;
    public TokenService(@Value("${app.token-secret:}") String configured) {
        if (configured.isBlank()) {
            secret = new byte[32]; new SecureRandom().nextBytes(secret);
        } else {
            if (configured.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("APP_TOKEN_SECRET 至少 32 字节");
            secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }
    public String issue(long userId) {
        String payload = userId + ":" + Instant.now().plusSeconds(86400).getEpochSecond();
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
    }
    public Long verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) return null;
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            if (payload.length != 2 || Instant.now().getEpochSecond() >= Long.parseLong(payload[1])) return null;
            long id = Long.parseLong(payload[0]);
            return id > 0 ? id : null;
        } catch (RuntimeException e) { return null; }
    }
    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("令牌签名不可用", e); }
    }
}
