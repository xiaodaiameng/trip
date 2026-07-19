package com.softbei.scenicai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        int chatPerMinute,
        int chatBurst
) {
}
