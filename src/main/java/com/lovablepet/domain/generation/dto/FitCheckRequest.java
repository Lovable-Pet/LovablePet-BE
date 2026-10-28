package com.lovablepet.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "적합도 확인 요청")
public record FitCheckRequest(
        @Schema(description = "고른 사진: 동물 상세 photoUrls 의 순서 (0부터)", example = "0")
        @NotNull(message = "사진을 골라 주세요.")
        @Min(value = 0, message = "사진 번호는 0 이상이어야 합니다.")
        Integer photoIndex,

        @Schema(description = "체중(kg). 비우면 공고 체중을 쓴다. 공고 체중을 숫자로 바꾸지 못한 동물은 꼭 넣어야 한다", example = "20.5")
        @DecimalMin(value = "0", inclusive = false, message = "체중은 0보다 커야 합니다.")
        @DecimalMax(value = "120", message = "체중은 120kg 이하여야 합니다.")
        BigDecimal weightKg
) {
}
