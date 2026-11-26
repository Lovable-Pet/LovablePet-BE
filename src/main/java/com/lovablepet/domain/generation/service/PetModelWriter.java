package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * pet_model 쓰기. 파이프라인 호출(수 초~수십 초) 동안 DB 트랜잭션을 잡지 않도록 쓰기를 짧은 트랜잭션으로 나눈다.
 */
@Component
@RequiredArgsConstructor
public class PetModelWriter {

    private final PetModelRepository petModelRepository;

    /** 같은 동물에 만드는 중인 시도가 이미 있으면 유니크 인덱스(uk_pet_model_pending)에 걸린다 */
    @Transactional
    public PetModel reserve(PetModel model) {
        try {
            return petModelRepository.saveAndFlush(model);
        } catch (DataIntegrityViolationException e) {
            throw new GenerationException(ErrorCode.GENERATION_IN_PROGRESS);
        }
    }

    @Transactional
    public PetModel attachJob(Long id, String jobId) {
        PetModel model = petModelRepository.findById(id).orElseThrow();
        model.attachJob(jobId);
        return model;
    }

    @Transactional
    public void delete(Long id) {
        petModelRepository.deleteById(id);
    }

    /**
     * 만드는 중(PENDING)일 때만 파이프라인 결과를 반영한다. 이미 끝났으면 {@link Finish#SKIPPED}.
     * done 인데 같은 동물에 완성 모델이 이미 있으면(다시 만들기) 옛 모델을 REPLACED 로 바꾸고, 지울 옛 파일 경로를 돌려준다.
     * 다른 트랜잭션이 동시에 반영하면 ObjectOptimisticLockingFailureException(@Version)이 난다.
     */
    @Transactional
    public Finish finish(Long id, Job job, String glbPath, String usdzPath, Instant now) {
        PetModel model = petModelRepository.findById(id).orElse(null);
        if (model == null || !model.isPending()) {
            return Finish.SKIPPED;
        }
        if (!"done".equals(job.status())) {
            model.finishWithoutModel(job, now); // 실패하면 옛 완성 모델을 그대로 쓴다
            return Finish.applied(List.of());
        }
        List<String> replacedFiles = petModelRepository.findByPetIdAndStatus(model.getPetId(), PetModelStatus.DONE)
                .map(old -> {
                    List<String> paths = old.replace();
                    // 완성은 동물마다 하나(uk_pet_model_done)라, 옛 모델을 먼저 DB 에 내보낸 뒤 새 모델을 DONE 으로 바꾼다
                    petModelRepository.flush();
                    return paths;
                })
                .orElse(List.of());
        model.complete(job, glbPath, usdzPath, now);
        return Finish.applied(replacedFiles);
    }

    /**
     * finish 결과. replacedFiles 는 이번에 교체된 옛 완성 모델의 파일(GLB·USDZ·미리보기) 경로로, 커밋한 뒤에 지운다.
     */
    public record Finish(boolean applied, List<String> replacedFiles) {

        public static final Finish SKIPPED = new Finish(false, List.of());

        public static Finish applied(List<String> replacedFiles) {
            return new Finish(true, replacedFiles);
        }
    }

    /** 완성 모델에 미리보기 그림 경로를 붙인다. 다시 찍었으면 이전 그림 경로를 돌려준다 (없으면 null) */
    @Transactional
    public String attachPreview(Long id, String previewPath) {
        PetModel model = petModelRepository.findById(id).orElseThrow();
        return model.attachPreview(previewPath);
    }

    /** 파이프라인에서 결과를 받을 수 없을 때 FAILED 로. 만드는 중이 아니면 false */
    @Transactional
    public boolean fail(Long id, String error, Instant now) {
        PetModel model = petModelRepository.findById(id).orElse(null);
        if (model == null || !model.isPending()) {
            return false;
        }
        model.fail(error, now);
        return true;
    }
}
