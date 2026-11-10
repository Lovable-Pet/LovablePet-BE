package com.lovablepet.domain.favorite.controller;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.service.FavoriteService;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 찜 목록 API. 찜은 회원마다 다른 "내 것"이라 찜하기·취소·목록을 모두 /api/members/me/favorites 아래에 둔다.
 * 모두 로그인이 필요하다 (SecurityConfig 의 anyRequest().authenticated()).
 */
@Tag(name = "Favorite", description = "찜하기 / 찜 취소 / 내 찜 목록 (로그인 필요)")
@RestController
@RequestMapping("/api/members/me/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "찜하기",
            description = "이미 찜했어도 성공한다 (여러 번 눌러도 하나만 남는다). 입양된 동물도 찜할 수 있다.\n\n"
                    + "실패: `COMMON_401`·`AUTH_401_TOKEN` 로그인 필요, `PET_404` 없는 동물")
    @PutMapping("/{petId}")
    public ApiResponse<FavoriteStatusResponse> add(
            @Parameter(description = "동물 ID", example = "1024") @PathVariable Long petId,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.add(memberId, petId));
    }

    @Operation(summary = "찜 취소",
            description = "찜하지 않았어도 성공한다.\n\n실패: `COMMON_401`·`AUTH_401_TOKEN` 로그인 필요")
    @DeleteMapping("/{petId}")
    public ApiResponse<FavoriteStatusResponse> remove(
            @Parameter(description = "동물 ID", example = "1024") @PathVariable Long petId,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.remove(memberId, petId));
    }

    @Operation(summary = "내 찜 목록",
            description = "최근에 찜한 순. 입양·반환으로 입양 가능 목록에서 빠진 동물도 나오고, 그런 동물은 `adoptable` 이 false 다 "
                    + "(화면에서 '보호 종료' 로 표시).\n\n"
                    + "실패: `COMMON_401`·`AUTH_401_TOKEN` 로그인 필요, `COMMON_400` 파라미터 범위 오류")
    @GetMapping
    public ApiResponse<PetPageResponse> list(
            @Parameter(description = "페이지 (0부터)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (최대 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(favoriteService.list(memberId, page, size));
    }
}
