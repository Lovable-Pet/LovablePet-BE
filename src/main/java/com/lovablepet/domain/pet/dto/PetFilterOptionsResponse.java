package com.lovablepet.domain.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "목록 필터 선택지. 입양 가능한 동물 기준 마릿수가 많은 순")
public record PetFilterOptionsResponse(
        @Schema(description = "시도별 마릿수. 목록 조회의 region 에 name 을 넣는다")
        List<Option> regions,

        @Schema(description = "품종별 마릿수. 목록 조회의 kind 에 name 을 넣는다")
        List<Option> kinds
) {

    public record Option(
            @Schema(example = "경기도")
            String name,

            @Schema(example = "412")
            long count
    ) {
    }
}
