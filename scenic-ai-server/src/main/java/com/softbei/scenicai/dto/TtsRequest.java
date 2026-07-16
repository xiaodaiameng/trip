package com.softbei.scenicai.dto;

import jakarta.validation.constraints.NotBlank;

public record TtsRequest(
        @NotBlank(message = "朗读内容不能为空") String text,
        String voiceName
) {
}
