package com.lovablepet.domain.auth.controller;

import com.lovablepet.domain.auth.dto.AuthTokenResponse;
import com.lovablepet.domain.auth.dto.KakaoLoginRequest;
import com.lovablepet.domain.auth.dto.LocalLoginRequest;
import com.lovablepet.domain.auth.dto.LocalSignUpRequest;
import com.lovablepet.domain.auth.dto.RefreshTokenRequest;
import com.lovablepet.domain.auth.service.AuthService;
import com.lovablepet.domain.auth.service.KakaoAuthService;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. /api/auth/** 는 SecurityConfig에서 인증 없이 접근을 허용한다.
 */
@Tag(name = "Auth", description = "회원가입 / 로그인 / 토큰 재발급 / 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KakaoAuthService kakaoAuthService;

    @Operation(summary = "이메일 회원가입", description = "가입 후 바로 액세스/리프레시 토큰을 발급한다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signUp(@Valid @RequestBody LocalSignUpRequest request) {
        AuthTokenResponse tokens = authService.signUpLocal(request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(tokens));
    }

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LocalLoginRequest request) {
        return ApiResponse.ok(authService.loginLocal(request.email(), request.password()));
    }

    @Operation(summary = "카카오 로그인", description = "프론트에서 받은 카카오 인가 코드로 로그인한다. 처음이면 자동 가입된다.")
    @PostMapping("/kakao")
    public ApiResponse<AuthTokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(kakaoAuthService.login(request.authCode()));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스 토큰을 재발급한다. 리프레시 토큰도 새 값으로 교체된다(RTR).")
    @PostMapping("/reissue")
    public ApiResponse<AuthTokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.reissueToken(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "전달한 리프레시 토큰을 폐기한다. (현재 기기만 로그아웃)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok();
    }
}
