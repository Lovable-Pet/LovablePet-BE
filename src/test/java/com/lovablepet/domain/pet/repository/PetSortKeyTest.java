package com.lovablepet.domain.pet.repository;

import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.dto.PetSearchCondition;
import com.lovablepet.domain.pet.dto.PetSort;
import com.lovablepet.domain.pet.dto.PetSummaryResponse;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.service.PetQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목록 기본순(MIXED) 정렬 키를 실제 PostgreSQL 에서 확인한다 (컬럼 기본값, 다시 섞기, 페이지 넘김).
 * PostgreSQL(pgvector)이 떠 있어야 한다 — 로컬은 docker compose up -d, CI는 서비스 컨테이너 사용.
 * 테스트마다 트랜잭션을 롤백하므로 DB 에 데이터가 남지 않는다. 다른 데이터와 섞이지 않게 테스트용 지역으로만 조회한다.
 */
@SpringBootTest
@Transactional
class PetSortKeyTest {

    private static final String REGION = "테스트특별시 정렬구";

    @Autowired
    private PetRepository petRepository;
    @Autowired
    private PetQueryService petQueryService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("JPA 로 넣어도 정렬 키는 DB 기본값(무작위)으로 정해진다")
    void sortKey_isSetByDatabaseOnJpaInsert() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        List<Pet> pets = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> Pet.from(new PublicAnimalItem(jsonMapper.readTree(
                        "{\"desertionNo\":\"TEST-SORT-JPA-" + i + "\",\"processState\":\"보호중\"}")), Instant.now()))
                .toList();
        petRepository.saveAllAndFlush(pets);

        // 엔티티가 0 을 넣었다면 두 값이 같다 (무작위 두 값이 같을 확률은 21억 분의 1)
        assertThat(jdbc.queryForObject(
                "select count(distinct sort_key) from pet where desertion_no like 'TEST-SORT-JPA-%'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("다시 섞기는 목록에 있는 동물의 정렬 키만 바꾼다")
    void reshuffle_changesListedOnly() {
        List<Long> listed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            listed.add(insertPet("TEST-SORT-L" + i, true));
        }
        Long unlisted = insertPet("TEST-SORT-U", false);
        // 다시 섞은 값이 우연히 그대로일 수 없도록 범위 밖 값으로 맞춰 둔다
        jdbc.update("update pet set sort_key = -1 where desertion_no like 'TEST-SORT-%'");

        assertThat(petRepository.reshuffleSortKeys()).isGreaterThanOrEqualTo(listed.size());

        Map<Long, Integer> keys = sortKeys();
        assertThat(listed).allSatisfy(id -> assertThat(keys.get(id)).isNotNegative());
        assertThat(keys.get(unlisted)).isEqualTo(-1);
    }

    @Test
    @DisplayName("기본순: 정렬 키 → id 순으로 나오고, 페이지를 넘겨도 겹치거나 빠지는 동물이 없다")
    void mixedSort_isStableAcrossPages() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ids.add(insertPet("TEST-SORT-P" + i, true));
        }
        // 정렬 키가 같은 동물도 id 로 순서가 정해지는지 보려고 절반(짝수 번째)을 같은 값으로 맞춘다
        jdbc.update("update pet set sort_key = 7 where desertion_no like 'TEST-SORT-P%' and id % 2 = 0");

        List<Long> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            PetPageResponse response = petQueryService.search(
                    new PetSearchCondition(REGION, null, null, null, null, PetSort.MIXED), page, 10, null);
            paged.addAll(response.items().stream().map(PetSummaryResponse::id).toList());
        }

        Map<Long, Integer> keys = sortKeys();
        List<Long> expected = ids.stream()
                .sorted(Comparator.<Long, Integer>comparing(keys::get).thenComparing(Comparator.naturalOrder()))
                .toList();
        assertThat(paged).containsExactlyElementsOf(expected);
    }

    private Long insertPet(String desertionNo, boolean listed) {
        return jdbc.queryForObject("""
                insert into pet (desertion_no, process_state, listed, notice_end, org_name, photo_urls, raw, synced_at)
                values (?, ?, ?, current_date - 1, ?, '{}', '{}'::jsonb, now())
                returning id
                """, Long.class, desertionNo, listed ? "보호중" : "종료(입양)", listed, REGION);
    }

    private Map<Long, Integer> sortKeys() {
        Map<Long, Integer> keys = new HashMap<>();
        jdbc.query("select id, sort_key from pet where desertion_no like 'TEST-SORT-%'",
                (RowCallbackHandler) rs -> keys.put(rs.getLong("id"), rs.getInt("sort_key")));
        return keys;
    }
}
