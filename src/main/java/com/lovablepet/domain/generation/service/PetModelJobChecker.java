package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 콜백으로 결과를 받지 못한 작업을 파이프라인에 직접 물어본다.
 * <p>
 * 콜백만으로는 못 잡는 경우가 있다:
 * <ul>
 *   <li>파이프라인이 재시작·재배포되면 끝나지 않은 작업을 failed 로 바꾸기만 하고 콜백을 보내지 않는다</li>
 *   <li>결과가 나올 때 Spring 이 꺼져 있으면(배포 중 30~60초) 콜백 재시도 3번(약 12초)이 모두 실패한다</li>
 * </ul>
 * 관리자 화면의 조회는 DB 만 읽으므로 이 확인을 대신하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetModelJobChecker {

    /** 이만큼 지나도 만드는 중이면 물어본다 (한 마리 1.5~4분) */
    static final Duration CHECK_AFTER = Duration.ofMinutes(10);
    /** 파이프라인은 한 번에 한 마리씩 만들고 나머지는 줄을 서므로 넉넉하게 둔다 */
    static final Duration GIVE_UP_AFTER = Duration.ofHours(6);

    private final PetModelRepository petModelRepository;
    private final PetModelWriter writer;
    private final PetPipelineClient pipelineClient;
    private final PetModelResultService resultService;
    private final Clock clock;

    /** 5분마다. 해당하는 행이 없으면 아무것도 하지 않는다 */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT5M")
    public void checkPendingJobs() {
        Instant now = clock.instant();
        for (PetModel model : petModelRepository.findAllByStatusAndCreatedAtBefore(PetModelStatus.PENDING, now.minus(CHECK_AFTER))) {
            try {
                check(model, now);
            } catch (RuntimeException e) {
                log.warn("AR 생성 작업을 확인하지 못했습니다: id={}, jobId={}, {}", model.getId(), model.getJobId(), e.toString());
            }
        }
    }

    void check(PetModel model, Instant now) {
        if (model.getJobId() == null) {
            // 작업 등록 요청(최대 90초) 중에 서버가 꺼져 작업 ID 를 받지 못했다
            fail(model, "작업을 등록하는 중에 중단됐습니다", now);
            return;
        }
        Job job;
        try {
            job = pipelineClient.getJob(model.getJobId());
        } catch (HttpClientErrorException.NotFound e) {
            fail(model, "파이프라인에 작업이 없습니다 (파이프라인을 새로 배포했을 수 있음)", now);
            return;
        } catch (RestClientException e) {
            log.info("파이프라인에 연결하지 못해 다음에 다시 확인합니다: jobId={}", model.getJobId());
            return;
        }
        if (job.isFinished()) {
            resultService.apply(job);
        } else if (model.getCreatedAt().isBefore(now.minus(GIVE_UP_AFTER))) {
            fail(model, "6시간이 지나도 끝나지 않았습니다 (파이프라인 상태: " + job.status() + ")", now);
        }
    }

    private void fail(PetModel model, String error, Instant now) {
        try {
            if (writer.fail(model.getId(), error, now)) {
                log.warn("AR 생성 실패로 바꿨습니다: id={}, jobId={}, {}", model.getId(), model.getJobId(), error);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("다른 쪽(콜백)에서 먼저 반영했습니다: id={}", model.getId());
        }
    }
}
