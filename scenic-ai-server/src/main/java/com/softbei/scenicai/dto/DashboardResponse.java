package com.softbei.scenicai.dto;

import java.util.List;

public record DashboardResponse(
        long conversationCount,
        long feedbackCount,
        long avgResponseMillis,
        double satisfactionRate,
        List<MetricPoint> weeklyTrend,
        List<NameValue> emotionDistribution,
        List<NameValue> topAttractions,
        List<NameValue> hotQuestions
) {
    public record MetricPoint(String label, long value) {
    }

    public record NameValue(String name, long value) {
    }
}
