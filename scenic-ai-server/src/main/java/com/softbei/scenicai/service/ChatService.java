package com.softbei.scenicai.service;

import com.softbei.scenicai.dto.ChatRequest;
import com.softbei.scenicai.dto.ChatResponse;
import com.softbei.scenicai.dto.FeedbackRequest;
import com.softbei.scenicai.model.Attraction;
import com.softbei.scenicai.model.ConversationRecord;
import com.softbei.scenicai.model.FeedbackRecord;
import com.softbei.scenicai.model.KnowledgeEntry;
import com.softbei.scenicai.repository.AttractionRepository;
import com.softbei.scenicai.repository.ConversationRecordRepository;
import com.softbei.scenicai.repository.FeedbackRecordRepository;
import com.softbei.scenicai.repository.KnowledgeEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final AttractionRepository attractionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final ConversationRecordRepository conversationRecordRepository;
    private final FeedbackRecordRepository feedbackRecordRepository;

    @Transactional
    public ChatResponse ask(ChatRequest request) {
        long start = System.currentTimeMillis();
        String question = request.question().trim();
        String matchingText = expandQuestionForMatching(question);
        String emotion = detectEmotion(question);
        log.info("收到游客提问：{}，识别情绪：{}", question, emotion);

        Attraction attraction = findBestAttraction(matchingText);
        KnowledgeEntry knowledge = findBestKnowledge(matchingText);

        String answer;
        List<ChatResponse.SourceCard> sources = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String matchedSource = "未命中";
        String sourceType = "FALLBACK";

        int attractionScore = attraction == null ? 0 : scoreAttraction(matchingText, attraction);
        int knowledgeScore = knowledge == null ? 0 : scoreKnowledge(matchingText, knowledge);
        boolean preferKnowledge = shouldPreferKnowledge(question);

        if (!preferKnowledge && attractionScore >= knowledgeScore && attraction != null && attractionScore > 0) {
            answer = buildAttractionAnswer(attraction, emotion);
            sources.add(new ChatResponse.SourceCard(attraction.getName(), attraction.getHighlight(), "景点资料"));
            suggestions = List.of(
                    "这个景点适合安排多久？",
                    "附近还有哪些值得顺路看的点？",
                    "帮我按文化体验推荐一条路线"
            );
            matchedSource = attraction.getName();
            sourceType = "ATTRACTION";
        } else if (knowledge != null && knowledgeScore > 0) {
            answer = buildKnowledgeAnswer(knowledge, emotion);
            sources.add(new ChatResponse.SourceCard(knowledge.getTitle(), trim(knowledge.getContent(), 72), knowledge.getSource()));
            suggestions = switch (knowledge.getCategory()) {
                case "FAQ" -> List.of("还有门票或开放时间方面的问题吗？", "需要我顺便推荐路线吗？", "想看热门打卡点吗？");
                case "路线" -> List.of("你更偏好拍照、亲子还是文化？", "游览时长准备多久？", "需要轻松路线还是深度路线？");
                default -> List.of("要不要我换成更简洁的讲解版？", "需要我继续讲这个景点吗？", "也可以直接帮你规划路线");
            };
            matchedSource = knowledge.getTitle();
            sourceType = "KNOWLEDGE";
        } else {
            answer = "我暂时没有从本地知识库里查到足够准确的依据。你可以换个问法，比如直接问景点名称、开放时间、门票、路线或服务设施。我会继续优先依据灵山胜境资料给出更稳妥的导览建议。";
            suggestions = List.of(
                    "灵山大佛有什么亮点？",
                    "景区开放时间和门票怎么安排？",
                    "帮我推荐半日游路线"
            );
        }

        long responseMillis = System.currentTimeMillis() - start;
        ConversationRecord saved = conversationRecordRepository.save(ConversationRecord.builder()
                .sessionId(Optional.ofNullable(request.sessionId()).filter(v -> !v.isBlank()).orElse("guest-session"))
                .question(question)
                .answer(answer)
                .emotion(emotion)
                .matchedSource(matchedSource)
                .sourceType(sourceType)
                .responseMillis(responseMillis)
                .build());

        log.info("问答完成，来源类型：{}，命中来源：{}，耗时：{}ms", sourceType, matchedSource, responseMillis);

        return new ChatResponse(saved.getId(), question, answer, emotion, sources, suggestions);
    }

    @Transactional
    public void saveFeedback(FeedbackRequest request) {
        FeedbackRecord feedback = FeedbackRecord.builder()
                .conversationId(request.recordId())
                .helpful(Boolean.TRUE.equals(request.helpful()))
                .comment(Optional.ofNullable(request.comment()).orElse(""))
                .build();
        feedbackRecordRepository.save(feedback);
        log.info("收到游客反馈，记录ID：{}，是否有帮助：{}", request.recordId(), request.helpful());

        if (request.recordId() != null) {
            conversationRecordRepository.findById(request.recordId()).ifPresent(record -> {
                record.setHelpful(Boolean.TRUE.equals(request.helpful()));
                conversationRecordRepository.save(record);
            });
        }
    }

    private String buildAttractionAnswer(Attraction attraction, String emotion) {
        String tone = switch (emotion) {
            case "着急" -> "我先说重点：";
            case "疑惑" -> "可以这样理解：";
            case "开心" -> "这个点确实很值得看，";
            default -> "";
        };
        return tone + attraction.getName() + "位于" + attraction.getArea() + "，" + attraction.getIntro()
                + " 它的核心亮点是" + attraction.getHighlight()
                + " 常规建议停留约 " + attraction.getSuggestedDurationMinutes() + " 分钟，开放参考时间为 " + attraction.getOpenHours() + "。";
    }

    private String buildKnowledgeAnswer(KnowledgeEntry knowledge) {
        return buildKnowledgeAnswer(knowledge, "中性");
    }

    private String buildKnowledgeAnswer(KnowledgeEntry knowledge, String emotion) {
        String prefix = switch (emotion) {
            case "着急" -> "给你直接结论：";
            case "疑惑" -> "我根据资料说明一下：";
            default -> "";
        };
        return prefix + knowledge.getContent();
    }

    private Attraction findBestAttraction(String question) {
        return attractionRepository.searchCandidates(question, PageRequest.of(0, 50)).stream()
                .max(Comparator.comparingInt(item -> scoreAttraction(question, item)))
                .orElse(null);
    }

    private KnowledgeEntry findBestKnowledge(String question) {
        return knowledgeEntryRepository.searchPublishedCandidates(question, PageRequest.of(0, 50)).stream()
                .max(Comparator.comparingInt(item -> scoreKnowledge(question, item)))
                .orElse(null);
    }

    private int scoreAttraction(String question, Attraction attraction) {
        String lower = question.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains(attraction.getName().toLowerCase(Locale.ROOT))) {
            score += 60;
        }
        if (lower.contains(attraction.getTheme().toLowerCase(Locale.ROOT))) {
            score += 15;
        }
        for (String tag : attraction.getTags()) {
            if (lower.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 10;
            }
        }
        for (String feature : attraction.getFeatures()) {
            if (lower.contains(feature.toLowerCase(Locale.ROOT))) {
                score += 8;
            }
        }
        return score;
    }

    private int scoreKnowledge(String question, KnowledgeEntry knowledge) {
        String lower = question.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains(knowledge.getTitle().toLowerCase(Locale.ROOT))) {
            score += 45;
        }
        for (String keyword : knowledge.getKeywords()) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 18;
            }
        }
        if (lower.contains(knowledge.getCategory().toLowerCase(Locale.ROOT))) {
            score += 8;
        }
        return score;
    }

    private String detectEmotion(String question) {
        if (containsAny(question, "赶时间", "来不及", "快点", "马上", "着急")) {
            return "着急";
        }
        if (containsAny(question, "怎么", "哪里", "为啥", "为什么", "吗", "？", "?")) {
            return "疑惑";
        }
        if (containsAny(question, "喜欢", "太好了", "不错", "开心")) {
            return "开心";
        }
        if (containsAny(question, "失望", "不好", "麻烦")) {
            return "不满";
        }
        return "中性";
    }

    private String expandQuestionForMatching(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        StringBuilder expanded = new StringBuilder(question);

        if (lower.contains("nine dragons") || lower.contains("sakyamuni") || lower.contains("bathing")) {
            expanded.append(" 九龙灌浴 演出 表演 观看 观演 建议 几点 时间");
        }
        if (lower.contains("lingshan grand buddha") || lower.contains("grand buddha")) {
            expanded.append(" 灵山大佛 大佛 亮点 介绍");
        }
        if (lower.contains("family") || lower.contains("children") || lower.contains("kids") || lower.contains("parent")) {
            expanded.append(" 亲子 带孩子 儿童 路线 轻松");
        }
        if (lower.contains("route") || lower.contains("arranged") || lower.contains("arrange")) {
            expanded.append(" 路线 推荐 游览 安排");
        }
        if (lower.contains("best time") || lower.contains("when")) {
            expanded.append(" 什么时候 几点 时间 观看 建议");
        }
        if (lower.contains("highlight") || lower.contains("highlights")) {
            expanded.append(" 亮点 必看 介绍");
        }

        return expanded.toString();
    }

    private boolean shouldPreferKnowledge(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        return lower.contains("when")
                || lower.contains("best time")
                || lower.contains("show")
                || lower.contains("route")
                || lower.contains("family")
                || lower.contains("什么时候")
                || lower.contains("几点")
                || lower.contains("观演")
                || lower.contains("演出")
                || lower.contains("路线")
                || lower.contains("亲子");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
