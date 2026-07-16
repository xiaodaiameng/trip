package com.softbei.scenicai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RouteRequest(
        List<String> interests,
        @NotNull(message = "请填写游览时长") Integer durationHours,
        String pace,
        String companionType
) {
}
