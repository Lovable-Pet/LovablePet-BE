package com.lovablepet.domain.generation.dto;

import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "AR 생성 시도")
public record PetModelResponse(
        @Schema(description = "생성 시도 ID", example = "12")
        Long id,

        @Schema(description = "동물 ID", example = "1024")
        Long petId,

        @Schema(description = "PENDING 만드는 중 / DONE 완성 / PHOTO_REJECTED 사진 거부 / UNSUITABLE 템플릿 부적합으로 멈춤 / FAILED 실패",
                example = "PENDING")
        PetModelStatus status,

        @Schema(description = "파이프라인 작업 ID", example = "448567202600123-a5ca257e")
        String jobId,

        @Schema(description = "쓴 사진 주소")
        String photoUrl,

        @Schema(description = "쓴 체중(kg)", example = "25.7")
        BigDecimal weightKg,

        @Schema(description = "템플릿 부적합이어도 끝까지 만들었는지", example = "false")
        boolean forced,

        @Schema(description = "적합도 good / fair / poor", example = "good")
        String fitLevel,

        @Schema(example = "100")
        Integer fitScore,

        @Schema(description = "템플릿", example = "jindo")
        String template,

        @Schema(description = "사진 거부·부적합 사유 (한국어)")
        List<String> reasons,

        @Schema(description = "실패 사유")
        String error,

        @Schema(description = "완성된 GLB 주소 (완성 전 null)", example = "/models/448567202600123/448567202600123-a5ca257e.glb")
        String glbUrl,

        @Schema(description = "체고(m). 표시용 — GLB 가 이미 실제 크기라 AR 에서 크기를 다시 조정하지 않는다", example = "0.546")
        BigDecimal withersM,

        @Schema(description = "탭했을 때 재생할 애니메이션", example = "tail_wag")
        String tapAnimation,

        @Schema(description = "애니메이션 재생 속도", example = "1.0")
        BigDecimal playbackRate,

        @Schema(description = "비용(USD)", example = "0.075")
        BigDecimal costUsd,

        Instant createdAt,

        Instant finishedAt
) {

    public static PetModelResponse from(PetModel model, String glbUrl) {
        return new PetModelResponse(model.getId(), model.getPetId(), model.getStatus(), model.getJobId(),
                model.getPhotoUrl(), model.getWeightKg(), model.isForced(), model.getFitLevel(), model.getFitScore(),
                model.getTemplate(), model.getReasons(), model.getError(), glbUrl, model.getWithersM(), model.getTapAnimation(),
                model.getPlaybackRate(), model.getCostUsd(), model.getCreatedAt(), model.getFinishedAt());
    }
}
