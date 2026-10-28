package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.FitResult;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.PetRecord;
import com.lovablepet.domain.generation.dto.FitCheckRequest;
import com.lovablepet.domain.generation.dto.FitCheckResponse;
import com.lovablepet.domain.generation.dto.GenerateModelRequest;
import com.lovablepet.domain.generation.dto.PetModelListResponse;
import com.lovablepet.domain.generation.dto.PetModelResponse;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 AR(3D 모델) 생성: 적합도 확인과 생성 등록. 결과는 콜백(PetModelCallbackController)으로 온다.
 * <ul>
 *   <li>한 번 만든 모델은 계속 쓴다 — 완성 모델이 있으면 다시 만들지 않는다</li>
 *   <li>하루(한국 날짜) 생성 시도 상한 (pet-pipeline.daily-limit). 실패한 시도도 비용이 들어 센다</li>
 *   <li>사진이 거부되면 force 로도 만들 수 없다 (파이프라인 작업도 photo_rejected 로 끝난다)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PetModelGenerationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String SPECIES_DOG = "개";

    private final PetRepository petRepository;
    private final PetModelRepository petModelRepository;
    private final PetModelWriter writer;
    private final PetPipelineClient pipelineClient;
    private final PetPhotoSource photoSource;
    private final PetModelFiles files;
    private final PetPipelineProperties properties;
    private final Clock clock;

    /** 5~20초, 약 $0.02. 받은 사진과 판정(사진 거부 여부)은 30분 동안 기억해 생성 때 쓴다 */
    public FitCheckResponse checkFit(Long petId, FitCheckRequest request) {
        Pet pet = findPet(petId);
        BigDecimal weightKg = weight(pet);
        String photoUrl = photoUrl(pet, request.photoIndex());

        FitResult result = pipelineClient.fit(record(pet, weightKg), photoSource.get(photoUrl));
        photoSource.rememberRejection(photoUrl, result.fit() == null ? List.of() : result.fit().photoRejected());
        return FitCheckResponse.of(result, weightKg, photoUrl);
    }

    public PetModelResponse requestGeneration(Long petId, GenerateModelRequest request, Long adminId) {
        Pet pet = findPet(petId);
        if (petModelRepository.existsByPetIdAndStatus(petId, PetModelStatus.DONE)) {
            throw new GenerationException(ErrorCode.GENERATION_MODEL_EXISTS);
        }
        if (petModelRepository.existsByPetIdAndStatus(petId, PetModelStatus.PENDING)) {
            throw new GenerationException(ErrorCode.GENERATION_IN_PROGRESS);
        }
        BigDecimal weightKg = weight(pet);
        String photoUrl = photoUrl(pet, request.photoIndex());
        if (!photoSource.rejection(photoUrl).isEmpty()) {
            throw new GenerationException(ErrorCode.GENERATION_PHOTO_REJECTED);
        }
        if (todayCount() >= properties.dailyLimit()) {
            throw new GenerationException(ErrorCode.GENERATION_DAILY_LIMIT,
                    "오늘 AR 생성 횟수(" + properties.dailyLimit() + "번)를 다 썼습니다. 내일 다시 시도해 주세요.");
        }

        // 파이프라인 호출 동안 DB 트랜잭션을 잡지 않도록, 자리를 먼저 잡고(PENDING) 작업을 등록한 뒤 작업 ID 를 붙인다
        PetModel reserved = writer.reserve(
                PetModel.reserve(petId, photoUrl, weightKg, request.force(), adminId, clock.instant()));
        Job job;
        try {
            job = pipelineClient.createJob(record(pet, weightKg), photoSource.get(photoUrl), !request.force());
        } catch (RuntimeException e) {
            // 작업이 등록되지 않았으니 비용도 없다 → 자리를 지워 하루 횟수에서도 뺀다
            writer.delete(reserved.getId());
            throw e;
        }
        return toResponse(writer.attachJob(reserved.getId(), job.jobId()));
    }

    public Optional<PetModelResponse> latest(Long petId) {
        return petModelRepository.findFirstByPetIdOrderByCreatedAtDesc(petId).map(this::toResponse);
    }

    public PetModelListResponse list(PetModelStatus status) {
        List<PetModel> models = status == null
                ? petModelRepository.findTop100ByOrderByCreatedAtDesc()
                : petModelRepository.findTop100ByStatusOrderByCreatedAtDesc(status);
        return new PetModelListResponse(models.stream().map(this::toResponse).toList(), todayCount(),
                properties.dailyLimit());
    }

    /** 파이프라인에 보내는 보호소 기록. pet_id 는 유기번호(숫자라 파일 이름·주소에 안전) */
    static PetRecord record(Pet pet, BigDecimal weightKg) {
        return new PetRecord(
                pet.getDesertionNo(),
                SPECIES_DOG,
                weightKg.doubleValue(),
                nullToEmpty(pet.getColor()),
                pet.displayName(),
                nullToEmpty(pet.getSex()),
                "Y".equals(pet.getNeutered()), // 파이프라인은 true/false 만 받는다: Y → true, N·U(미상) → false
                nullToEmpty(pet.getAgeText()));
    }

    private PetModelResponse toResponse(PetModel model) {
        return PetModelResponse.from(model, files.publicUrl(model.getGlbPath()));
    }

    private long todayCount() {
        Instant startOfToday = LocalDate.now(clock.withZone(KST)).atStartOfDay(KST).toInstant();
        return petModelRepository.countByCreatedAtGreaterThanEqual(startOfToday);
    }

    private Pet findPet(Long petId) {
        return petRepository.findById(petId).orElseThrow(() -> new PetException(ErrorCode.PET_NOT_FOUND));
    }

    /**
     * 공고 체중. 체중이 모델의 실제 크기(체고)를 정하므로, 숫자로 바꾸지 못한 공고(0(Kg), 205(Kg) 등)는 만들 수 없다.
     * 우리는 공고를 중계할 뿐이라 관리자도 실제 체중을 모른다 — 추측한 체중으로 만들면 "실제 크기"가 틀린다.
     */
    private static BigDecimal weight(Pet pet) {
        if (pet.getWeightKg() == null) {
            throw new GenerationException(ErrorCode.GENERATION_WEIGHT_UNKNOWN,
                    "공고 체중(" + pet.getWeightText() + ")을 알 수 없어 실제 크기로 만들 수 없습니다.");
        }
        return pet.getWeightKg();
    }

    private static String photoUrl(Pet pet, int photoIndex) {
        List<String> photos = pet.getPhotoUrls();
        if (photoIndex < 0 || photoIndex >= photos.size()) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_PHOTO,
                    "사진 번호가 올바르지 않습니다 (0~" + (photos.size() - 1) + ").");
        }
        return photos.get(photoIndex);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
