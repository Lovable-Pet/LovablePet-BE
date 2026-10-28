package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPhoto;
import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Fit;
import com.lovablepet.domain.generation.client.PetPipelineDtos.FitResult;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.PetRecord;
import com.lovablepet.domain.generation.dto.FitCheckRequest;
import com.lovablepet.domain.generation.dto.FitCheckResponse;
import com.lovablepet.domain.generation.dto.GenerateModelRequest;
import com.lovablepet.domain.generation.dto.PetModelResponse;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PetModelGenerationServiceTest {

    // 2026-10-29 01:00 한국 시각 (UTC 로는 아직 10-28)
    private static final Instant NOW = Instant.parse("2026-10-28T16:00:00Z");
    private static final Instant START_OF_TODAY_KST = Instant.parse("2026-10-28T15:00:00Z");
    private static final String PHOTO_0 = "https://openapi.animal.go.kr/files/a.jpg";
    private static final String PHOTO_1 = "https://openapi.animal.go.kr/files/b.jpg";
    private static final PetPhoto PHOTO = PetPhoto.from(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).orElseThrow();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PetRepository petRepository;
    @Mock
    private PetModelRepository petModelRepository;
    @Mock
    private PetModelWriter writer;
    @Mock
    private PetPipelineClient pipelineClient;
    @Mock
    private PetPhotoSource photoSource;

    private PetModelGenerationService service;

    @BeforeEach
    void setUp() {
        PetPipelineProperties properties = new PetPipelineProperties("http://pipeline:8000", "key", "", "models",
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30);
        service = new PetModelGenerationService(petRepository, petModelRepository, writer, pipelineClient, photoSource,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("기록: pet_id 는 유기번호, 중성화는 Y → true, N·U(미상) → false 로 보낸다")
    void record_mapsFields() {
        PetRecord record = PetModelGenerationService.record(pet("Y", "5.5(Kg)"), new BigDecimal("5.50"));

        assertThat(record.petId()).isEqualTo("448567202600123");
        assertThat(record.species()).isEqualTo("개");
        assertThat(record.weightKg()).isEqualTo(5.5);
        assertThat(record.coatColor()).isEqualTo("갈색&흰색");
        assertThat(record.name()).isEqualTo("믹스견 · 경기도 화성시 · 5.5kg");
        assertThat(record.neutered()).isTrue();
        assertThat(PetModelGenerationService.record(pet("N", "5(Kg)"), BigDecimal.ONE).neutered()).isFalse();
        assertThat(PetModelGenerationService.record(pet("U", "5(Kg)"), BigDecimal.ONE).neutered()).isFalse();
    }

    @Test
    @DisplayName("적합도: 관리자가 넣은 체중으로 판정하고, 사진 거부 여부를 기억하고, 거부면 canGenerate=false")
    void checkFit_rejectedPhoto() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("N", "205(Kg)")));
        given(photoSource.get(PHOTO_1)).willReturn(PHOTO);
        Fit fit = new Fit("poor", "부적합", null, List.of("사람 손이 털을 가림"), List.of(), null, null, null, null, null,
                List.of("person_covers"));
        given(pipelineClient.fit(any(), eq(PHOTO))).willReturn(new FitResult("448567202600123", fit, null, null, null));

        FitCheckResponse response = service.checkFit(7L, new FitCheckRequest(1, new BigDecimal("20.5")));

        ArgumentCaptor<PetRecord> record = ArgumentCaptor.forClass(PetRecord.class);
        verify(pipelineClient).fit(record.capture(), eq(PHOTO));
        assertThat(record.getValue().weightKg()).isEqualTo(20.5);
        verify(photoSource).rememberRejection(PHOTO_1, List.of("person_covers"));
        assertThat(response.canGenerate()).isFalse();
        assertThat(response.needsForce()).isFalse();
        assertThat(response.withersM()).isNull();
        assertThat(response.photoLevel()).isNull();
    }

    @Test
    @DisplayName("생성: 자리를 잡고(PENDING) 작업을 등록한 뒤 작업 ID 를 붙인다. force=false 면 fit_stop 을 켠다")
    void requestGeneration_success() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        given(photoSource.get(PHOTO_0)).willReturn(PHOTO);
        given(photoSource.rejection(PHOTO_0)).willReturn(List.of());
        given(petModelRepository.countByCreatedAtGreaterThanEqual(START_OF_TODAY_KST)).willReturn(29L);
        given(writer.reserve(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 12L));
        given(pipelineClient.createJob(any(), eq(PHOTO), eq(true)))
                .willReturn(job("448567202600123-a5ca257e", "queued"));
        given(writer.attachJob(12L, "448567202600123-a5ca257e")).willAnswer(invocation -> {
            PetModel model = PetModel.reserve(7L, PHOTO_0, new BigDecimal("5.50"), false, 3L, NOW);
            model.attachJob("448567202600123-a5ca257e");
            return withId(model, 12L);
        });

        PetModelResponse response = service.requestGeneration(7L, new GenerateModelRequest(0, null, false), 3L);

        ArgumentCaptor<PetModel> reserved = ArgumentCaptor.forClass(PetModel.class);
        verify(writer).reserve(reserved.capture());
        assertThat(reserved.getValue().getWeightKg()).isEqualByComparingTo("5.5");
        assertThat(reserved.getValue().getRequestedBy()).isEqualTo(3L);
        assertThat(reserved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(PetModelStatus.PENDING);
        assertThat(response.jobId()).isEqualTo("448567202600123-a5ca257e");
    }

    @Test
    @DisplayName("생성: force 면 fit_stop 을 끈다")
    void requestGeneration_forceTurnsOffFitStop() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        given(photoSource.get(PHOTO_0)).willReturn(PHOTO);
        given(photoSource.rejection(PHOTO_0)).willReturn(List.of());
        given(writer.reserve(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 12L));
        given(pipelineClient.createJob(any(), eq(PHOTO), eq(false))).willReturn(job("j1", "queued"));
        given(writer.attachJob(12L, "j1")).willAnswer(invocation ->
                withId(PetModel.reserve(7L, PHOTO_0, BigDecimal.ONE, true, 3L, NOW), 12L));

        service.requestGeneration(7L, new GenerateModelRequest(0, null, true), 3L);

        verify(pipelineClient).createJob(any(), eq(PHOTO), eq(false));
    }

    @Test
    @DisplayName("생성: 파이프라인 등록이 실패하면 잡아 둔 자리를 지운다 (비용이 없었으니 하루 횟수에서도 빠진다)")
    void requestGeneration_pipelineDown_releasesReservation() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        given(photoSource.get(PHOTO_0)).willReturn(PHOTO);
        given(photoSource.rejection(PHOTO_0)).willReturn(List.of());
        given(writer.reserve(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 12L));
        given(pipelineClient.createJob(any(), any(), anyBoolean())).willThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.requestGeneration(7L, new GenerateModelRequest(0, null, false), 3L))
                .isInstanceOf(ResourceAccessException.class);
        verify(writer).delete(12L);
        verify(writer, never()).attachJob(any(), any());
    }

    @Test
    @DisplayName("생성 막기: 완성 모델이 있으면(한 번 만들면 재사용) 409, 만드는 중이면 409")
    void requestGeneration_blockedByExistingModel() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        given(petModelRepository.existsByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(true);
        assertError(() -> service.requestGeneration(7L, new GenerateModelRequest(0, null, true), 3L),
                ErrorCode.GENERATION_MODEL_EXISTS);

        given(petModelRepository.existsByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(false);
        given(petModelRepository.existsByPetIdAndStatus(7L, PetModelStatus.PENDING)).willReturn(true);
        assertError(() -> service.requestGeneration(7L, new GenerateModelRequest(0, null, false), 3L),
                ErrorCode.GENERATION_IN_PROGRESS);
        verify(pipelineClient, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("생성 막기: 체중을 알 수 없으면(205(Kg) 등) 400, 관리자가 체중을 넣으면 그 값으로 만든다")
    void requestGeneration_weightRequired() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "205(Kg)")));

        assertThatThrownBy(() -> service.requestGeneration(7L, new GenerateModelRequest(0, null, false), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("205(Kg)")
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_WEIGHT_REQUIRED);
    }

    @Test
    @DisplayName("생성 막기: 사진 번호가 범위 밖이면 400, 적합도 확인에서 거부된 사진이면 force 여도 400")
    void requestGeneration_photoChecks() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        assertError(() -> service.requestGeneration(7L, new GenerateModelRequest(2, null, false), 3L),
                ErrorCode.GENERATION_INVALID_PHOTO);

        given(photoSource.rejection(PHOTO_1)).willReturn(List.of("several_animals"));
        assertError(() -> service.requestGeneration(7L, new GenerateModelRequest(1, null, true), 3L),
                ErrorCode.GENERATION_PHOTO_REJECTED);
        verify(writer, never()).reserve(any());
    }

    @Test
    @DisplayName("생성 막기: 오늘(한국 날짜 0시부터) 30번을 다 쓰면 429")
    void requestGeneration_dailyLimit() {
        given(petRepository.findById(7L)).willReturn(Optional.of(pet("Y", "5.5(Kg)")));
        given(photoSource.rejection(PHOTO_0)).willReturn(List.of());
        given(petModelRepository.countByCreatedAtGreaterThanEqual(START_OF_TODAY_KST)).willReturn(30L);

        assertError(() -> service.requestGeneration(7L, new GenerateModelRequest(0, null, false), 3L),
                ErrorCode.GENERATION_DAILY_LIMIT);
        verify(writer, never()).reserve(any());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }

    private Pet pet(String neutered, String weight) {
        Pet pet = Pet.from(new PublicAnimalItem(jsonMapper.readTree("""
                {"desertionNo":"448567202600123","kindNm":"믹스견","orgNm":"경기도 화성시","colorCd":"갈색&흰색",
                 "sexCd":"F","neuterYn":"%s","age":"2024(년생)","weight":"%s","processState":"보호중",
                 "popfile1":"http://openapi.animal.go.kr/files/a.jpg","popfile2":"http://openapi.animal.go.kr/files/b.jpg"}
                """.formatted(neutered, weight))), NOW);
        ReflectionTestUtils.setField(pet, "id", 7L);
        return pet;
    }

    private static PetModel withId(PetModel model, long id) {
        ReflectionTestUtils.setField(model, "id", id);
        return model;
    }

    private static Job job(String jobId, String status) {
        return new Job(jobId, "448567202600123", status, null, null, null, null, null, null, null, null, null);
    }
}
