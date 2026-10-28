package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFileControllerTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("GLB 를 model/gltf-binary 로, 1년 캐시(immutable)로 내려준다")
    void servesGlb() throws Exception {
        PetModelFiles files = files();
        files.saveGlb("448567202600123", "448567202600123-a5ca257e", new byte[]{'g', 'l', 'T', 'F'});

        ResponseEntity<Resource> response = new ModelFileController(files).glb("448567202600123", "448567202600123-a5ca257e.glb");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(ModelFileController.GLTF_BINARY);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("max-age=31536000").contains("immutable").contains("public");
        assertThat(response.getBody().getContentAsByteArray()).containsExactly('g', 'l', 'T', 'F');
    }

    @Test
    @DisplayName("없는 파일은 COMMON_404")
    void missing() {
        assertThatThrownBy(() -> new ModelFileController(files()).glb("448567202600123", "nope.glb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private PetModelFiles files() {
        return new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "", tempDir.toString(),
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "/models"));
    }
}
