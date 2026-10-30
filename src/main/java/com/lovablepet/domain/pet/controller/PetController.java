package com.lovablepet.domain.pet.controller;

import com.lovablepet.domain.pet.dto.PetDetailResponse;
import com.lovablepet.domain.pet.dto.PetFilterOptionsResponse;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.dto.PetSearchCondition;
import com.lovablepet.domain.pet.dto.PetSort;
import com.lovablepet.domain.pet.service.PetQueryService;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 유기견 조회 API. GET /api/pets/** 는 SecurityConfig 에서 로그인 없이 허용한다.
 */
@Tag(name = "Pet", description = "유기견 목록 / 상세 (로그인 없이 조회)")
@SecurityRequirements // 토큰 없이 호출하는 API — Swagger 에서 Authorize(자물쇠) 표시를 뺀다
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetQueryService petQueryService;

    @Operation(summary = "입양 가능한 유기견 목록",
            description = "보호 중이고 공고 기간이 끝나 입양할 수 있는 개만 나온다. 공공데이터를 매주 월요일에 동기화한다.\n\n"
                    + "실패: `COMMON_400` 파라미터 범위 오류, `COMMON_400_TYPE` sort 값 오류")
    @GetMapping
    public ApiResponse<PetPageResponse> list(
            @Parameter(description = "관할 지자체 앞부분", example = "경기도")
            @RequestParam(required = false) String region,
            @Parameter(description = "품종 (정확히 일치)", example = "믹스견")
            @RequestParam(required = false) String kind,
            @Parameter(description = "최소 체중(kg)", example = "5")
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minWeight,
            @Parameter(description = "최대 체중(kg)", example = "15")
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxWeight,
            @Parameter(description = "true: AR 모델이 있는 개만, false: 없는 개만 (관리자가 만들 개 찾기). 비우면 전체")
            @RequestParam(required = false) Boolean hasModel,
            @Parameter(description = "NEWEST: 최근 공고순, OLDEST: 오래 기다린 순")
            @RequestParam(defaultValue = "NEWEST") PetSort sort,
            @Parameter(description = "페이지 (0부터)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (최대 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(petQueryService.search(
                new PetSearchCondition(region, kind, minWeight, maxWeight, hasModel, sort), page, size));
    }

    @Operation(summary = "목록 필터 선택지",
            description = "입양 가능한 동물의 시도별·품종별 마릿수. 목록 조회의 region, kind 에 name 을 그대로 넣는다.")
    @GetMapping("/filters")
    public ApiResponse<PetFilterOptionsResponse> filters() {
        return ApiResponse.ok(petQueryService.filterOptions());
    }

    @Operation(summary = "유기견 상세",
            description = "입양·반환 등으로 목록에서 빠진 동물도 조회된다. 지금 입양할 수 있는지는 `adoptable` 로 구분한다.\n\n"
                    + "실패: `PET_404` 없는 동물")
    @GetMapping("/{id}")
    public ApiResponse<PetDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(petQueryService.get(id));
    }
}
