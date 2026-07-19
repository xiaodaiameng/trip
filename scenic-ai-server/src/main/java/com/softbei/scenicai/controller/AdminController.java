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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(adminService.login(request), "Login succeeded");
    }

    @GetMapping("/attractions")
    public ApiResponse<Page<Attraction>> attractions(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.listAttractions(pageable));
    }

    @GetMapping("/knowledge")
    public ApiResponse<Page<KnowledgeEntry>> knowledge(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.listKnowledge(pageable));
    }

    @PostMapping("/knowledge")
    public ApiResponse<KnowledgeEntry> createKnowledge(@Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.success(adminService.createKnowledge(request), "Knowledge entry created");
    }

    @PutMapping("/knowledge/{id}")
    public ApiResponse<KnowledgeEntry> updateKnowledge(@PathVariable Long id, @Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.success(adminService.updateKnowledge(id, request), "Knowledge entry updated");
    }

    @DeleteMapping("/knowledge/{id}")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        adminService.deleteKnowledge(id);
        return ApiResponse.success(null, "Knowledge entry deleted");
    }

    @GetMapping("/records")
    public ApiResponse<Page<ConversationRecord>> records(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.listRecords(pageable));
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return ApiResponse.success(adminService.getDashboard());
    }
}
