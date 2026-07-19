package com.softbei.scenicai.service;

import com.softbei.scenicai.dto.RoutePlanResponse;
import com.softbei.scenicai.dto.RouteRequest;
import com.softbei.scenicai.model.Attraction;
import com.softbei.scenicai.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RouteService {

    private final AttractionRepository attractionRepository;

    public RoutePlanResponse recommend(RouteRequest request) {
        List<String> interests = request.interests() == null ? List.of() : request.interests();
        int durationHours = Math.max(2, request.durationHours());
        String pace = request.pace() == null ? "适中" : request.pace();
        String companionType = request.companionType() == null ? "朋友" : request.companionType();
        log.info("开始生成路线推荐：兴趣={}，时长={}小时，节奏={}，同行类型={}", interests, durationHours, pace, companionType);

        int stopCount = durationHours <= 4 ? 3 : durationHours <= 6 ? 4 : 5;

        List<Attraction> selected = attractionRepository.findAll().stream()
                .sorted(Comparator.comparingInt((Attraction item) -> score(item, interests, pace, companionType)).reversed())
                .limit(stopCount)
                .collect(Collectors.toList());

        List<RoutePlanResponse.RouteStop> stops = selected.stream()
                .map(item -> new RoutePlanResponse.RouteStop(
                        selected.indexOf(item) + 1,
                        item.getName(),
                        item.getHighlight(),
                        formatDuration(item.getSuggestedDurationMinutes()),
                        buildReason(item, interests, companionType)))
                .toList();

        String title = buildTitle(interests, durationHours, companionType);
        List<String> tips = List.of(
                "如果想减少步行压力，可以把观光车加入路线。",
                "核心演艺项目建议提前查看当天场次，避免错过。",
                "当前先按标签和热度推荐，后续可以接入拥堵感知与实时排队数据。"
        );

        RoutePlanResponse response = new RoutePlanResponse(title, durationHours, stops, tips);
        log.info("路线推荐完成：{}，共推荐 {} 个点位", title, stops.size());
        return response;
    }

    private int score(Attraction attraction, List<String> interests, String pace, String companionType) {
        int score = attraction.getPopularityScore();
        for (String interest : interests) {
            String lower = interest.toLowerCase(Locale.ROOT);
            score += attraction.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(lower)) ? 26 : 0;
            score += attraction.getTheme().toLowerCase(Locale.ROOT).contains(lower) ? 20 : 0;
        }
        if ("轻松".equals(pace)) {
            score -= attraction.getWalkingIntensity() * 8;
        }
        if ("亲子".equals(companionType) && attraction.getTags().contains("亲子")) {
            score += 20;
        }
        if ("情侣".equals(companionType) && (attraction.getTags().contains("拍照") || attraction.getTags().contains("夜游"))) {
            score += 18;
        }
        if ("长辈".equals(companionType) && attraction.getWalkingIntensity() <= 2) {
            score += 15;
        }
        return score;
    }

    private String buildReason(Attraction attraction, List<String> interests, String companionType) {
        if (interests.stream().anyMatch(interest -> attraction.getTags().contains(interest))) {
            return "和你的兴趣标签高度匹配，适合作为本次路线重点。";
        }
        if ("亲子".equals(companionType) && attraction.getTags().contains("亲子")) {
            return "更适合亲子同行，节奏友好，体验轻松。";
        }
        if ("情侣".equals(companionType) && attraction.getTags().stream().anyMatch(tag -> List.of("拍照", "夜游").contains(tag))) {
            return "氛围感和出片率都不错，适合情侣路线。";
        }
        return "综合热度、停留时长和路线完整度后，适合作为本次游览的推荐点位。";
    }

    private String buildTitle(List<String> interests, int durationHours, String companionType) {
        if (interests.contains("拍照")) {
            return "灵山" + durationHours + "小时高出片打卡路线";
        }
        if (interests.contains("文化")) {
            return "灵山" + durationHours + "小时文化讲解路线";
        }
        return "面向" + companionType + "的灵山" + durationHours + "小时精选路线";
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null) {
            return "时长待定";
        }
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int hour = minutes / 60;
        int remain = minutes % 60;
        return remain == 0 ? hour + " 小时" : hour + " 小时 " + remain + " 分钟";
    }
}
