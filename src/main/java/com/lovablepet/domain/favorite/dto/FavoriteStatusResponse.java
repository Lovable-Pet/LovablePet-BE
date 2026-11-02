package com.lovablepet.domain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "찜하기·찜 취소 결과")
public record FavoriteStatusResponse(
        @Schema(description = "동물 ID", example = "1024")
        Long petId,

        @Schema(description = "요청 후 찜 상태 (찜하기면 true, 취소면 false)", example = "true")
        boolean favorited
) {
}
