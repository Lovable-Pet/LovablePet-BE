package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.Optional;

/**
 * 파이프라인 작업 결과를 pet_model 에 반영한다. 콜백과 진행 중 작업 확인(PetModelJobChecker)이 같이 쓴다.
 * <p>
 * 같은 결과가 여러 번 와도(파이프라인은 콜백을 세 번까지 다시 보낸다) 한 번만 반영한다:
 * 이미 끝난 시도는 무시하고, 콜백과 확인이 동시에 반영하려 하면 @Version 으로 한쪽만 성공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetModelResultService {

    static final String GLB_FILE = "model.glb";

    private final PetModelRepository petModelRepository;
    private final PetRepository petRepository;
    private final PetModelWriter writer;
    private final PetPipelineClient pipelineClient;
    private final PetModelFiles files;
    private final Clock clock;

    public void apply(Job job) {
        if (job.jobId() == null || !job.isFinished()) {
            return;
        }
        Optional<PetModel> found = petModelRepository.findByJobId(job.jobId());
        if (found.isEmpty()) {
            // 오류로 답하면 파이프라인이 다시 보내므로 로그만 남긴다
            log.warn("모르는 작업의 결과라 무시합니다: jobId={}, status={}", job.jobId(), job.status());
            return;
        }
        PetModel model = found.get();
        if (!model.isPending()) {
            log.info("이미 반영한 작업이라 무시합니다: jobId={}, status={}", job.jobId(), model.getStatus());
            return;
        }

        String glbPath = null;
        if ("done".equals(job.status())) {
            // GLB(약 10MB)는 트랜잭션 밖에서 받는다. 같은 작업이면 같은 경로에 덮어쓰므로 중복으로 받아도 무해하다
            String desertionNo = petRepository.findById(model.getPetId()).map(Pet::getDesertionNo).orElseThrow();
            glbPath = files.saveGlb(desertionNo, job.jobId(), pipelineClient.download(job.jobId(), GLB_FILE));
        }

        boolean applied;
        try {
            applied = writer.finish(model.getId(), job, glbPath, clock.instant());
        } catch (ObjectOptimisticLockingFailureException e) {
            applied = false;
        }
        if (!applied) {
            log.info("다른 쪽(콜백·작업 확인)에서 먼저 반영했습니다: jobId={}", job.jobId());
            return;
        }
        log.info("AR 생성 결과 반영: jobId={}, status={}, glb={}", job.jobId(), job.status(), glbPath);

        if (glbPath != null) {
            deleteFromPipeline(job.jobId());
        }
    }

    // GLB 를 옮겨 왔으니 파이프라인 쪽 작업 파일은 지운다. 실패한 작업은 원인을 볼 수 있게 남겨 둔다
    private void deleteFromPipeline(String jobId) {
        try {
            pipelineClient.delete(jobId);
        } catch (RestClientException e) {
            log.warn("파이프라인 작업 파일을 지우지 못했습니다: jobId={}, {}", jobId, e.getClass().getSimpleName());
        }
    }
}
