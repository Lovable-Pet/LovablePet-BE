package com.lovablepet.domain.favorite.repository;

import com.lovablepet.domain.pet.entity.Pet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 찜 쿼리를 실제 PostgreSQL 에서 확인한다 (on conflict, 동물과의 조인, 정렬).
 * PostgreSQL(pgvector)이 떠 있어야 한다 — 로컬은 docker compose up -d, CI는 서비스 컨테이너 사용.
 * 테스트마다 트랜잭션을 롤백하므로 DB 에 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class FavoriteRepositoryTest {

    private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

    @Autowired
    private FavoriteRepository favoriteRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long member;
    private Long otherMember;

    @BeforeEach
    void setUp() {
        member = insertMember("찜하는 회원");
        otherMember = insertMember("다른 회원");
    }

    @Test
    @DisplayName("찜하기: 같은 회원·동물을 두 번 넣으면 두 번째는 무시되고 한 행만 남는다")
    void insertIfAbsent_ignoresDuplicate() {
        Long pet = insertPet("TEST-0001", true);

        assertThat(favoriteRepository.insertIfAbsent(member, pet, T1)).isEqualTo(1);
        assertThat(favoriteRepository.insertIfAbsent(member, pet, T2)).isEqualTo(0);

        assertThat(countFavorites(member)).isEqualTo(1);
        assertThat(favoriteRepository.existsByMemberIdAndPetId(member, pet)).isTrue();
    }

    @Test
    @DisplayName("찜 취소: 지운 행 수를 돌려주고, 찜하지 않았으면 0")
    void deleteByMemberIdAndPetId() {
        Long pet = insertPet("TEST-0001", true);
        favoriteRepository.insertIfAbsent(member, pet, T1);

        assertThat(favoriteRepository.deleteByMemberIdAndPetId(member, pet)).isEqualTo(1);
        assertThat(favoriteRepository.deleteByMemberIdAndPetId(member, pet)).isEqualTo(0);
        assertThat(favoriteRepository.existsByMemberIdAndPetId(member, pet)).isFalse();
    }

    @Test
    @DisplayName("내 찜 목록: 최근에 찜한 순이고, 목록에서 내려간(입양·반환) 동물도 나오며, 다른 회원의 찜은 안 나온다")
    void findFavoritePets() {
        Long listedPet = insertPet("TEST-0001", true);
        Long endedPet = insertPet("TEST-0002", false);
        Long othersPet = insertPet("TEST-0003", true);
        favoriteRepository.insertIfAbsent(member, listedPet, T1);
        favoriteRepository.insertIfAbsent(member, endedPet, T2);
        favoriteRepository.insertIfAbsent(otherMember, othersPet, T1);

        Page<Pet> page = favoriteRepository.findFavoritePets(member, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Pet::getId).containsExactly(endedPet, listedPet);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("내 찜 목록: 페이지 크기만큼 자르고 전체 개수를 센다")
    void findFavoritePets_paging() {
        Long first = insertPet("TEST-0001", true);
        Long second = insertPet("TEST-0002", true);
        favoriteRepository.insertIfAbsent(member, first, T1);
        favoriteRepository.insertIfAbsent(member, second, T2);

        Page<Pet> page = favoriteRepository.findFavoritePets(member, PageRequest.of(1, 1));

        assertThat(page.getContent()).extracting(Pet::getId).containsExactly(first);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("찜 여부: 넘긴 동물 중 이 회원이 찜한 동물 ID 만 돌려준다")
    void findFavoritePetIds() {
        Long favorited = insertPet("TEST-0001", true);
        Long notFavorited = insertPet("TEST-0002", true);
        favoriteRepository.insertIfAbsent(member, favorited, T1);
        favoriteRepository.insertIfAbsent(otherMember, notFavorited, T1);

        assertThat(favoriteRepository.findFavoritePetIds(member, List.of(favorited, notFavorited)))
                .containsExactly(favorited);
    }

    private Long insertMember(String nickname) {
        return jdbc.queryForObject(
                "insert into member (nickname, status) values (?, 'ACTIVE') returning id", Long.class, nickname);
    }

    private Long insertPet(String desertionNo, boolean listed) {
        return jdbc.queryForObject("""
                insert into pet (desertion_no, process_state, listed, notice_end, photo_urls, raw, synced_at)
                values (?, ?, ?, ?, '{}', '{}'::jsonb, now())
                returning id
                """, Long.class, desertionNo, listed ? "보호중" : "종료(입양)", listed, LocalDate.of(2026, 10, 1));
    }

    private int countFavorites(Long memberId) {
        return jdbc.queryForObject("select count(*) from favorite where member_id = ?", Integer.class, memberId);
    }
}
