package com.softbei.scenicai.dto;

public record FeedbackRequest(
        Long recordId,
        Boolean helpful,
        String comment
) {
}
