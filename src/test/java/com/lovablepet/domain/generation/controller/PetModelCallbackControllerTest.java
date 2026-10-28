package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.domain.generation.service.PetModelResultService;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PetModelCallbackController 단위 테스트: X-API-Key 검사.
 */
@ExtendWith(MockitoExtension.class)
class PetModelCallbackControllerTest {

    private static final String API_KEY = "test-pipeline-key";
    private static final Job JOB = new Job("E07-a5ca257e", "E07", "done",
            null, null, null, null, null, null, null, null, null);

    @Mock
    private PetModelResultService petModelResultService;

    private PetModelCallbackController petModelCallbackController;

    @BeforeEach
    void setUp() {
        PetPipelineProperties properties = new PetPipelineProperties(
                "http://pipeline:8000", API_KEY, "", "models", Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30);
        petModelCallbackController = new PetModelCallbackController(petModelResultService, properties);
    }

    @Test
    @DisplayName("키가 맞으면 콜백을 처리한다")
    void onJobFinished_validKey_handlesJob() {
        petModelCallbackController.onJobFinished(API_KEY, JOB);

        verify(petModelResultService).apply(JOB);
    }

    @Test
    @DisplayName("키가 틀리면 GENERATION_401_KEY 로 거부한다")
    void onJobFinished_wrongKey_rejected() {
        assertThatThrownBy(() -> petModelCallbackController.onJobFinished("wrong-key", JOB))
                .isInstanceOf(GenerationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_API_KEY);

        verify(petModelResultService, never()).apply(any());
    }

    @Test
    @DisplayName("키가 없으면 GENERATION_401_KEY 로 거부한다")
    void onJobFinished_missingKey_rejected() {
        assertThatThrownBy(() -> petModelCallbackController.onJobFinished(null, JOB))
                .isInstanceOf(GenerationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_API_KEY);

        verify(petModelResultService, never()).apply(any());
    }
}
