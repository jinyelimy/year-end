package com.example.yearend.admin.api;

import com.example.yearend.admin.application.AdminReviewService;
import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.taxsession.domain.SessionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminReviewService adminReviewService;

    @Operation(summary = "검토 대상 세션 목록 조회")
    @GetMapping("/tax-sessions")
    public ApiResponse<List<AdminDtos.AdminSessionResponse>> listSessions(
        @RequestParam(required = false) SessionStatus status
    ) {
        return ApiResponse.success(adminReviewService.listSessions(status));
    }

    @Operation(summary = "검토 대상 체크리스트 조회")
    @GetMapping("/tax-sessions/{sessionId}/checklists")
    public ApiResponse<List<AdminDtos.AdminChecklistResponse>> listChecklists(@PathVariable UUID sessionId) {
        return ApiResponse.success(adminReviewService.listChecklists(sessionId));
    }

    @Operation(summary = "체크리스트 검토 처리")
    @PostMapping("/checklists/{checklistId}/review")
    public ApiResponse<AdminDtos.AdminChecklistResponse> reviewChecklist(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID checklistId,
        @Valid @RequestBody AdminDtos.ReviewChecklistRequest request
    ) {
        return ApiResponse.success(adminReviewService.reviewChecklist(userDetails.getUsername(), checklistId, request));
    }
}
