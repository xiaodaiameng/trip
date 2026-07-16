package com.softbei.scenicai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "提问内容不能为空") String question,
        String sessionId
) {
}
