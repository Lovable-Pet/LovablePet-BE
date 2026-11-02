package com.lovablepet.domain.favorite.controller;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.service.FavoriteService;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 찜 API. 모두 로그인이 필요하다 (SecurityConfig 의 anyRequest().authenticated()).
 * 찜 목록을 /api/pets 아래에 두지 않는 것은 GET /api/pets/** 가 로그인 없이 열려 있기 때문이다.
 */
@Tag(name = "Favorite", description = "찜하기 / 찜 취소 / 내 찜 목록 (로그인 필요)")
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "찜하기",
            description = "이미 찜했어도 성공한다 (여러 번 눌러도 하나만 남는다). 입양된 동물도 찜할 수 있다.\n\n"
                    + "실패: `COMMON_401`·`AUTH_401_TOKEN` 로그인 필요, `PET_404` 없는 동물")
    @PutMapping("/api/pets/{petId}/favorite")
    public ApiResponse<FavoriteStatusResponse> add(
            @Parameter(description = "동물 ID", example = "1024") @PathVariable Long petId,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.add(memberId, petId));
    }

    @Operation(summary = "찜 취소",
            description = "찜하지 않았어도 성공한다.\n\n실패: `COMMON_401`·`AUTH_401_TOKEN` 로그인 필요")
    @DeleteMapping("/api/pets/{petId}/favorite")
    public ApiResponse<FavoriteStatusResponse> remove(
            @Parameter(description = "동물 ID", example = "1024") @PathVariable Long petId,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.remove(memberId, petId));
    }
}
