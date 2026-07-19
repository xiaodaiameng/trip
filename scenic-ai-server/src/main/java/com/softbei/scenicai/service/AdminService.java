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
import com.softbei.scenicai.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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

    @Value("${app.admin.password-hash}")
    private String adminPasswordHash;

    @Value("${app.admin.display-name}")
    private String adminDisplayName;

    private final AttractionRepository attractionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final ConversationRecordRepository conversationRecordRepository;
    private final FeedbackRecordRepository feedbackRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (adminPasswordHash == null || adminPasswordHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin password hash is not configured");
        }
        if (!adminUsername.equals(request.username()) || !passwordEncoder.matches(request.password(), adminPasswordHash)) {
            log.warn("Admin login failed for username={}", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        log.info("Admin login succeeded for username={}", request.username());
        return new LoginResponse(jwtService.issueAdminToken(request.username(), List.of("ADMIN")), adminDisplayName, List.of("ADMIN"));
    }

    @Transactional(readOnly = true)
    public List<Attraction> listAttractions() {
        return attractionRepository.findAllByOrderByPopularityScoreDesc();
    }

    @Transactional(readOnly = true)
    public Page<Attraction> listAttractions(Pageable pageable) {
        return attractionRepository.findAllByOrderByPopularityScoreDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntry> listKnowledge() {
        return knowledgeEntryRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeEntry> listKnowledge(Pageable pageable) {
        return knowledgeEntryRepository.findAllByOrderByUpdatedAtDesc(pageable);
    }

    @Transactional
    public KnowledgeEntry createKnowledge(KnowledgeRequest request) {
        KnowledgeEntry entry = KnowledgeEntry.builder()
                .title(request.title())
                .category(request.category())
                .keywords(request.keywords() == null ? List.of() : request.keywords())
                .content(request.content())
                .source(request.source())
                .published(request.published() == null || request.published())
                .build();
        KnowledgeEntry saved = knowledgeEntryRepository.save(entry);
        log.info("Knowledge entry created, id={}, title={}", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public KnowledgeEntry updateKnowledge(Long id, KnowledgeRequest request) {
        KnowledgeEntry entry = knowledgeEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge entry not found"));
        entry.setTitle(request.title());
        entry.setCategory(request.category());
        entry.setKeywords(request.keywords() == null ? List.of() : request.keywords());
        entry.setContent(request.content());
        entry.setSource(request.source());
        entry.setPublished(request.published() == null || request.published());
        KnowledgeEntry saved = knowledgeEntryRepository.save(entry);
        log.info("Knowledge entry updated, id={}, title={}", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteKnowledge(Long id) {
        KnowledgeEntry entry = knowledgeEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge entry not found"));
        knowledgeEntryRepository.delete(entry);
        log.info("Knowledge entry deleted, id={}, title={}", entry.getId(), entry.getTitle());
    }

    @Transactional(readOnly = true)
    public List<ConversationRecord> listRecords() {
        return conversationRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<ConversationRecord> listRecords(Pageable pageable) {
        return conversationRecordRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
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

        return new DashboardResponse(
                conversationCount,
                feedbackCount,
                avgResponseMillis,
                Math.round(satisfactionRate * 10.0) / 10.0,
                buildWeeklyTrend(records),
                buildEmotionDistribution(records),
                buildTopAttractions(records),
                buildHotQuestions(records)
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
        return attractionRepository.findTop5ByOrderByPopularityScoreDesc().stream()
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
