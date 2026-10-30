package com.lovablepet.domain.generation.dto;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Fit;
import com.lovablepet.domain.generation.client.PetPipelineDtos.FitResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "적합도 확인 결과")
public record FitCheckResponse(
        @Schema(description = "good 적합 / fair 주의 / poor 부적합 (사진 거부도 poor)", example = "good")
        String level,

        @Schema(description = "적합 / 주의 / 부적합", example = "적합")
        String label,

        @Schema(description = "점수 (0~100)", example = "100")
        Integer score,

        @Schema(description = "사유 (한국어). 사진 거부 사유나 템플릿과 다른 점, 사진 품질 안내", example = "[]")
        List<String> reasons,

        @Schema(description = "참고 (예: 템플릿: 말티즈)", example = "[\"템플릿: 진도 (사진과 모델 비교)\"]")
        List<String> notes,

        @Schema(description = "만들 템플릿: jindo, jindo_puppy, maltese, toypoodle, chihuahua, retriever. 사진 거부면 null", example = "jindo")
        String template,

        @Schema(description = "사진 거부 사유 코드: person_covers, several_animals, text_on_animal, clothing, object_covers, not_a_dog. 통과면 []",
                example = "[]")
        List<String> photoRejected,

        @Schema(description = "이 사진으로 만들 수 있는지. 사진이 거부되면 false → '다른 사진 선택'으로 안내 (force 로도 못 만든다)", example = "true")
        boolean canGenerate,

        @Schema(description = "템플릿 부적합이라 force 로만 만들 수 있는지 (만들어도 그 개처럼 안 보일 수 있다)", example = "false")
        boolean needsForce,

        @Schema(description = "예상 체고(m). 표시용 — GLB 가 이미 실제 크기다. 사진이 거부되면 null", example = "0.546")
        Double withersM,

        @Schema(description = "판정에 쓴 체중(kg)", example = "25.7")
        BigDecimal weightKg,

        @Schema(description = "사진 품질 good / fair / poor. 사진이 거부되면 null", example = "good")
        String photoLevel,

        @Schema(description = "판정한 사진 주소")
        String photoUrl
) {

    public static FitCheckResponse of(FitResult result, BigDecimal weightKg, String photoUrl) {
        Fit fit = result.fit();
        boolean photoRejected = fit != null && fit.isPhotoRejected();
        return new FitCheckResponse(
                fit == null ? null : fit.level(),
                fit == null ? null : fit.label(),
                fit == null ? null : fit.score(),
                fit == null || fit.reasons() == null ? List.of() : fit.reasons(),
                fit == null || fit.notes() == null ? List.of() : fit.notes(),
                fit == null ? null : fit.template(),
                fit == null || fit.photoRejected() == null ? List.of() : fit.photoRejected(),
                !photoRejected,
                !photoRejected && fit != null && fit.isPoor(),
                result.size() == null ? null : result.size().withersM(),
                weightKg,
                result.photo() == null ? null : result.photo().level(),
                photoUrl);
    }
}
