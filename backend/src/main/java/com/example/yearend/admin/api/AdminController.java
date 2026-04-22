package com.example.yearend.admin.api;

import com.example.yearend.admin.application.AdminReviewService;
import com.example.yearend.admin.application.AdminRuleSetService;
import com.example.yearend.admin.application.LawPackImportService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminReviewService adminReviewService;
    private final AdminRuleSetService adminRuleSetService;
    private final LawPackImportService lawPackImportService;

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

    @Operation(summary = "猷곗뀑 ?щ씪 寃??寃곗젙 湲곕줉")
    @PostMapping("/rule-sets/{ruleSetId}/review")
    public ApiResponse<AdminDtos.AdminRuleSetResponse> reviewRuleSet(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID ruleSetId,
        @Valid @RequestBody AdminDtos.ReviewRuleSetRequest request
    ) {
        return ApiResponse.success(adminRuleSetService.reviewRuleSet(userDetails.getUsername(), ruleSetId, request));
    }

    @Operation(summary = "READY_FOR_REVIEW 猷곗뀑 PUBLISHED 寃뚯떆")
    @PostMapping("/rule-sets/{ruleSetId}/publish")
    public ApiResponse<AdminDtos.AdminRuleSetResponse> publishRuleSet(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID ruleSetId
    ) {
        return ApiResponse.success(adminRuleSetService.publishRuleSet(userDetails.getUsername(), ruleSetId));
    }

    @Operation(summary = "normalized-rule-pack.json을 DRAFT 룰셋으로 임포트")
    @PostMapping(value = "/rule-sets/import", consumes = "multipart/form-data")
    public ApiResponse<AdminDtos.ImportRuleSetResponse> importLawPack(
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(lawPackImportService.importLawPack(file));
    }
}
