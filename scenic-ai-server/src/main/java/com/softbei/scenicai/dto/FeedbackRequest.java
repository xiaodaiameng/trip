package com.softbei.scenicai.dto;

import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        Long recordId,
        @NotNull(message = "helpful is required")
        Boolean helpful,
        String comment
) {
}
