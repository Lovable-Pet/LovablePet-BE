package com.lovablepet.domain.pet.dto;

import com.lovablepet.domain.pet.entity.Pet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 동물 상세. 공공데이터 응답 원본(pet.raw)은 보호소 담당자 이름이 들어 있어 내보내지 않는다.
 */
@Schema(description = "동물 상세")
public record PetDetailResponse(
        @Schema(description = "동물 ID", example = "1024")
        Long id,

        @Schema(description = "유기번호 (공공데이터의 동물 고유번호)", example = "448567202600123")
        String desertionNo,

        @Schema(description = "공고번호", example = "경기-화성-2026-01509")
        String noticeNo,

        @Schema(description = "화면에 보여 줄 이름 (품종 · 관할 지자체 · 체중)", example = "믹스견 · 경기도 화성시 · 5kg")
        String displayName,

        @Schema(description = "품종", example = "믹스견")
        String kindName,

        @Schema(description = "털색 원문", example = "갈색&흰색")
        String color,

        @Schema(description = "나이 원문", example = "2024(년생)")
        String ageText,

        @Schema(description = "출생연도", example = "2024")
        Integer birthYear,

        @Schema(description = "체중 원문", example = "5.0(Kg)")
        String weightText,

        @Schema(description = "체중(kg). 원문을 숫자로 바꾸지 못하면 null", example = "5.00")
        BigDecimal weightKg,

        @Schema(description = "성별: M 수컷, F 암컷, Q 미상", example = "F")
        String sex,

        @Schema(description = "중성화: Y 예, N 아니오, U 미상", example = "Y")
        String neutered,

        @Schema(description = "공공데이터 상태: 보호중, 종료(입양), 종료(반환) ... (공고 기간 중인 동물도 보호중)", example = "보호중")
        String processState,

        @Schema(description = "지금 입양 가능한지 (보호중이고 공고 기간이 끝났음). 입양·반환 등으로 종료되면 false", example = "true")
        boolean adoptable,

        @Schema(description = "공고 시작일", example = "2026-10-12")
        LocalDate noticeStart,

        @Schema(description = "공고 종료일. 이 날이 지나면 입양할 수 있다", example = "2026-10-26")
        LocalDate noticeEnd,

        @Schema(description = "관할 지자체", example = "경기도 화성시")
        String orgName,

        @Schema(description = "발견 장소", example = "화성시 봉담읍")
        String happenPlace,

        @Schema(description = "보호소", example = "화성시동물보호센터")
        String careName,

        @Schema(description = "보호소 전화번호", example = "031-000-0000")
        String careTel,

        @Schema(description = "보호소 주소", example = "경기도 화성시 ...")
        String careAddr,

        @Schema(description = "특징", example = "사람을 잘 따름")
        String specialMark,

        @Schema(description = "사진 주소 (https, 중복 제거)")
        List<String> photoUrls,

        @Schema(description = "AR 모델. 아직 만들지 않았으면 null. weightKg 가 null 인 개는 실제 크기를 알 수 없어 만들 수 없다")
        PetModelView model
) {

    public static PetDetailResponse of(Pet pet, LocalDate today, PetModelView model) {
        return new PetDetailResponse(
                pet.getId(),
                pet.getDesertionNo(),
                pet.getNoticeNo(),
                pet.displayName(),
                pet.getKindName(),
                pet.getColor(),
                pet.getAgeText(),
                pet.getBirthYear(),
                pet.getWeightText(),
                pet.getWeightKg(),
                pet.getSex(),
                pet.getNeutered(),
                pet.getProcessState(),
                pet.isAdoptable(today),
                pet.getNoticeStart(),
                pet.getNoticeEnd(),
                pet.getOrgName(),
                pet.getHappenPlace(),
                pet.getCareName(),
                pet.getCareTel(),
                pet.getCareAddr(),
                pet.getSpecialMark(),
                pet.getPhotoUrls(),
                model);
    }
}
