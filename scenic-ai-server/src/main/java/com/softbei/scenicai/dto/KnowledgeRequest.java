package com.softbei.scenicai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record KnowledgeRequest(
        @NotBlank(message = "知识标题不能为空") String title,
        @NotBlank(message = "知识分类不能为空") String category,
        List<String> keywords,
        @NotBlank(message = "知识内容不能为空") String content,
        @NotBlank(message = "知识来源不能为空") String source,
        Boolean published
) {
}
