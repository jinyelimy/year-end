package com.example.yearend.calculation.api;

import com.example.yearend.calculation.application.SimulationService;
import com.example.yearend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Simulation")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax-sessions/{sessionId}")
public class SimulationController {

    private final SimulationService simulationService;

    @Operation(summary = "연말정산 시뮬레이션 실행")
    @PostMapping("/simulation")
    public ApiResponse<SimulationDtos.SimulationRunResponse> run(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(simulationService.run(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "최신 계산 결과 조회")
    @GetMapping("/results/latest")
    public ApiResponse<SimulationDtos.CalculationResultResponse> getLatestResult(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(simulationService.getLatestResult(userDetails.getUsername(), sessionId));
    }

    @Operation(summary = "미적용 사유 조회")
    @GetMapping("/results/latest/rejections")
    public ApiResponse<List<SimulationDtos.RejectionReasonResponse>> getRejections(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(simulationService.getRejections(userDetails.getUsername(), sessionId));
    }
}
