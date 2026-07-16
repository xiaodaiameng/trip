package com.softbei.scenicai.service;

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
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AttractionRepository attractionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final ConversationRecordRepository conversationRecordRepository;
    private final FeedbackRecordRepository feedbackRecordRepository;

    @Override
    public void run(String... args) {
        if (attractionRepository.count() == 0) {
            log.info("检测到景点数据为空，开始初始化灵山胜境景点资料");
            attractionRepository.saveAll(List.of(
                    Attraction.builder()
                            .code("LS-001")
                            .name("灵山大照壁")
                            .area("景区入口")
                            .theme("佛教文化门户")
                            .intro("景区入口第一视觉地标，面朝太湖，是游客入园后的第一站。")
                            .highlight("长 39.8 米、高 7 米的青石照壁，正面鎏金题字极具辨识度。")
                            .details("灵山大照壁被誉为“华夏第一壁”，兼具迎宾、文化展示与拍照打卡功能。背面诗刻与太湖景观共同构成灵山胜境的文化序章。")
                            .openHours("08:00-17:00")
                            .suggestedDurationMinutes(20)
                            .popularityScore(84)
                            .walkingIntensity(1)
                            .latitude(31.4219)
                            .longitude(120.1008)
                            .tags(List.of("文化", "拍照", "轻松", "首站"))
                            .features(List.of("入口地标", "诗刻文化", "太湖视野"))
                            .build(),
                    Attraction.builder()
                            .code("LS-002")
                            .name("九龙灌浴")
                            .area("佛足坛广场")
                            .theme("演艺互动")
                            .intro("灵山代表性动态演艺项目，适合首次来访游客。")
                            .highlight("九条金龙环绕太子像喷水灌浴，仪式感强，现场氛围很足。")
                            .details("九龙灌浴以佛诞典故为灵感打造，是灵山胜境最受欢迎的定时演艺之一。建议提前到场，站在广场中心区域观演效果更好。")
                            .openHours("以现场演艺时间为准")
                            .suggestedDurationMinutes(35)
                            .popularityScore(96)
                            .walkingIntensity(2)
                            .latitude(31.4251)
                            .longitude(120.1029)
                            .tags(List.of("演艺", "必看", "亲子", "佛教文化"))
                            .features(List.of("定时演出", "仪式体验", "打卡热点"))
                            .build(),
                    Attraction.builder()
                            .code("LS-003")
                            .name("灵山大佛")
                            .area("核心景观区")
                            .theme("朝圣地标")
                            .intro("灵山胜境核心地标，也是游客最关注的必游景点。")
                            .highlight("大佛巍峨庄严，是灵山的精神象征与远景名片。")
                            .details("灵山大佛承载了景区最强的宗教文化辨识度，周边视野开阔，适合观景、祈福与讲解式导览。对于历史文化兴趣强的游客，建议搭配梵宫一同游览。")
                            .openHours("08:00-17:00")
                            .suggestedDurationMinutes(60)
                            .popularityScore(100)
                            .walkingIntensity(3)
                            .latitude(31.4260)
                            .longitude(120.1038)
                            .tags(List.of("朝圣", "文化", "地标", "深度游"))
                            .features(List.of("核心地标", "祈福体验", "远景观景"))
                            .build(),
                    Attraction.builder()
                            .code("LS-004")
                            .name("梵宫")
                            .area("核心景观区")
                            .theme("建筑艺术")
                            .intro("融合建筑、艺术、佛教文化与数字演艺体验的重要空间。")
                            .highlight("建筑内部金碧辉煌，适合进行沉浸式讲解。")
                            .details("梵宫以恢弘建筑和丰富陈设见长，适合做“深入了解版”的导览讲解。对建筑审美、宗教艺术感兴趣的游客会有更强共鸣。")
                            .openHours("08:30-17:00")
                            .suggestedDurationMinutes(75)
                            .popularityScore(93)
                            .walkingIntensity(2)
                            .latitude(31.4248)
                            .longitude(120.1047)
                            .tags(List.of("建筑", "艺术", "文化", "室内"))
                            .features(List.of("沉浸讲解", "建筑艺术", "避暑避雨"))
                            .build(),
                    Attraction.builder()
                            .code("LS-005")
                            .name("五印坛城")
                            .area("文化体验区")
                            .theme("藏传文化")
                            .intro("适合做差异化文化体验的特色景点。")
                            .highlight("建筑色彩浓郁、文化辨识度高，是拍照与讲解兼备的点位。")
                            .details("五印坛城体现了多元佛教文化展示价值，适合喜欢新鲜体验、拍照取景和民族风建筑的游客。")
                            .openHours("08:30-17:00")
                            .suggestedDurationMinutes(45)
                            .popularityScore(82)
                            .walkingIntensity(2)
                            .latitude(31.4238)
                            .longitude(120.1058)
                            .tags(List.of("建筑", "拍照", "文化", "特色"))
                            .features(List.of("色彩建筑", "差异化文化", "中程停留"))
                            .build(),
                    Attraction.builder()
                            .code("LS-006")
                            .name("拈花湾禅意小镇")
                            .area("延展游览区")
                            .theme("休闲夜游")
                            .intro("偏休闲、慢游和夜景体验，适合轻松路线或情侣路线。")
                            .highlight("氛围感强，夜景、灯光和慢节奏体验是核心卖点。")
                            .details("拈花湾适合安排在半日或一日路线的后半段，尤其适合拍照、放松和体验禅意生活方式。若同行人偏年轻或情侣客群，推荐度会更高。")
                            .openHours("09:00-21:00")
                            .suggestedDurationMinutes(120)
                            .popularityScore(90)
                            .walkingIntensity(2)
                            .latitude(31.4980)
                            .longitude(120.1312)
                            .tags(List.of("夜游", "休闲", "拍照", "情侣"))
                            .features(List.of("夜景氛围", "慢生活", "文创体验"))
                            .build()
            ));
            log.info("景点资料初始化完成，共写入 {} 条", attractionRepository.count());
        }

        syncAttractionCoordinates();

        if (knowledgeEntryRepository.count() == 0) {
            log.info("检测到知识库为空，开始初始化知识条目");
            LocalDateTime now = LocalDateTime.now();
            knowledgeEntryRepository.saveAll(List.of(
                    buildEntry("景区开放时间", "FAQ", List.of("开放", "时间", "几点", "营业"), "灵山胜境常规开放时间建议按 08:00-17:00 参考，具体演艺场次和特殊节假日安排以景区公告为准。", "运营FAQ", now),
                    buildEntry("门票与优惠政策", "FAQ", List.of("门票", "价格", "优惠", "学生", "老人"), "资料包显示成人票约 210 元，半价票约 105 元，6 周岁以下或 1.4 米以下儿童、70 周岁以上老人等群体可按景区政策享受免票或优惠，出行前建议再核对公告。", "游览指南", now),
                    buildEntry("观光车说明", "服务", List.of("观光车", "交通", "代步"), "网购联票可含观光车，单独购买观光车参考价约 40 元/人，适合体力有限或希望提高游览效率的游客。", "游览指南", now),
                    buildEntry("九龙灌浴观演建议", "讲解", List.of("九龙灌浴", "演出", "表演"), "观看九龙灌浴建议提前到场，占据广场中心或视野开阔位置。若带孩子或老人同行，可选择外圈更舒适区域，兼顾安全和视野。", "景点讲解", now),
                    buildEntry("灵山大佛讲解词", "讲解", List.of("大佛", "灵山大佛", "介绍"), "灵山大佛是景区最核心的精神地标，适合从地标意义、文化象征和观景体验三个层面进行讲解。若用户希望更浅显的版本，可以从“为什么它是必打卡点”切入。", "景点讲解", now),
                    buildEntry("梵宫亮点", "讲解", List.of("梵宫", "建筑", "内部"), "梵宫以恢弘建筑与精致陈设著称，适合喜欢建筑艺术和沉浸式讲解的游客，也是下雨天较友好的室内游览点。", "景点讲解", now),
                    buildEntry("亲子路线建议", "路线", List.of("亲子", "带孩子", "儿童"), "亲子游客可以优先安排九龙灌浴、灵山大佛和拈花湾的轻松体验段，控制连续步行时间，并在中途安排休息与餐饮。", "路线规则", now),
                    buildEntry("拍照路线建议", "路线", List.of("拍照", "打卡", "出片"), "喜欢拍照的游客可优先考虑灵山大照壁、五印坛城和拈花湾。前两者适合白天建筑大片，拈花湾更适合傍晚与夜景氛围。", "路线规则", now),
                    buildEntry("文化深度游建议", "路线", List.of("历史", "文化", "深度"), "文化深度游建议重点安排灵山大佛、梵宫、五印坛城，并预留讲解与停留时间，不建议压缩成快节奏路线。", "路线规则", now),
                    buildEntry("餐饮建议", "服务", List.of("吃饭", "餐饮", "素斋"), "景区内可体验素斋自助、素面等偏清淡路线的餐饮。若希望更具禅意氛围，可优先考虑梵宫周边或拈花湾区域的特色餐饮点。", "游览指南", now),
                    buildEntry("最佳游览季节", "FAQ", List.of("季节", "什么时候", "最佳"), "资料建议春秋季来访体验更佳，春季花木景观丰富，秋季气候舒适且适合长时间步行。", "游览指南", now),
                    buildEntry("文明游览提醒", "服务", List.of("注意事项", "文明", "拍照"), "灵山胜境具有较强宗教文化属性，建议保持安静、尊重信仰，不在禁拍区域拍照，不随意触碰佛像与展陈。", "游览指南", now)
            ));
            log.info("知识条目初始化完成，共写入 {} 条", knowledgeEntryRepository.count());
        }

        if (conversationRecordRepository.count() == 0) {
            log.info("检测到会话记录为空，开始初始化演示问答记录");
            LocalDateTime now = LocalDateTime.now();
            conversationRecordRepository.saveAll(List.of(
                    record("session-a", "灵山大佛值得先去吗？", "如果你是第一次来灵山，建议把灵山大佛放进前半程，它是景区的核心地标。", "中性", "灵山大佛", "ATTRACTION", 980L, true, now.minusDays(6)),
                    record("session-b", "九龙灌浴几点看比较好？", "建议提前到场，优先选择视野开阔位置，演艺时间以当天公告为准。", "疑惑", "九龙灌浴观演建议", "KNOWLEDGE", 1250L, true, now.minusDays(5)),
                    record("session-c", "我带孩子，路线怎么走轻松一点？", "亲子路线建议控制连续步行时长，优先安排九龙灌浴、灵山大佛和休闲体验段。", "着急", "亲子路线建议", "KNOWLEDGE", 1420L, true, now.minusDays(4)),
                    record("session-d", "门票有没有学生优惠？", "资料显示学生群体可按政策享受半价或优惠，出行前建议再核对公告。", "疑惑", "门票与优惠政策", "KNOWLEDGE", 860L, null, now.minusDays(3)),
                    record("session-e", "梵宫适合拍照吗？", "适合，尤其是对建筑和空间氛围感兴趣的游客。", "开心", "梵宫", "ATTRACTION", 930L, true, now.minusDays(2)),
                    record("session-f", "下午时间不多，能给我半日路线吗？", "可以优先走入口地标、核心演艺和一处主景点，保证体验完整度。", "着急", "文化深度游建议", "KNOWLEDGE", 1110L, false, now.minusDays(1)),
                    record("session-g", "拈花湾晚上去怎么样？", "很适合慢游和夜景体验，适合安排在路线后半程。", "开心", "拈花湾禅意小镇", "ATTRACTION", 900L, true, now.minusHours(8)),
                    record("session-h", "景区几点开门？", "常规可按 08:00-17:00 参考，节假日以公告为准。", "中性", "景区开放时间", "KNOWLEDGE", 720L, true, now.minusHours(2))
            ));
            log.info("演示问答记录初始化完成，共写入 {} 条", conversationRecordRepository.count());
        }

        if (feedbackRecordRepository.count() == 0) {
            log.info("检测到反馈记录为空，开始初始化演示反馈");
            LocalDateTime now = LocalDateTime.now();
            feedbackRecordRepository.saveAll(List.of(
                    FeedbackRecord.builder().conversationId(1L).helpful(true).comment("回答直接，适合第一次来灵山。").createdAt(now.minusDays(6)).build(),
                    FeedbackRecord.builder().conversationId(2L).helpful(true).comment("能提醒提前占位很实用。").createdAt(now.minusDays(5)).build(),
                    FeedbackRecord.builder().conversationId(6L).helpful(false).comment("希望路线更具体一点。").createdAt(now.minusDays(1)).build(),
                    FeedbackRecord.builder().conversationId(8L).helpful(true).comment("开放时间说得比较清楚。").createdAt(now.minusHours(1)).build()
            ));
            log.info("演示反馈初始化完成，共写入 {} 条", feedbackRecordRepository.count());
        }
    }

    private KnowledgeEntry buildEntry(String title, String category, List<String> keywords, String content, String source, LocalDateTime time) {
        return KnowledgeEntry.builder()
                .title(title)
                .category(category)
                .keywords(keywords)
                .content(content)
                .source(source)
                .published(true)
                .createdAt(time)
                .updatedAt(time)
                .build();
    }

    private ConversationRecord record(String sessionId, String question, String answer, String emotion, String matchedSource,
                                      String sourceType, Long responseMillis, Boolean helpful, LocalDateTime createdAt) {
        return ConversationRecord.builder()
                .sessionId(sessionId)
                .question(question)
                .answer(answer)
                .emotion(emotion)
                .matchedSource(matchedSource)
                .sourceType(sourceType)
                .responseMillis(responseMillis)
                .helpful(helpful)
                .createdAt(createdAt)
                .build();
    }

    private void syncAttractionCoordinates() {
        List<Attraction> attractions = attractionRepository.findAll();
        int updated = 0;

        for (Attraction attraction : attractions) {
            if (attraction.getLatitude() != null && attraction.getLongitude() != null) {
                continue;
            }

            switch (attraction.getCode()) {
                case "LS-001" -> {
                    attraction.setLatitude(31.4219);
                    attraction.setLongitude(120.1008);
                    updated++;
                }
                case "LS-002" -> {
                    attraction.setLatitude(31.4251);
                    attraction.setLongitude(120.1029);
                    updated++;
                }
                case "LS-003" -> {
                    attraction.setLatitude(31.4260);
                    attraction.setLongitude(120.1038);
                    updated++;
                }
                case "LS-004" -> {
                    attraction.setLatitude(31.4248);
                    attraction.setLongitude(120.1047);
                    updated++;
                }
                case "LS-005" -> {
                    attraction.setLatitude(31.4238);
                    attraction.setLongitude(120.1058);
                    updated++;
                }
                case "LS-006" -> {
                    attraction.setLatitude(31.4980);
                    attraction.setLongitude(120.1312);
                    updated++;
                }
                default -> {
                }
            }
        }

        if (updated > 0) {
            attractionRepository.saveAll(attractions);
            log.info("Scenic attraction coordinates synced: {}", updated);
        }
    }
}
