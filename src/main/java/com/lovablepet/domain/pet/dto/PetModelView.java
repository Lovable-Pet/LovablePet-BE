package com.lovablepet.domain.pet.dto;

import com.lovablepet.domain.generation.entity.PetModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "AR 모델. 완성 전에는 null")
public record PetModelView(
        @Schema(description = "GLB 주소 (API 주소 기준 경로). 미터 단위 실제 크기라 AR 에서 크기를 다시 조정하지 않는다. 텍스처 포함 약 10MB",
                example = "/models/448567202600123/448567202600123-a5ca257e.glb")
        String glbUrl,

        @Schema(description = "체고(m). 표시용 (예: \"실제 크기 · 체고 55cm\"). GLB 에 이미 적용돼 있다", example = "0.546")
        BigDecimal withersM,

        @Schema(description = "만든 템플릿", example = "jindo")
        String template,

        @Schema(description = "탭 반응. 처음엔 idle 을 재생하고, 탭하면 tapAnimation 을 재생한 뒤 idle 로 돌아간다")
        Behavior behavior
) {

    public record Behavior(
            @Schema(description = "탭했을 때 재생할 애니메이션 (GLB 의 애니메이션 이름: idle, tail_wag)", example = "tail_wag")
            String tapAnimation,

            @Schema(description = "애니메이션 재생 속도 (AnimationMixer.timeScale)", example = "1.0")
            BigDecimal playbackRate
    ) {
    }

    public static PetModelView of(PetModel model, String glbUrl) {
        return new PetModelView(glbUrl, model.getWithersM(), model.getTemplate(),
                new Behavior(model.getTapAnimation(), model.getPlaybackRate()));
    }
}
