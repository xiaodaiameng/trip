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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping({"/api/public", "/api/v1/public"})
@RequiredArgsConstructor
public class PublicController {

    private final AdminService adminService;
    private final ChatService chatService;
    private final RouteService routeService;
    private final TtsService ttsService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(Map.of(
                "projectName", "灵境智游",
                "scenicName", "灵山胜境",
                "welcomeMessage", "你好，我是灵山胜境导览助手，可以为你讲解景点、推荐路线和解答游览问题。",
                "mvpScope", List.of("景点讲解", "路线推荐", "游客咨询", "知识维护", "运营看板"),
                "hotQuestions", List.of(
                        "灵山大佛有哪些必看的亮点？",
                        "九龙灌浴演出什么时候观看比较合适？",
                        "亲子游客怎么安排游览路线更轻松？"
                )
        ));
    }

    @GetMapping("/attractions")
    public ApiResponse<Page<Attraction>> attractions(@PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.success(adminService.listAttractions(pageable));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.ask(request), "Answer generated");
    }

    @PostMapping("/routes/recommend")
    public ApiResponse<RoutePlanResponse> recommend(@Valid @RequestBody RouteRequest request) {
        return ApiResponse.success(routeService.recommend(request), "Route generated");
    }

    @PostMapping("/feedback")
    public ApiResponse<String> feedback(@Valid @RequestBody FeedbackRequest request) {
        chatService.saveFeedback(request);
        return ApiResponse.success("Feedback received", "Thank you for the feedback");
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
