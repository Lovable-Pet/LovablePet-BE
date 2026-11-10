package com.lovablepet.domain.favorite.repository;

import com.lovablepet.domain.favorite.entity.Favorite;
import com.lovablepet.domain.pet.entity.Pet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * 찜하기. 이미 찜했으면 아무것도 하지 않는다 (버튼을 두 번 눌러도, 두 요청이 동시에 와도 한 행만 남는다).
     *
     * @return 새로 넣었으면 1, 이미 있었으면 0
     */
    @Modifying
    @Query(value = """
            insert into favorite (member_id, pet_id, created_at)
            values (:memberId, :petId, :createdAt)
            on conflict (member_id, pet_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("petId") Long petId,
                       @Param("createdAt") Instant createdAt);

    /** 찜 취소. 찜하지 않았으면 0 */
    @Modifying
    @Query("delete from Favorite f where f.memberId = :memberId and f.petId = :petId")
    int deleteByMemberIdAndPetId(@Param("memberId") Long memberId, @Param("petId") Long petId);

    /** 내 찜 목록 (최근에 찜한 순). 입양·반환으로 목록에서 내려간 동물도 나온다 */
    @Query(value = """
            select p from Favorite f join Pet p on p.id = f.petId
            where f.memberId = :memberId
            order by f.createdAt desc, f.id desc
            """,
            countQuery = "select count(f) from Favorite f where f.memberId = :memberId")
    Page<Pet> findFavoritePets(@Param("memberId") Long memberId, Pageable pageable);

    /** 목록 한 페이지의 동물 중 이 회원이 찜한 동물 ID */
    @Query("select f.petId from Favorite f where f.memberId = :memberId and f.petId in :petIds")
    Set<Long> findFavoritePetIds(@Param("memberId") Long memberId, @Param("petIds") Collection<Long> petIds);

    boolean existsByMemberIdAndPetId(Long memberId, Long petId);
}
