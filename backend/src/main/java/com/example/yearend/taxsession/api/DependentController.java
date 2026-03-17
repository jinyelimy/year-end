package com.example.yearend.taxsession.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.taxsession.application.DependentService;
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

@Tag(name = "Dependent")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions/{sessionId}/dependents")
public class DependentController {

    private final DependentService dependentService;

    @Operation(summary = "부양가족 등록")
    @PostMapping
    public ApiResponse<DependentDtos.DependentResponse> create(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @Valid @RequestBody DependentDtos.UpsertDependentRequest request
    ) {
        return ApiResponse.success(dependentService.create(userDetails.getUsername(), sessionId, request));
    }

    @Operation(summary = "부양가족 목록 조회")
    @GetMapping
    public ApiResponse<List<DependentDtos.DependentResponse>> list(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(dependentService.list(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "부양가족 수정")
    @PutMapping("/{dependentId}")
    public ApiResponse<DependentDtos.DependentResponse> update(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID dependentId,
        @Valid @RequestBody DependentDtos.UpsertDependentRequest request
    ) {
        return ApiResponse.success(dependentService.update(userDetails.getUsername(), sessionId, dependentId, request));
    }

    @Operation(summary = "부양가족 삭제")
    @DeleteMapping("/{dependentId}")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId,
        @PathVariable UUID dependentId
    ) {
        dependentService.delete(userDetails.getUsername(), sessionId, dependentId);
        return ApiResponse.success(null);
    }
}
