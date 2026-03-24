package com.example.yearend.deduction.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.deduction.application.DeductionItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Deduction Item")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions/{sessionId}/deduction-items")
public class DeductionItemController {

    private final DeductionItemService deductionItemService;

    @Operation(summary = "지출/공제 항목 등록")
    @PostMapping
    public ApiResponse<DeductionItemDtos.DeductionItemResponse> create(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @Valid @RequestBody DeductionItemDtos.UpsertDeductionItemRequest request
    ) {
        return ApiResponse.success(deductionItemService.create(userDetails.getUsername(), sessionId, request));
    }

    @Operation(summary = "지출/공제 항목 목록 조회")
    @GetMapping
    public ApiResponse<List<DeductionItemDtos.DeductionItemResponse>> list(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(deductionItemService.list(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "지출/공제 항목 수정")
    @PutMapping("/{deductionItemId}")
    public ApiResponse<DeductionItemDtos.DeductionItemResponse> update(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID deductionItemId,
        @Valid @RequestBody DeductionItemDtos.UpsertDeductionItemRequest request
    ) {
        return ApiResponse.success(
            deductionItemService.update(userDetails.getUsername(), sessionId, deductionItemId, request)
        );
    }

    @Operation(summary = "지출/공제 항목 삭제")
    @DeleteMapping("/{deductionItemId}")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID deductionItemId
    ) {
        deductionItemService.delete(userDetails.getUsername(), sessionId, deductionItemId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "홈택스 간소화 PDF 1차 가져오기")
    @PostMapping("/imports/hometax")
    public ApiResponse<DeductionItemDtos.HometaxImportResponse> importHometax(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(deductionItemService.importHometax(userDetails.getUsername(), sessionId, file));
    }
}
