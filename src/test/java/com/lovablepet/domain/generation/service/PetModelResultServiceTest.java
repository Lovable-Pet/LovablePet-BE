package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PetModelResultServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    private static final String JOB_ID = "448567202600123-a5ca257e";
    private static final byte[] GLB = {'g', 'l', 'T', 'F'};

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PetModelRepository petModelRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private PetModelWriter writer;
    @Mock
    private PetPipelineClient pipelineClient;
    @Mock
    private PetModelFiles files;

    private PetModelResultService service;

    @BeforeEach
    void setUp() {
        service = new PetModelResultService(petModelRepository, petRepository, writer, pipelineClient, files,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("done: GLB 를 받아 {유기번호}/{jobId}.glb 로 저장하고 결과를 반영한 뒤 파이프라인 작업 파일을 지운다")
    void done_savesGlbAndCleansUp() {
        PetModel model = pendingModel();
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.of(model));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(pipelineClient.download(JOB_ID, "model.glb")).willReturn(GLB);
        given(files.saveGlb("448567202600123", JOB_ID, GLB)).willReturn("448567202600123/" + JOB_ID + ".glb");
        Job job = job("done");
        given(writer.finish(12L, job, "448567202600123/" + JOB_ID + ".glb", NOW)).willReturn(true);

        service.apply(job);

        verify(pipelineClient).delete(JOB_ID);
    }

    @Test
    @DisplayName("같은 콜백이 또 오면(이미 DONE) GLB 를 다시 받지 않고 무시한다")
    void duplicate_ignored() {
        PetModel model = pendingModel();
        model.complete(job("done"), "448567202600123/" + JOB_ID + ".glb", NOW);
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.of(model));

        service.apply(job("done"));

        verifyNoInteractions(pipelineClient, files, writer);
    }

    @Test
    @DisplayName("모르는 작업이거나 아직 안 끝난 상태면 아무것도 하지 않는다")
    void unknownOrUnfinished_ignored() {
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.empty());
        service.apply(job("done"));

        service.apply(job("running"));

        verify(petModelRepository).findByJobId(JOB_ID); // running 은 조회도 안 한다
        verifyNoInteractions(pipelineClient, files, writer);
    }

    @Test
    @DisplayName("photo_rejected: GLB 없이 결과만 반영하고, 원인을 볼 수 있게 파이프라인 파일은 남긴다")
    void photoRejected_recordsWithoutGlb() {
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.of(pendingModel()));
        Job job = job("photo_rejected");
        given(writer.finish(eq(12L), eq(job), isNull(), eq(NOW))).willReturn(true);

        service.apply(job);

        verify(pipelineClient, never()).download(anyString(), anyString());
        verify(pipelineClient, never()).delete(anyString());
    }

    @Test
    @DisplayName("콜백과 작업 확인이 동시에 반영하면(@Version 충돌) 진 쪽은 조용히 끝낸다")
    void optimisticLock_swallowed() {
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.of(pendingModel()));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(pipelineClient.download(JOB_ID, "model.glb")).willReturn(GLB);
        given(files.saveGlb(any(), any(), any())).willReturn("448567202600123/" + JOB_ID + ".glb");
        given(writer.finish(any(), any(), any(), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(PetModel.class, 12L));

        service.apply(job("done"));

        verify(pipelineClient, never()).delete(anyString());
    }

    @Test
    @DisplayName("파이프라인 작업 파일을 못 지워도 결과 반영은 성공으로 끝낸다")
    void cleanupFailure_ignored() {
        given(petModelRepository.findByJobId(JOB_ID)).willReturn(Optional.of(pendingModel()));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(pipelineClient.download(JOB_ID, "model.glb")).willReturn(GLB);
        given(files.saveGlb(any(), any(), any())).willReturn("p.glb");
        given(writer.finish(any(), any(), any(), any())).willReturn(true);
        willThrow(new ResourceAccessException("down")).given(pipelineClient).delete(JOB_ID);

        service.apply(job("done"));

        verify(pipelineClient).delete(JOB_ID);
    }

    private PetModel pendingModel() {
        PetModel model = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("25.70"), false, 3L, NOW);
        model.attachJob(JOB_ID);
        ReflectionTestUtils.setField(model, "id", 12L);
        return model;
    }

    private Pet pet() {
        return Pet.from(new PublicAnimalItem(jsonMapper.readTree("{\"desertionNo\":\"448567202600123\",\"processState\":\"보호중\"}")), NOW);
    }

    private static Job job(String status) {
        return new Job(JOB_ID, "448567202600123", status, null, null, null, null, null, null, null, null, null);
    }
}
