package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.generation.service.PetModelWriter.Finish;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다시 만든 모델의 교체를 실제 PostgreSQL 에서 확인한다: 완성은 동물마다 하나(uk_pet_model_done)인 채로
 * 옛 모델을 REPLACED(V13 의 status CHECK)로 바꾸고 새 모델을 DONE 으로 바꿀 수 있는지.
 * PostgreSQL(pgvector)이 떠 있어야 한다 — 로컬은 docker compose up -d, CI는 서비스 컨테이너 사용.
 * 테스트마다 트랜잭션을 롤백하므로 DB 에 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class PetModelReplaceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T03:03:00Z");

    @Autowired
    private PetModelWriter writer;
    @Autowired
    private PetModelRepository petModelRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("다시 만든 모델 완성: 옛 모델은 REPLACED(파일 경로 비움), 새 모델은 DONE 이 되고 유니크 인덱스에 걸리지 않는다")
    void replacesOldModel() {
        Long pet = insertPet("TEST-0001");
        Long old = insertModel(pet, "DONE", "TEST-0001-old", "TEST-0001/TEST-0001-old.glb");
        Long fresh = insertModel(pet, "PENDING", "TEST-0001-new", null);

        Finish finish = writer.finish(fresh, job("TEST-0001-new", "done"), "TEST-0001/TEST-0001-new.glb", null, NOW);
        petModelRepository.flush();

        assertThat(finish.replacedFiles()).containsExactly("TEST-0001/TEST-0001-old.glb");
        assertThat(row(old)).containsEntry("status", "REPLACED").containsEntry("glb_path", null);
        assertThat(row(fresh)).containsEntry("status", "DONE").containsEntry("glb_path", "TEST-0001/TEST-0001-new.glb");
        assertThat(petModelRepository.findByPetIdAndStatus(pet, PetModelStatus.DONE))
                .hasValueSatisfying(model -> assertThat(model.getId()).isEqualTo(fresh));
    }

    @Test
    @DisplayName("다시 만들기가 실패하면 옛 모델은 DONE 그대로")
    void failedKeepsOldModel() {
        Long pet = insertPet("TEST-0001");
        Long old = insertModel(pet, "DONE", "TEST-0001-old", "TEST-0001/TEST-0001-old.glb");
        Long fresh = insertModel(pet, "PENDING", "TEST-0001-new", null);

        writer.finish(fresh, job("TEST-0001-new", "failed"), null, null, NOW);
        petModelRepository.flush();

        assertThat(row(old)).containsEntry("status", "DONE").containsEntry("glb_path", "TEST-0001/TEST-0001-old.glb");
        assertThat(row(fresh)).containsEntry("status", "FAILED");
    }

    private Long insertPet(String desertionNo) {
        return jdbc.queryForObject("""
                insert into pet (desertion_no, process_state, listed, notice_end, photo_urls, raw, synced_at)
                values (?, '보호중', true, ?, '{}', '{}'::jsonb, now())
                returning id
                """, Long.class, desertionNo, LocalDate.of(2026, 10, 1));
    }

    private Long insertModel(Long petId, String status, String jobId, String glbPath) {
        return jdbc.queryForObject("""
                insert into pet_model (pet_id, job_id, status, photo_url, weight_kg, forced, reasons, glb_path, version, created_at)
                values (?, ?, ?, 'https://a/1.jpg', 5.5, false, '{}', ?, 0, now())
                returning id
                """, Long.class, petId, jobId, status, glbPath);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("select status, glb_path from pet_model where id = ?", id);
    }

    private static Job job(String jobId, String status) {
        return new Job(jobId, "TEST-0001", status, null, null, null, null, null, null, null, null, 0.006);
    }
}
