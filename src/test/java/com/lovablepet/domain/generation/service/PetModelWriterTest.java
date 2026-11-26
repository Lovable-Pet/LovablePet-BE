package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.generation.service.PetModelWriter.Finish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PetModelWriterTest {

    private static final Instant CREATED = Instant.parse("2026-11-26T03:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-26T03:03:00Z");

    @Mock
    private PetModelRepository petModelRepository;

    private PetModelWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PetModelWriter(petModelRepository);
    }

    @Test
    @DisplayName("다시 만든 모델 완성: 옛 완성 모델을 REPLACED 로 바꿔 먼저 DB 에 보낸 뒤(유니크 인덱스) 새 모델을 DONE 으로, 옛 파일 경로를 돌려준다")
    void done_replacesOldModelFirst() {
        PetModel old = model(11L, "j1");
        old.complete(job("j1", "done"), "1/j1.glb", "1/j1.usdz", CREATED);
        old.attachPreview("1/j1-preview-0a1b2c3d.webp");
        PetModel fresh = model(12L, "j2");
        given(petModelRepository.findById(12L)).willReturn(Optional.of(fresh));
        given(petModelRepository.findByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(Optional.of(old));
        willAnswer(invocation -> {
            // flush 하는 순간 DONE 은 아무도 아니어야 uk_pet_model_done 에 걸리지 않는다
            assertThat(old.getStatus()).isEqualTo(PetModelStatus.REPLACED);
            assertThat(fresh.getStatus()).isEqualTo(PetModelStatus.PENDING);
            return null;
        }).given(petModelRepository).flush();

        Finish finish = writer.finish(12L, job("j2", "done"), "1/j2.glb", null, NOW);

        verify(petModelRepository).flush();
        assertThat(finish.applied()).isTrue();
        assertThat(finish.replacedFiles()).containsExactly("1/j1.glb", "1/j1.usdz", "1/j1-preview-0a1b2c3d.webp");
        assertThat(fresh.getStatus()).isEqualTo(PetModelStatus.DONE);
        assertThat(fresh.getGlbPath()).isEqualTo("1/j2.glb");
        assertThat(old.getGlbPath()).isNull();
    }

    @Test
    @DisplayName("처음 만든 모델 완성: 교체할 모델이 없으면 지울 파일도 없다")
    void done_firstModel() {
        PetModel fresh = model(12L, "j2");
        given(petModelRepository.findById(12L)).willReturn(Optional.of(fresh));
        given(petModelRepository.findByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(Optional.empty());

        Finish finish = writer.finish(12L, job("j2", "done"), "1/j2.glb", null, NOW);

        assertThat(finish).isEqualTo(Finish.applied(List.of()));
        assertThat(fresh.getStatus()).isEqualTo(PetModelStatus.DONE);
        verify(petModelRepository, never()).flush();
    }

    @Test
    @DisplayName("다시 만들기가 실패하면 옛 완성 모델은 건드리지 않는다 (계속 보여 준다)")
    void failed_keepsOldModel() {
        PetModel fresh = model(12L, "j2");
        given(petModelRepository.findById(12L)).willReturn(Optional.of(fresh));

        Finish finish = writer.finish(12L, job("j2", "failed"), null, null, NOW);

        assertThat(finish).isEqualTo(Finish.applied(List.of()));
        assertThat(fresh.getStatus()).isEqualTo(PetModelStatus.FAILED);
        verify(petModelRepository, never()).findByPetIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("이미 끝난 시도면 SKIPPED")
    void notPending_skipped() {
        PetModel done = model(12L, "j2");
        done.complete(job("j2", "done"), "1/j2.glb", null, CREATED);
        given(petModelRepository.findById(12L)).willReturn(Optional.of(done));

        assertThat(writer.finish(12L, job("j2", "done"), "1/j2.glb", null, NOW)).isEqualTo(Finish.SKIPPED);
        verify(petModelRepository, never()).findByPetIdAndStatus(any(), any());
    }

    private static PetModel model(long id, String jobId) {
        PetModel model = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("25.70"), false, 3L, CREATED);
        model.attachJob(jobId);
        ReflectionTestUtils.setField(model, "id", id);
        return model;
    }

    private static Job job(String jobId, String status) {
        return new Job(jobId, "1", status, null, null, null, null, null, null, null, null, null);
    }
}
