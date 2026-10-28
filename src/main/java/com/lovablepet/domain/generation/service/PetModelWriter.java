package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
     * 만드는 중(PENDING)일 때만 파이프라인 결과를 반영한다. 이미 끝났으면 false.
     * 다른 트랜잭션이 동시에 반영하면 커밋할 때 ObjectOptimisticLockingFailureException(@Version)이 난다.
     */
    @Transactional
    public boolean finish(Long id, Job job, String glbPath, Instant now) {
        PetModel model = petModelRepository.findById(id).orElse(null);
        if (model == null || !model.isPending()) {
            return false;
        }
        if ("done".equals(job.status())) {
            model.complete(job, glbPath, now);
        } else {
            model.finishWithoutModel(job, now);
        }
        return true;
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
