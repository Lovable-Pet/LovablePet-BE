package com.lovablepet.domain.pet.repository;

import com.lovablepet.domain.pet.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PetRepository extends JpaRepository<Pet, Long>, JpaSpecificationExecutor<Pet> {

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

    /** 입양 가능한 동물의 시도(관할 지자체 첫 단어)별 마릿수. 예: "경기도 화성시" → "경기도" */
    @Query(value = """
            select split_part(org_name, ' ', 1) as name, count(*) as count
            from pet
            where listed and notice_end < :today and org_name is not null
            group by 1
            order by 2 desc, 1
            """, nativeQuery = true)
    List<PetCountByName> countAdoptableBySido(@Param("today") LocalDate today);

    /** 입양 가능한 동물의 품종별 마릿수 */
    @Query("""
            select p.kindName as name, count(p) as count
            from Pet p
            where p.listed = true and p.noticeEnd < :today and p.kindName is not null
            group by p.kindName
            order by count(p) desc, p.kindName
            """)
    List<PetCountByName> countAdoptableByKind(@Param("today") LocalDate today);
}
