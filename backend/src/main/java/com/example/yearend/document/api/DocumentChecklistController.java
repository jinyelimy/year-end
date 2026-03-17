package com.example.yearend.document.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.document.application.DocumentChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Document Checklist")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions/{sessionId}/document-checklists")
public class DocumentChecklistController {

    private final DocumentChecklistService documentChecklistService;

    @Operation(summary = "누락 서류 체크 조회")
    @GetMapping
    public ApiResponse<List<DocumentChecklistDtos.ChecklistResponse>> list(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(documentChecklistService.list(userDetails.getUsername(), sessionId));
    }
}
