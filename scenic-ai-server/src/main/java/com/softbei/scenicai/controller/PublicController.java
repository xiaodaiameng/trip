package com.softbei.scenicai.controller;

import com.softbei.scenicai.dto.ApiResponse;
import com.softbei.scenicai.dto.ChatRequest;
import com.softbei.scenicai.dto.ChatResponse;
import com.softbei.scenicai.dto.FeedbackRequest;
import com.softbei.scenicai.dto.RoutePlanResponse;
import com.softbei.scenicai.dto.RouteRequest;
import com.softbei.scenicai.dto.TtsRequest;
import com.softbei.scenicai.dto.TtsVoiceResponse;
import com.softbei.scenicai.model.Attraction;
import com.softbei.scenicai.service.AdminService;
import com.softbei.scenicai.service.ChatService;
import com.softbei.scenicai.service.RouteService;
import com.softbei.scenicai.service.TtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final AdminService adminService;
    private final ChatService chatService;
    private final RouteService routeService;
    private final TtsService ttsService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(Map.of(
                "projectName", "景区导览服务 AI 数字人",
                "scenicName", "灵山胜境",
                "welcomeMessage", "你好，我是灵山胜境中文导览助手，可以帮你问答、讲解景点和推荐路线。",
                "mvpScope", List.of("游客问答", "景点讲解", "路线推荐", "后台知识库", "数据看板"),
                "hotQuestions", List.of(
                        "灵山大佛有什么亮点？",
                        "九龙灌浴什么时候看合适？",
                        "适合亲子游客的路线怎么走？"
                )
        ));
    }

    @GetMapping("/attractions")
    public ApiResponse<List<Attraction>> attractions() {
        return ApiResponse.success(adminService.listAttractions());
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.ask(request), "回答已生成");
    }

    @PostMapping("/routes/recommend")
    public ApiResponse<RoutePlanResponse> recommend(@Valid @RequestBody RouteRequest request) {
        return ApiResponse.success(routeService.recommend(request), "路线推荐完成");
    }

    @PostMapping("/feedback")
    public ApiResponse<String> feedback(@RequestBody FeedbackRequest request) {
        chatService.saveFeedback(request);
        return ApiResponse.success("已收到反馈", "感谢你的反馈，我们会继续优化回答");
    }

    @GetMapping("/tts/voices")
    public ApiResponse<List<TtsVoiceResponse>> ttsVoices() {
        return ApiResponse.success(ttsService.listVoices());
    }

    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TtsRequest request) {
        byte[] audio = ttsService.synthesize(request.text(), request.voiceName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("speech.wav").build().toString())
                .body(audio);
    }
}
