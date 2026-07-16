package com.softbei.scenicai.service;

import com.softbei.scenicai.dto.DashboardResponse;
import com.softbei.scenicai.dto.KnowledgeRequest;
import com.softbei.scenicai.dto.LoginRequest;
import com.softbei.scenicai.dto.LoginResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.display-name}")
    private String adminDisplayName;

    private final AttractionRepository attractionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final ConversationRecordRepository conversationRecordRepository;
    private final FeedbackRecordRepository feedbackRecordRepository;

    public LoginResponse login(LoginRequest request) {
        if (!adminUsername.equals(request.username()) || !adminPassword.equals(request.password())) {
            log.warn("后台登录失败，账号：{}", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        log.info("后台登录成功，账号：{}", request.username());
        return new LoginResponse("demo-admin-token", adminDisplayName, List.of("ADMIN"));
    }

    public List<Attraction> listAttractions() {
        return attractionRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Attraction::getPopularityScore).reversed())
                .toList();
    }

    public List<KnowledgeEntry> listKnowledge() {
        return knowledgeEntryRepository.findAll().stream()
                .sorted(Comparator.comparing(KnowledgeEntry::getUpdatedAt).reversed())
                .toList();
    }

    public KnowledgeEntry createKnowledge(KnowledgeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeEntry entry = KnowledgeEntry.builder()
                .title(request.title())
                .category(request.category())
                .keywords(request.keywords() == null ? List.of() : request.keywords())
                .content(request.content())
                .source(request.source())
                .published(request.published() == null || request.published())
                .createdAt(now)
                .updatedAt(now)
                .build();
        KnowledgeEntry saved = knowledgeEntryRepository.save(entry);
        log.info("已新增知识条目：{}（分类：{}）", saved.getTitle(), saved.getCategory());
        return saved;
    }

    public KnowledgeEntry updateKnowledge(Long id, KnowledgeRequest request) {
        KnowledgeEntry entry = knowledgeEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识条目不存在"));
        entry.setTitle(request.title());
        entry.setCategory(request.category());
        entry.setKeywords(request.keywords() == null ? List.of() : request.keywords());
        entry.setContent(request.content());
        entry.setSource(request.source());
        entry.setPublished(request.published() == null || request.published());
        entry.setUpdatedAt(LocalDateTime.now());
        KnowledgeEntry saved = knowledgeEntryRepository.save(entry);
        log.info("已更新知识条目：{}（ID：{}）", saved.getTitle(), saved.getId());
        return saved;
    }

    public void deleteKnowledge(Long id) {
        KnowledgeEntry entry = knowledgeEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识条目不存在"));
        knowledgeEntryRepository.delete(entry);
        log.info("已删除知识条目：{}（ID：{}）", entry.getTitle(), entry.getId());
    }

    public List<ConversationRecord> listRecords() {
        return conversationRecordRepository.findAll().stream()
                .sorted(Comparator.comparing(ConversationRecord::getCreatedAt).reversed())
                .toList();
    }

    public DashboardResponse getDashboard() {
        List<ConversationRecord> records = conversationRecordRepository.findAll();
        List<FeedbackRecord> feedbacks = feedbackRecordRepository.findAll();

        long conversationCount = records.size();
        long feedbackCount = feedbacks.size();
        long avgResponseMillis = records.isEmpty() ? 0L : Math.round(records.stream()
                .mapToLong(record -> record.getResponseMillis() == null ? 0L : record.getResponseMillis())
                .average()
                .orElse(0));
        double satisfactionRate = feedbacks.isEmpty() ? 0D :
                feedbacks.stream().filter(FeedbackRecord::getHelpful).count() * 100.0 / feedbackCount;

        List<DashboardResponse.MetricPoint> weeklyTrend = buildWeeklyTrend(records);
        List<DashboardResponse.NameValue> emotionDistribution = buildEmotionDistribution(records);
        List<DashboardResponse.NameValue> topAttractions = buildTopAttractions(records);
        List<DashboardResponse.NameValue> hotQuestions = buildHotQuestions(records);

        return new DashboardResponse(
                conversationCount,
                feedbackCount,
                avgResponseMillis,
                Math.round(satisfactionRate * 10.0) / 10.0,
                weeklyTrend,
                emotionDistribution,
                topAttractions,
                hotQuestions
        );
    }

    private List<DashboardResponse.MetricPoint> buildWeeklyTrend(List<ConversationRecord> records) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            counts.put(LocalDate.now().minusDays(i), 0L);
        }
        records.forEach(record -> {
            LocalDate date = record.getCreatedAt().toLocalDate();
            if (counts.containsKey(date)) {
                counts.put(date, counts.get(date) + 1);
            }
        });
        return counts.entrySet().stream()
                .map(entry -> new DashboardResponse.MetricPoint(entry.getKey().getMonthValue() + "/" + entry.getKey().getDayOfMonth(), entry.getValue()))
                .toList();
    }

    private List<DashboardResponse.NameValue> buildEmotionDistribution(List<ConversationRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(ConversationRecord::getEmotion, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DashboardResponse.NameValue(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardResponse.NameValue> buildTopAttractions(List<ConversationRecord> records) {
        List<DashboardResponse.NameValue> ranked = records.stream()
                .filter(record -> "ATTRACTION".equals(record.getSourceType()))
                .collect(Collectors.groupingBy(ConversationRecord::getMatchedSource, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DashboardResponse.NameValue(entry.getKey(), entry.getValue()))
                .toList();
        if (!ranked.isEmpty()) {
            return ranked;
        }
        return attractionRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Attraction::getPopularityScore).reversed())
                .limit(5)
                .map(item -> new DashboardResponse.NameValue(item.getName(), item.getPopularityScore().longValue()))
                .toList();
    }

    private List<DashboardResponse.NameValue> buildHotQuestions(List<ConversationRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(ConversationRecord::getQuestion, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new DashboardResponse.NameValue(entry.getKey(), entry.getValue()))
                .toList();
    }
}
