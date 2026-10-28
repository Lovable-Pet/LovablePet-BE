package com.lovablepet.domain.pet.repository;

import com.lovablepet.domain.pet.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findAllByDesertionNoIn(Collection<String> desertionNos);

    /** 받아 둔 원본 중 가장 최근 수정 시각. 비어 있으면 null. */
    @Query("select max(p.sourceUpdatedAt) from Pet p")
    Instant findLatestSourceUpdatedAt();

    /**
     * 이번 동기화에서 받지 못한(공공데이터 목록에서 빠진) 동물을 목록에서 내린다.
     * 벌크 update 라 updated_at 은 바뀌지 않는다.
     */
    @Modifying
    @Query("update Pet p set p.listed = false where p.listed = true and p.syncedAt < :syncStartedAt")
    int unlistNotSyncedSince(@Param("syncStartedAt") Instant syncStartedAt);
}
