package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineClient;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 파이프라인 작업 완료 콜백 처리.
 * done 이면 GLB 를 받아 우리 저장소(pet-pipeline.model-dir)에 보관한다. 파이프라인 주소는 앱에 노출하지 않으므로
 * 앱/AR 페이지는 Spring 이 서빙하는 GLB 를 받는다.
 */
@Slf4j
@Service
public class PetModelCallbackService {

    private static final String GLB_FILE = "model.glb";

    private final PetPipelineClient petPipelineClient;
    private final Path modelDir;

    public PetModelCallbackService(PetPipelineClient petPipelineClient, PetPipelineProperties properties) {
        this.petPipelineClient = petPipelineClient;
        this.modelDir = Path.of(properties.modelDir()).toAbsolutePath().normalize();
    }

    public void handle(Job job) throws IOException {
        log.info("파이프라인 콜백: jobId={}, petId={}, status={}", job.jobId(), job.petId(), job.status());
        switch (job.status()) {
            case "done" -> saveModel(job);
            case "photo_rejected" -> {
                // TODO 펫 상태 PHOTO_REJECTED, job.reasons()(한국어)를 보여주고 다른 사진 요청
            }
            case "unsuitable" -> {
                // TODO 펫 상태 UNSUITABLE, job.fit().reasons() 표시
            }
            default -> {
                // "failed": TODO 펫 상태 FAILED, 재시도 허용
                log.warn("파이프라인 작업 실패: jobId={}, error={}", job.jobId(), job.error());
            }
        }
    }

    private void saveModel(Job job) throws IOException {
        Path target = resolveModelPath(job.petId());
        byte[] glb = petPipelineClient.download(job.jobId(), GLB_FILE);
        Files.createDirectories(modelDir);
        Files.write(target, glb);
        log.info("GLB 저장: petId={}, {} bytes", job.petId(), glb.length);
        // TODO 펫 행에 저장: 상태 READY, GLB 경로/URL, job.fit(), job.size(),
        //      job.behavior()(탭 애니메이션, 재생 속도) — AR 페이지에서 쓴다
    }

    /** 관리번호로 model-dir 밖의 경로("../" 등)를 만들 수 없게 한다. */
    private Path resolveModelPath(String petId) {
        if (petId == null || petId.isBlank()) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_PET_ID);
        }
        Path target = modelDir.resolve(petId + ".glb").normalize();
        if (!modelDir.equals(target.getParent())) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_PET_ID);
        }
        return target;
    }
}
