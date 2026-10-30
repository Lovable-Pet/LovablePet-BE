package com.lovablepet.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "적합도 확인 요청")
public record FitCheckRequest(
        @Schema(description = "고른 사진: 동물 상세 photoUrls 의 순서 (0부터)", example = "0")
        @NotNull(message = "사진을 골라 주세요.")
        @Min(value = 0, message = "사진 번호는 0 이상이어야 합니다.")
        Integer photoIndex
) {
}
