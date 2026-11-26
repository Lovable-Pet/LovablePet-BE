package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 완성된 AR 모델의 미리보기 그림(GET /api/pets/{id} 의 model.previewUrl)을 찍어 GLB 옆에 저장한다.
 * <p>
 * 한 장에 수 초~십수 초 걸리고 크롬이 수백 MB 를 쓰므로, 요청을 한 줄로 세워 뒤에서 한 장씩 찍는다(콜백 응답을 늦추지 않는다).
 * 그림은 덤이라 찍지 못해도 모델은 그대로 쓰고(previewUrl 이 null 이면 앱이 GLB 를 직접 그린다) 로그만 남긴다.
 * 서버가 꺼지면 줄에 남은 요청은 사라지는데, 서버가 뜰 때마다 그림이 없는 모델을 다시 줄 세워 채운다.
 */
@Slf4j
@Service
public class PetModelPreviewService {

    private final PetModelRepository petModelRepository;
    private final PetRepository petRepository;
    private final PetModelWriter writer;
    private final PetModelFiles files;
    private final PetModelPreviewRenderer renderer;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "model-preview");
        thread.setDaemon(true);
        return thread;
    });

    public PetModelPreviewService(PetModelRepository petModelRepository, PetRepository petRepository, PetModelWriter writer,
                                  PetModelFiles files, PetModelPreviewRenderer renderer) {
        this.petModelRepository = petModelRepository;
        this.petRepository = petRepository;
        this.writer = writer;
        this.files = files;
        this.renderer = renderer;
    }

    /** 방금 완성된 모델의 그림을 뒤에서 찍는다 */
    public void request(Long modelId) {
        worker.execute(() -> create(modelId, false));
    }

    /**
     * 완성 모델들의 그림을 뒤에서 찍는다. all 이 false 면 그림이 없는 모델만, true 면 이미 있는 모델도 다시 찍는다
     * (프론트의 3D 화면 설정을 바꿨을 때). 줄 세운 모델 수를 돌려준다.
     */
    public int requestAll(boolean all) {
        List<Long> ids = petModelRepository.findIdsForPreview(PetModelStatus.DONE, all);
        ids.forEach(id -> worker.execute(() -> create(id, all)));
        return ids.size();
    }

    /** 이미 만든 모델 채우기: 서버가 뜰 때마다 그림이 없는 모델을 줄 세운다 (없으면 아무것도 하지 않는다) */
    @EventListener(ApplicationReadyEvent.class)
    public void fillMissingOnStartup() {
        try {
            int queued = requestAll(false);
            if (queued > 0) {
                log.info("미리보기 그림이 없는 AR 모델 {}개를 찍습니다", queued);
            }
        } catch (RuntimeException e) {
            log.warn("미리보기 그림이 없는 모델을 찾지 못했습니다: {}", e.toString());
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    // worker 스레드에서 돈다. 예외를 밖으로 던지면 아무도 받지 않으므로 여기서 로그로 끝낸다
    void create(Long modelId, boolean again) {
        try {
            PetModel model = petModelRepository.findById(modelId).orElse(null);
            if (model == null || model.getStatus() != PetModelStatus.DONE || (model.getPreviewPath() != null && !again)) {
                return;
            }
            Path glb = files.stored(model.getGlbPath()).orElse(null);
            if (glb == null) {
                log.warn("GLB 파일이 없어 미리보기 그림을 찍지 못했습니다: modelId={}, glb={}", modelId, model.getGlbPath());
                return;
            }
            String desertionNo = petRepository.findById(model.getPetId()).map(Pet::getDesertionNo).orElseThrow();

            String path = files.savePreview(desertionNo, model.getJobId(), renderer.render(glb));
            String previous = writer.attachPreview(modelId, path);
            if (previous != null && !previous.equals(path)) {
                files.delete(previous);
            }
            log.info("미리보기 그림 저장: modelId={}, {}", modelId, path);
        } catch (RuntimeException e) {
            log.warn("미리보기 그림을 찍지 못했습니다(앱은 GLB 를 직접 그린다): modelId={}", modelId, e);
        }
    }
}
