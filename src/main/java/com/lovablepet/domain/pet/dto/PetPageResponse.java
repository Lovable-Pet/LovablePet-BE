package com.lovablepet.domain.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "동물 목록 한 페이지")
public record PetPageResponse(
        List<PetSummaryResponse> items,

        @Schema(description = "현재 페이지 (0부터)", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "조건에 맞는 전체 마릿수", example = "1747")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "88")
        int totalPages,

        @Schema(description = "다음 페이지가 있는지", example = "true")
        boolean hasNext
) {

    public static PetPageResponse from(Page<PetSummaryResponse> page) {
        return new PetPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
