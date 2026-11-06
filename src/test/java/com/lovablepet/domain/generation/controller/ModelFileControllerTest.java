package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.domain.generation.service.PetModelFiles.Format;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFileControllerTest {

    private static final String DESERTION_NO = "448567202600123";
    private static final String JOB_ID = "448567202600123-a5ca257e";

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("GLB 를 model/gltf-binary 로, 1년 캐시(immutable)로 내려준다")
    void servesGlb() throws Exception {
        PetModelFiles files = files();
        files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{'g', 'l', 'T', 'F'});

        ResponseEntity<Resource> response = new ModelFileController(files).file(DESERTION_NO, JOB_ID + ".glb");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("model/gltf-binary"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("max-age=31536000").contains("immutable").contains("public");
        assertThat(response.getBody().getContentAsByteArray()).containsExactly('g', 'l', 'T', 'F');
    }

    @Test
    @DisplayName("USDZ 를 model/vnd.usdz+zip 으로 내려준다 (아이폰 Quick Look 은 이 Content-Type 이어야 연다)")
    void servesUsdz() throws Exception {
        PetModelFiles files = files();
        files.save(DESERTION_NO, JOB_ID, Format.USDZ, new byte[]{'P', 'K', 3, 4});

        ResponseEntity<Resource> response = new ModelFileController(files).file(DESERTION_NO, JOB_ID + ".usdz");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("model/vnd.usdz+zip"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("max-age=31536000").contains("immutable");
        assertThat(response.getBody().getContentAsByteArray()).containsExactly('P', 'K', 3, 4);
    }

    @Test
    @DisplayName("없는 파일은 COMMON_404 (GLB 만 있는 옛 모델의 .usdz 도)")
    void missing() {
        PetModelFiles files = files();
        files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{1});

        for (String name : new String[]{"nope.glb", JOB_ID + ".usdz"}) {
            assertThatThrownBy(() -> new ModelFileController(files).file(DESERTION_NO, name))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private PetModelFiles files() {
        return new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "", tempDir.toString(),
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "/models"));
    }
}
