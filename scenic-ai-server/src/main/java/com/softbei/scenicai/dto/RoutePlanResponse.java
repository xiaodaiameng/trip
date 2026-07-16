package com.softbei.scenicai.dto;

import java.util.List;

public record RoutePlanResponse(
        String title,
        Integer totalDurationHours,
        List<RouteStop> stops,
        List<String> tips
) {
    public record RouteStop(
            Integer order,
            String attractionName,
            String highlight,
            String duration,
            String reason
    ) {
    }
}
