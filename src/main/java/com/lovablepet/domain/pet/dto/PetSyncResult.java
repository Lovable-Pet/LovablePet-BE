package com.lovablepet.domain.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공공데이터 동기화 결과")
public record PetSyncResult(
        @Schema(description = "공공데이터에서 받은 수 (공고중·보호중 전체 + 최근 수정분, 겹치는 동물 포함)", example = "3300")
        int fetched,

        @Schema(description = "새로 저장한 동물 수", example = "120")
        int created,

        @Schema(description = "덮어쓴 동물 수", example = "3180")
        int updated,

        @Schema(description = "공공데이터 목록에서 빠져 목록에서 내린 동물 수", example = "95")
        int unlisted,

        @Schema(description = "유기번호가 없어 건너뛴 수", example = "0")
        int skipped,

        @Schema(description = "걸린 시간(ms)", example = "21500")
        long tookMs
) {
}
