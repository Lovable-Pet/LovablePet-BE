package com.lovablepet.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미리보기 그림 일괄 생성 접수 결과")
public record PreviewBatchResponse(
        @Schema(description = "찍으려고 줄 세운 완성 모델 수. 한 장에 수 초~십수 초씩 뒤에서 차례로 찍는다", example = "12")
        int queued
) {
}
