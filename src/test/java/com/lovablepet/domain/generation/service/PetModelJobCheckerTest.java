package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PetModelJobCheckerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    @Mock
    private PetModelRepository petModelRepository;
    @Mock
    private PetModelWriter writer;
    @Mock
    private PetPipelineClient pipelineClient;
    @Mock
    private PetModelResultService resultService;

    private PetModelJobChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PetModelJobChecker(petModelRepository, writer, pipelineClient, resultService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("만든 지 10분이 넘은 만드는 중만 찾고, 없으면 파이프라인을 부르지 않는다")
    void onlyOldPending() {
        given(petModelRepository.findAllByStatusAndCreatedAtBefore(PetModelStatus.PENDING, NOW.minus(Duration.ofMinutes(10))))
                .willReturn(List.of());

        checker.checkPendingJobs();

        verifyNoInteractions(pipelineClient, writer, resultService);
    }

    @Test
    @DisplayName("파이프라인 재시작으로 failed 가 된 작업(콜백 없음)을 찾아 콜백과 같이 반영한다")
    void finishedJob_applied() {
        Job failed = new Job("j1", "1", "failed", null, null, null, null, null, null, null,
                "server restarted before the job finished", null);
        given(pipelineClient.getJob("j1")).willReturn(failed);

        checker.check(model("j1", Duration.ofMinutes(15)), NOW);

        verify(resultService).apply(failed);
    }

    @Test
    @DisplayName("파이프라인에 작업이 없으면(404, 새로 배포) FAILED")
    void missingJob_failed() {
        given(pipelineClient.getJob("j1")).willThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        checker.check(model("j1", Duration.ofMinutes(15)), NOW);

        verify(writer).fail(eq(12L), contains("파이프라인에 작업이 없습니다"), eq(NOW));
    }

    @Test
    @DisplayName("파이프라인에 연결이 안 되면 건너뛰고 다음에 다시 본다")
    void unreachable_skipped() {
        given(pipelineClient.getJob("j1")).willThrow(new ResourceAccessException("connection refused"));

        checker.check(model("j1", Duration.ofMinutes(15)), NOW);

        verifyNoInteractions(writer, resultService);
    }

    @Test
    @DisplayName("줄을 서 있거나 만드는 중이면 그대로 두고, 6시간이 지나면 FAILED")
    void stillRunning() {
        given(pipelineClient.getJob(anyString())).willReturn(new Job("j1", "1", "queued", null, null, null, null, null, null, null, null, null));

        checker.check(model("j1", Duration.ofHours(1)), NOW);
        verify(writer, never()).fail(any(), any(), any());

        checker.check(model("j1", Duration.ofHours(7)), NOW);
        verify(writer).fail(eq(12L), contains("6시간"), eq(NOW));
        verify(resultService, never()).apply(any());
    }

    @Test
    @DisplayName("작업 ID 를 받기 전에 중단된 자리(등록 중 서버 꺼짐)는 FAILED")
    void reservationWithoutJob_failed() {
        checker.check(model(null, Duration.ofMinutes(15)), NOW);

        verify(writer).fail(eq(12L), contains("등록하는 중에 중단"), eq(NOW));
        verifyNoInteractions(pipelineClient);
    }

    private PetModel model(String jobId, Duration age) {
        PetModel model = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("5.00"), false, 3L, NOW.minus(age));
        if (jobId != null) {
            model.attachJob(jobId);
        }
        ReflectionTestUtils.setField(model, "id", 12L);
        return model;
    }
}
