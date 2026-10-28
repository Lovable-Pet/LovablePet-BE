package com.lovablepet.domain.generation.repository;

import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PetModelRepository extends JpaRepository<PetModel, Long> {

    boolean existsByPetIdAndStatus(Long petId, PetModelStatus status);

    /** 하루 생성 횟수 (from = 오늘 0시, 한국 시각) */
    long countByCreatedAtGreaterThanEqual(Instant from);

    Optional<PetModel> findByJobId(String jobId);

    Optional<PetModel> findFirstByPetIdOrderByCreatedAtDesc(Long petId);

    /** 진행 중 작업 확인: 만든 지 오래된 PENDING */
    List<PetModel> findAllByStatusAndCreatedAtBefore(PetModelStatus status, Instant before);

    List<PetModel> findTop100ByStatusOrderByCreatedAtDesc(PetModelStatus status);

    List<PetModel> findTop100ByOrderByCreatedAtDesc();
}
