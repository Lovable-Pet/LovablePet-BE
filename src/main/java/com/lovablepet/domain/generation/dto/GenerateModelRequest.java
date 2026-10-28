package com.lovablepet.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "AR 생성 요청")
public record GenerateModelRequest(
        @Schema(description = "고른 사진: 동물 상세 photoUrls 의 순서 (0부터). 적합도 확인한 사진과 같게 고른다", example = "0")
        @NotNull(message = "사진을 골라 주세요.")
        @Min(value = 0, message = "사진 번호는 0 이상이어야 합니다.")
        Integer photoIndex,

        @Schema(description = "체중(kg). 비우면 공고 체중을 쓴다. 모델의 실제 크기(체고)가 이 값으로 정해진다", example = "20.5")
        @DecimalMin(value = "0", inclusive = false, message = "체중은 0보다 커야 합니다.")
        @DecimalMax(value = "120", message = "체중은 120kg 이하여야 합니다.")
        BigDecimal weightKg,

        @Schema(description = "true 면 템플릿 부적합이어도 끝까지 만든다. 사진 거부(photoRejected)는 force 로도 넘을 수 없다. 비우면 false",
                example = "false")
        Boolean force
) {

    // Jackson 3 은 빠진 값을 원시 타입(boolean)에 넣지 못해 본문 오류(400)가 나므로 래퍼로 받고 기본값을 채운다
    public GenerateModelRequest {
        if (force == null) {
            force = false;
        }
    }
}
