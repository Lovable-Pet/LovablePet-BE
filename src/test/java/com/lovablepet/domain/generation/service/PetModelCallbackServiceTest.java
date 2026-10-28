package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PetModelCallbackService 단위 테스트 (DB·파이프라인 없이 실행).
 * 파이프라인 클라이언트는 Mock, GLB 는 임시 폴더에 저장한다.
 */
@ExtendWith(MockitoExtension.class)
class PetModelCallbackServiceTest {

    @TempDir
    private Path tempDir;

    @Mock
    private PetPipelineClient petPipelineClient;

    private PetModelCallbackService petModelCallbackService;

    @BeforeEach
    void setUp() {
        PetPipelineProperties properties = new PetPipelineProperties(
                "http://pipeline:8000", "test-key", "", tempDir.resolve("models").toString(),
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30);
        petModelCallbackService = new PetModelCallbackService(petPipelineClient, properties);
    }

    @Test
    @DisplayName("done 이면 GLB 를 받아 model-dir/{관리번호}.glb 로 저장한다")
    void handle_done_savesGlb() throws IOException {
        byte[] glb = {0x67, 0x6C, 0x54, 0x46};
        given(petPipelineClient.download("E07-a5ca257e", "model.glb")).willReturn(glb);

        petModelCallbackService.handle(job("E07-a5ca257e", "경기-포천-2026-00031", "done"));

        assertThat(tempDir.resolve("models").resolve("경기-포천-2026-00031.glb")).hasBinaryContent(glb);
    }

    @Test
    @DisplayName("관리번호로 model-dir 밖의 경로를 만들면 거부하고 파일을 받지 않는다")
    void handle_petIdEscapingModelDir_rejected() {
        assertThatThrownBy(() -> petModelCallbackService.handle(job("job-1", "../escape", "done")))
                .isInstanceOf(GenerationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_PET_ID);

        verify(petPipelineClient, never()).download(any(), any());
        assertThat(tempDir.resolve("escape.glb")).doesNotExist();
    }

    @Test
    @DisplayName("관리번호가 비어 있으면 거부한다")
    void handle_blankPetId_rejected() {
        assertThatThrownBy(() -> petModelCallbackService.handle(job("job-2", " ", "done")))
                .isInstanceOf(GenerationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_PET_ID);
    }

    @Test
    @DisplayName("done 이 아니면 파일을 받지 않는다")
    void handle_notDone_doesNotDownload() throws IOException {
        petModelCallbackService.handle(job("job-3", "E19", "photo_rejected"));
        petModelCallbackService.handle(job("job-4", "E19", "unsuitable"));
        petModelCallbackService.handle(job("job-5", "E19", "failed"));

        verify(petPipelineClient, never()).download(any(), any());
    }

    private static Job job(String jobId, String petId, String status) {
        return new Job(jobId, petId, status, null, null, null, null, null, null, null, null, null);
    }
}
