package com.softbei.scenicai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softbei.scenicai.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private String signingSecret;

    @PostConstruct
    void validateSecret() {
        if (properties.jwtSecret() != null && properties.jwtSecret().length() >= 32) {
            signingSecret = properties.jwtSecret();
            return;
        }
        signingSecret = UUID.randomUUID() + "-" + UUID.randomUUID();
        log.warn("JWT_SECRET is not configured or too short. A temporary runtime secret was generated; admin tokens will expire after restart.");
    }

    public String issueAdminToken(String username, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + Math.max(properties.tokenTtlMinutes(), 1) * 60;
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "sub", username,
                "roles", roles,
                "iat", now,
                "exp", expiresAt
        );
        return encode(header) + "." + encode(payload) + "." + sign(encode(header) + "." + encode(payload));
    }

    public Optional<JwtPrincipal> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String signed = parts[0] + "." + parts[1];
            if (!constantTimeEquals(parts[2], sign(signed))) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String subject = String.valueOf(payload.get("sub"));
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) payload.getOrDefault("roles", List.of());
            return Optional.of(new JwtPrincipal(subject, roles));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String encode(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create auth token");
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign auth token");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigestHolder.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record JwtPrincipal(String username, List<String> roles) {
    }

    private static final class MessageDigestHolder {
        private static boolean equals(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
