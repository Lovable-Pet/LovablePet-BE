package com.lovablepet.domain.generation.repository;

import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PetModelRepository extends JpaRepository<PetModel, Long> {

    boolean existsByPetIdAndStatus(Long petId, PetModelStatus status);

    /** 완성 모델 (동물마다 하나뿐) */
    Optional<PetModel> findByPetIdAndStatus(Long petId, PetModelStatus status);

    /** 목록 한 페이지의 동물 중 이 상태의 모델이 있는 동물 ID */
    @Query("select m.petId from PetModel m where m.petId in :petIds and m.status = :status")
    Set<Long> findPetIdsByStatus(@Param("petIds") Collection<Long> petIds, @Param("status") PetModelStatus status);

    /** 하루 생성 횟수 (from = 오늘 0시, 한국 시각) */
    long countByCreatedAtGreaterThanEqual(Instant from);

    Optional<PetModel> findByJobId(String jobId);

    Optional<PetModel> findFirstByPetIdOrderByCreatedAtDesc(Long petId);

    /** 진행 중 작업 확인: 만든 지 오래된 PENDING */
    List<PetModel> findAllByStatusAndCreatedAtBefore(PetModelStatus status, Instant before);

    List<PetModel> findTop100ByStatusOrderByCreatedAtDesc(PetModelStatus status);

    List<PetModel> findTop100ByOrderByCreatedAtDesc();

    /** 미리보기 그림을 찍을 모델: GLB 가 있고, all 이 false 면 그림이 아직 없는 것만 */
    default List<Long> findIdsForPreview(PetModelStatus status, boolean all) {
        return all ? findIdsWithGlb(status) : findIdsWithGlbWithoutPreview(status);
    }

    @Query("select m.id from PetModel m where m.status = :status and m.glbPath is not null order by m.id")
    List<Long> findIdsWithGlb(@Param("status") PetModelStatus status);

    @Query("select m.id from PetModel m where m.status = :status and m.glbPath is not null and m.previewPath is null order by m.id")
    List<Long> findIdsWithGlbWithoutPreview(@Param("status") PetModelStatus status);
}
