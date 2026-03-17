package com.example.yearend.taxsession.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.taxsession.application.TaxSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tax Session")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions")
public class TaxSessionController {

    private final TaxSessionService taxSessionService;

    @Operation(summary = "연말정산 세션 생성")
    @PostMapping
    public ApiResponse<TaxSessionDtos.TaxSessionResponse> create(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody TaxSessionDtos.CreateTaxSessionRequest request
    ) {
        return ApiResponse.success(taxSessionService.create(userDetails.getUsername(), request));
    }

    @Operation(summary = "내 세션 목록 조회")
    @GetMapping
    public ApiResponse<List<TaxSessionDtos.TaxSessionResponse>> list(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ApiResponse.success(taxSessionService.list(userDetails.getUsername()));
    }

    @Operation(summary = "세션 상세 조회")
    @GetMapping("/{sessionId}")
    public ApiResponse<TaxSessionDtos.TaxSessionResponse> get(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(taxSessionService.get(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "기본 정보 업데이트")
    @PatchMapping("/{sessionId}/basic-info")
    public ApiResponse<TaxSessionDtos.TaxSessionResponse> updateBasicInfo(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @Valid @RequestBody TaxSessionDtos.UpdateBasicInfoRequest request
    ) {
        return ApiResponse.success(taxSessionService.updateBasicInfo(userDetails.getUsername(), sessionId, request));
    }

    @Operation(summary = "세션 제출")
    @PostMapping("/{sessionId}/submit")
    public ApiResponse<TaxSessionDtos.TaxSessionResponse> submit(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(taxSessionService.submit(userDetails.getUsername(), sessionId));
    }
}
