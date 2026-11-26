package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.generation.service.PetModelWriter.PreviewAttach;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PetModelPreviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
    private static final String JOB_ID = "448567202600123-a5ca257e";
    private static final String GLB_PATH = "448567202600123/" + JOB_ID + ".glb";
    private static final String PREVIEW_PATH = "448567202600123/" + JOB_ID + "-preview-1f2e3d4c.webp";
    private static final Path GLB_FILE = Path.of("/data/storage/models/" + GLB_PATH);
    private static final byte[] WEBP = {'R', 'I', 'F', 'F'};

    @Mock
    private PetModelRepository petModelRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private PetModelWriter writer;
    @Mock
    private PetModelFiles files;
    @Mock
    private PetModelPreviewRenderer renderer;

    private PetModelPreviewService service;

    @BeforeEach
    void setUp() {
        service = new PetModelPreviewService(petModelRepository, petRepository, writer, files, renderer);
    }

    @Test
    @DisplayName("완성 모델의 GLB 를 찍어 {jobId}-preview-*.webp 로 저장하고 경로를 붙인다")
    void create_rendersAndAttaches() {
        given(petModelRepository.findById(12L)).willReturn(Optional.of(doneModel()));
        given(files.stored(GLB_PATH)).willReturn(Optional.of(GLB_FILE));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(renderer.render(GLB_FILE)).willReturn(WEBP);
        given(files.savePreview("448567202600123", JOB_ID, WEBP)).willReturn(PREVIEW_PATH);
        given(writer.attachPreview(12L, PREVIEW_PATH)).willReturn(new PreviewAttach(true, null));

        service.create(12L, false);

        verify(writer).attachPreview(12L, PREVIEW_PATH);
        verify(files, never()).delete(anyString());
    }

    @Test
    @DisplayName("찍는 사이 다시 만들기로 교체된 모델(REPLACED)이면 붙이지 않고 방금 찍은 그림을 지운다")
    void create_replacedWhileRendering_deletesNewPreview() {
        given(petModelRepository.findById(12L)).willReturn(Optional.of(doneModel()));
        given(files.stored(GLB_PATH)).willReturn(Optional.of(GLB_FILE));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(renderer.render(GLB_FILE)).willReturn(WEBP);
        given(files.savePreview("448567202600123", JOB_ID, WEBP)).willReturn(PREVIEW_PATH);
        given(writer.attachPreview(12L, PREVIEW_PATH)).willReturn(PreviewAttach.SKIPPED);

        service.create(12L, false);

        verify(files).delete(PREVIEW_PATH);
    }

    @Test
    @DisplayName("이미 그림이 있으면 다시 찍지 않고, 다시 찍기(again)면 새 그림을 붙이고 이전 그림 파일을 지운다")
    void create_again_replacesPrevious() {
        PetModel model = doneModel();
        model.attachPreview("448567202600123/" + JOB_ID + "-preview-00000000.webp");
        given(petModelRepository.findById(12L)).willReturn(Optional.of(model));

        service.create(12L, false);
        verifyNoInteractions(renderer, writer);

        given(files.stored(GLB_PATH)).willReturn(Optional.of(GLB_FILE));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(renderer.render(GLB_FILE)).willReturn(WEBP);
        given(files.savePreview("448567202600123", JOB_ID, WEBP)).willReturn(PREVIEW_PATH);
        given(writer.attachPreview(12L, PREVIEW_PATH))
                .willReturn(new PreviewAttach(true, "448567202600123/" + JOB_ID + "-preview-00000000.webp"));

        service.create(12L, true);

        verify(files).delete("448567202600123/" + JOB_ID + "-preview-00000000.webp");
    }

    @Test
    @DisplayName("찍지 못하면(크롬 없음·GLB 오류) 경로를 붙이지 않고 예외도 밖으로 내지 않는다")
    void create_renderFails_keepsModel() {
        given(petModelRepository.findById(12L)).willReturn(Optional.of(doneModel()));
        given(files.stored(GLB_PATH)).willReturn(Optional.of(GLB_FILE));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        willThrow(new RuntimeException("Executable doesn't exist")).given(renderer).render(GLB_FILE);

        service.create(12L, false);

        verifyNoInteractions(writer);
        verify(files, never()).savePreview(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("완성되지 않았거나 GLB 파일이 없으면 찍지 않는다")
    void create_skipsWithoutGlb() {
        PetModel pending = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("25.70"), false, 3L, NOW);
        given(petModelRepository.findById(11L)).willReturn(Optional.of(pending));
        given(petModelRepository.findById(12L)).willReturn(Optional.of(doneModel()));
        given(files.stored(GLB_PATH)).willReturn(Optional.empty());

        service.create(11L, false);
        service.create(12L, false);

        verifyNoInteractions(renderer, writer);
    }

    @Test
    @DisplayName("일괄: 그림이 없는 완성 모델을 줄 세워 뒤에서 찍고, 줄 세운 수를 돌려준다")
    void requestAll_queuesMissing() {
        given(petModelRepository.findIdsForPreview(PetModelStatus.DONE, false)).willReturn(List.of(12L));
        given(petModelRepository.findById(12L)).willReturn(Optional.of(doneModel()));
        given(files.stored(GLB_PATH)).willReturn(Optional.of(GLB_FILE));
        given(petRepository.findById(7L)).willReturn(Optional.of(pet()));
        given(renderer.render(GLB_FILE)).willReturn(WEBP);
        given(files.savePreview("448567202600123", JOB_ID, WEBP)).willReturn(PREVIEW_PATH);
        given(writer.attachPreview(12L, PREVIEW_PATH)).willReturn(new PreviewAttach(true, null));

        assertThat(service.requestAll(false)).isEqualTo(1);

        verify(writer, timeout(2000)).attachPreview(12L, PREVIEW_PATH);
    }

    private PetModel doneModel() {
        PetModel model = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("25.70"), false, 3L, NOW);
        model.attachJob(JOB_ID);
        model.complete(new Job(JOB_ID, "448567202600123", "done", null, null, null, null, null, null, null, null, null),
                GLB_PATH, null, NOW);
        ReflectionTestUtils.setField(model, "id", 12L);
        return model;
    }

    private Pet pet() {
        return Pet.from(new PublicAnimalItem(JsonMapper.builder().build()
                .readTree("{\"desertionNo\":\"448567202600123\",\"processState\":\"보호중\"}")), NOW);
    }
}
