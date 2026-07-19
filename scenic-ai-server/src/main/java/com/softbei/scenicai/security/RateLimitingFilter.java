package com.softbei.scenicai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softbei.scenicai.config.RateLimitProperties;
import com.softbei.scenicai.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isChatRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(properties.chatBurst()));
        if (!bucket.tryConsume(properties.chatPerMinute(), properties.chatBurst())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error("Too many chat requests, please try again later"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isChatRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
                && ("/api/public/chat".equals(path) || "/api/v1/public/chat".equals(path));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(int burst) {
            this.tokens = burst;
            this.updatedAt = Instant.now().toEpochMilli();
        }

        private synchronized boolean tryConsume(int perMinute, int burst) {
            long now = Instant.now().toEpochMilli();
            double refill = Math.max(0, now - updatedAt) * (Math.max(perMinute, 1) / 60000.0);
            tokens = Math.min(Math.max(burst, 1), tokens + refill);
            updatedAt = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }
    }
}
