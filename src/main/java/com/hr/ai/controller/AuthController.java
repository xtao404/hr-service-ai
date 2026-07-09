package com.hr.ai.controller;

import com.hr.ai.dto.*;
import com.hr.ai.security.UserPrincipal;
import com.hr.ai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.getCurrentUser(principal));
    }
}
