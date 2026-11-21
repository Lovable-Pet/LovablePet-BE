package com.lovablepet.domain.generation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 크롬으로 찍어 보는 시험. 크롬이 깔린 곳에서만 손으로 돌린다 (CI 에는 크롬이 없다).
 * <pre>
 * PREVIEW_GLB=/경로/model.glb ./gradlew test --tests '*PetModelPreviewRendererTest'
 * </pre>
 * 찍은 그림은 build/preview-test.webp 에 남긴다.
 */
@EnabledIfEnvironmentVariable(named = "PREVIEW_GLB", matches = ".+")
class PetModelPreviewRendererTest {

    @Test
    void 투명_배경_WebP_를_찍는다() throws Exception {
        Path glb = Path.of(System.getenv("PREVIEW_GLB"));

        byte[] webp = new PetModelPreviewRenderer().render(glb);

        // RIFF....WEBP, 투명(알파)이 있으면 VP8X 확장 머리의 알파 비트가 켜진다
        assertThat(new String(Arrays.copyOfRange(webp, 0, 4))).isEqualTo("RIFF");
        assertThat(new String(Arrays.copyOfRange(webp, 8, 12))).isEqualTo("WEBP");
        assertThat(new String(Arrays.copyOfRange(webp, 12, 16))).isEqualTo("VP8X");
        assertThat(webp[20] & 0x10).as("알파 채널").isNotZero();
        int width = 1 + ((webp[24] & 0xff) | (webp[25] & 0xff) << 8 | (webp[26] & 0xff) << 16);
        int height = 1 + ((webp[27] & 0xff) | (webp[28] & 0xff) << 8 | (webp[29] & 0xff) << 16);
        assertThat(width).isEqualTo(PetModelPreviewRenderer.WIDTH);
        assertThat(height).isEqualTo(PetModelPreviewRenderer.HEIGHT);
        Files.write(Path.of("build/preview-test.webp"), webp);
    }
}
