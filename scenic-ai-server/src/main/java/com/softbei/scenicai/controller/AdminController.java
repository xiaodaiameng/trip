package com.softbei.scenicai.controller;

import com.softbei.scenicai.dto.ApiResponse;
import com.softbei.scenicai.dto.KnowledgeRequest;
import com.softbei.scenicai.dto.LoginRequest;
import com.softbei.scenicai.dto.LoginResponse;
import com.softbei.scenicai.model.Attraction;
import com.softbei.scenicai.model.ConversationRecord;
import com.softbei.scenicai.model.KnowledgeEntry;
import com.softbei.scenicai.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(adminService.login(request), "登录成功");
    }

    @GetMapping("/attractions")
    public ApiResponse<List<Attraction>> attractions() {
        return ApiResponse.success(adminService.listAttractions());
    }

    @GetMapping("/knowledge")
    public ApiResponse<List<KnowledgeEntry>> knowledge() {
        return ApiResponse.success(adminService.listKnowledge());
    }

    @PostMapping("/knowledge")
    public ApiResponse<KnowledgeEntry> createKnowledge(@Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.success(adminService.createKnowledge(request), "知识条目已新增");
    }

    @PutMapping("/knowledge/{id}")
    public ApiResponse<KnowledgeEntry> updateKnowledge(@PathVariable Long id, @Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.success(adminService.updateKnowledge(id, request), "知识条目已更新");
    }

    @DeleteMapping("/knowledge/{id}")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        adminService.deleteKnowledge(id);
        return ApiResponse.success(null, "知识条目已删除");
    }

    @GetMapping("/records")
    public ApiResponse<List<ConversationRecord>> records() {
        return ApiResponse.success(adminService.listRecords());
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return ApiResponse.success(adminService.getDashboard());
    }
}
