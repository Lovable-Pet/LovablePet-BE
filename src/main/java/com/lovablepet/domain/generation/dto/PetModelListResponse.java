package com.lovablepet.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AR 생성 시도 목록 (최근 100건)")
public record PetModelListResponse(
        List<PetModelResponse> items,

        @Schema(description = "오늘(한국 날짜) 생성 시도 수", example = "7")
        long todayCount,

        @Schema(description = "하루 생성 시도 상한", example = "30")
        int dailyLimit
) {
}
