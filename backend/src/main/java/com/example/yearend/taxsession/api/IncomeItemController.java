package com.example.yearend.taxsession.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.taxsession.application.IncomeItemService;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Income Item")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions/{sessionId}/income-items")
public class IncomeItemController {

    private final IncomeItemService incomeItemService;

    @Operation(summary = "소득 항목 등록")
    @PostMapping
    public ApiResponse<IncomeItemDtos.IncomeItemResponse> create(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @Valid @RequestBody IncomeItemDtos.UpsertIncomeItemRequest request
    ) {
        return ApiResponse.success(incomeItemService.create(userDetails.getUsername(), sessionId, request));
    }

    @Operation(summary = "소득 항목 목록 조회")
    @GetMapping
    public ApiResponse<List<IncomeItemDtos.IncomeItemResponse>> list(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(incomeItemService.list(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "소득 항목 수정")
    @PutMapping("/{incomeItemId}")
    public ApiResponse<IncomeItemDtos.IncomeItemResponse> update(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID incomeItemId,
        @Valid @RequestBody IncomeItemDtos.UpsertIncomeItemRequest request
    ) {
        return ApiResponse.success(incomeItemService.update(userDetails.getUsername(), sessionId, incomeItemId, request));
    }

    @Operation(summary = "소득 항목 삭제")
    @DeleteMapping("/{incomeItemId}")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID incomeItemId
    ) {
        incomeItemService.delete(userDetails.getUsername(), sessionId, incomeItemId);
        return ApiResponse.success(null);
    }
}
