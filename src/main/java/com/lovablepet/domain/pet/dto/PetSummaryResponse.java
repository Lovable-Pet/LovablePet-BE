package com.lovablepet.domain.pet.dto;

import com.lovablepet.domain.pet.entity.Pet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "목록의 동물 한 마리")
public record PetSummaryResponse(
        @Schema(description = "동물 ID (상세 조회에 사용)", example = "1024")
        Long id,

        @Schema(description = "화면에 보여 줄 이름. 공고에 이름이 없어 품종 · 관할 지자체 · 체중으로 만든다", example = "믹스견 · 경기도 화성시 · 5kg")
        String displayName,

        @Schema(description = "품종", example = "믹스견")
        String kindName,

        @Schema(description = "성별: M 수컷, F 암컷, Q 미상", example = "F")
        String sex,

        @Schema(description = "나이 원문", example = "2024(년생)")
        String ageText,

        @Schema(description = "체중(kg). 원문을 숫자로 바꾸지 못하면 null", example = "5.00")
        BigDecimal weightKg,

        @Schema(description = "관할 지자체", example = "경기도 화성시")
        String orgName,

        @Schema(description = "보호소", example = "화성시동물보호센터")
        String careName,

        @Schema(description = "대표 사진(첫 번째 사진) 주소. 사진이 없으면 null", example = "https://openapi.animal.go.kr/openapi/service/rest/fileDownloadSrvc/files/shelter/2026/10/202610281010123.jpg")
        String thumbnailUrl,

        @Schema(description = "공고 시작일", example = "2026-10-12")
        LocalDate noticeStart
) {

    public static PetSummaryResponse from(Pet pet) {
        return new PetSummaryResponse(
                pet.getId(),
                pet.displayName(),
                pet.getKindName(),
                pet.getSex(),
                pet.getAgeText(),
                pet.getWeightKg(),
                pet.getOrgName(),
                pet.getCareName(),
                pet.getPhotoUrls().isEmpty() ? null : pet.getPhotoUrls().getFirst(),
                pet.getNoticeStart());
    }
}
