package com.lovablepet.domain.generation.entity;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Behavior;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Fit;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetModelTest {

    private static final Instant CREATED = Instant.parse("2026-10-29T03:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-29T03:03:00Z");
    private static final Fit GOOD = new Fit("good", "적합", 100, List.of(), List.of(), "jindo", "vision", "close", List.of(), "jindo", List.of(), false);

    @Test
    @DisplayName("완성: GLB·USDZ 경로·체고·탭 동작·적합도·비용을 남기고 DONE")
    void complete() {
        PetModel model = reserved();
        Job done = new Job("j1", "1", "done", GOOD, null, new Size(25.7, 0.546, 0.8842, "weight"), null,
                List.of("idle", "tail_wag"), new Behavior(1.0, "tail_wag", false), null, null, 0.075);

        model.complete(done, "1/j1.glb", "1/j1.usdz", FINISHED);

        assertThat(model.getStatus()).isEqualTo(PetModelStatus.DONE);
        assertThat(model.getGlbPath()).isEqualTo("1/j1.glb");
        assertThat(model.getUsdzPath()).isEqualTo("1/j1.usdz");
        assertThat(model.getWithersM()).isEqualByComparingTo("0.546");
        assertThat(model.getTapAnimation()).isEqualTo("tail_wag");
        assertThat(model.getPlaybackRate()).isEqualByComparingTo("1.0");
        assertThat(model.getTemplate()).isEqualTo("jindo");
        assertThat(model.getFitScore()).isEqualTo(100);
        assertThat(model.getCostUsd()).isEqualByComparingTo("0.075");
        assertThat(model.getFinishedAt()).isEqualTo(FINISHED);
        assertThat(model.isPending()).isFalse();
    }

    @Test
    @DisplayName("사진 거부: 작업의 reasons(한국어)를 사유로 남긴다")
    void photoRejected() {
        PetModel model = reserved();

        model.finishWithoutModel(new Job("j1", "1", "photo_rejected", null, null, null, null, null, null,
                List.of("개가 여러 마리"), "photo rejected", 0.006), FINISHED);

        assertThat(model.getStatus()).isEqualTo(PetModelStatus.PHOTO_REJECTED);
        assertThat(model.getReasons()).containsExactly("개가 여러 마리");
        assertThat(model.getGlbPath()).isNull();
        assertThat(model.getUsdzPath()).isNull();
    }

    @Test
    @DisplayName("템플릿 부적합으로 멈춤: fit.reasons 를 사유로 남긴다")
    void unsuitable() {
        PetModel model = reserved();
        Fit poor = new Fit("poor", "부적합", 30, List.of("단두형이라 맞는 템플릿이 없음"), List.of(), null, "looks", null, null, "none", List.of(), true);

        model.finishWithoutModel(new Job("j1", "1", "unsuitable", poor, null, null, null, null, null, null, null, 0.02), FINISHED);

        assertThat(model.getStatus()).isEqualTo(PetModelStatus.UNSUITABLE);
        assertThat(model.getReasons()).containsExactly("단두형이라 맞는 템플릿이 없음");
        assertThat(model.getFitLevel()).isEqualTo("poor");
    }

    @Test
    @DisplayName("실패: 파이프라인 오류(재시작 포함)는 FAILED 와 오류 문구")
    void failed() {
        PetModel model = reserved();

        model.finishWithoutModel(new Job("j1", "1", "failed", null, null, null, null, null, null, null,
                "server restarted before the job finished", null), FINISHED);

        assertThat(model.getStatus()).isEqualTo(PetModelStatus.FAILED);
        assertThat(model.getError()).isEqualTo("server restarted before the job finished");
        assertThat(model.getReasons()).isEmpty();
    }

    @Test
    @DisplayName("교체: 다시 만든 모델이 완성되면 옛 모델은 REPLACED, 파일 경로를 비우고 지울 파일(있는 것만)을 돌려준다")
    void replace() {
        PetModel model = reserved();
        model.complete(new Job("j1", "1", "done", GOOD, null, null, null, null, null, null, null, 0.006),
                "1/j1.glb", null, FINISHED);
        model.attachPreview("1/j1-preview-0a1b2c3d.webp");

        List<String> files = model.replace();

        assertThat(files).containsExactly("1/j1.glb", "1/j1-preview-0a1b2c3d.webp"); // USDZ 가 없으면 빠진다
        assertThat(model.getStatus()).isEqualTo(PetModelStatus.REPLACED);
        assertThat(model.getGlbPath()).isNull();
        assertThat(model.getUsdzPath()).isNull();
        assertThat(model.getPreviewPath()).isNull();
        assertThat(model.getFinishedAt()).isEqualTo(FINISHED); // 기록은 그대로
    }

    private PetModel reserved() {
        PetModel model = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("25.70"), false, 3L, CREATED);
        model.attachJob("j1");
        assertThat(model.isPending()).isTrue();
        return model;
    }
}
