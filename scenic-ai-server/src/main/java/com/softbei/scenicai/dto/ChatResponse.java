package com.softbei.scenicai.dto;

import java.util.List;

public record ChatResponse(
        Long recordId,
        String question,
        String answer,
        String emotion,
        List<SourceCard> sources,
        List<String> suggestions
) {
    public record SourceCard(String title, String excerpt, String source) {
    }
}
