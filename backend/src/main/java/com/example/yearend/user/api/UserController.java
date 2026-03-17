package com.example.yearend.user.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping
    public ApiResponse<UserDtos.UserProfileResponse> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(userService.getMe(userDetails.getUsername()));
    }

    @Operation(summary = "내 정보 수정")
    @PatchMapping
    public ApiResponse<UserDtos.UserProfileResponse> updateMe(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody UserDtos.UpdateMeRequest request
    ) {
        return ApiResponse.success(userService.updateMe(userDetails.getUsername(), request));
    }
}
