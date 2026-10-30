package com.lovablepet.domain.pet.entity;

import com.lovablepet.domain.pet.client.PublicAnimalItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PetTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("화면 이름: 품종 · 관할 지자체 · 체중, 체중은 뒤의 0을 뺀다")
    void displayName() {
        assertThat(pet("{\"kindNm\":\"믹스견\",\"orgNm\":\"경기도 화성시\",\"weight\":\"5.0(Kg)\"}").displayName())
                .isEqualTo("믹스견 · 경기도 화성시 · 5kg");
        assertThat(pet("{\"kindNm\":\"푸들\",\"orgNm\":\"서울특별시 강남구\",\"weight\":\"6~7(Kg)\"}").displayName())
                .isEqualTo("푸들 · 서울특별시 강남구 · 6.5kg");
        assertThat(pet("{\"kindNm\":\"믹스견\",\"orgNm\":\"제주특별자치도\",\"weight\":\"0.15(Kg)\"}").displayName())
                .isEqualTo("믹스견 · 제주특별자치도 · 0.15kg");
    }

    @Test
    @DisplayName("화면 이름: 값이 없는 부분은 뺀다 (체중을 바꾸지 못한 경우 등)")
    void displayName_missingParts() {
        assertThat(pet("{\"kindNm\":\"믹스견\",\"orgNm\":\"경기도 화성시\",\"weight\":\"205(Kg)\"}").displayName())
                .isEqualTo("믹스견 · 경기도 화성시");
        assertThat(pet("{\"kindNm\":\"믹스견\"}").displayName()).isEqualTo("믹스견");
    }

    @Test
    @DisplayName("입양 가능: 목록에 있고 공고 종료일이 오늘 전이어야 한다 (오늘 끝나는 공고는 아직 공고중)")
    void isAdoptable() {
        assertThat(pet("{\"processState\":\"보호중\",\"noticeEdt\":\"20261028\"}").isAdoptable(TODAY)).isTrue();
        assertThat(pet("{\"processState\":\"보호중\",\"noticeEdt\":\"20261029\"}").isAdoptable(TODAY)).isFalse();
        assertThat(pet("{\"processState\":\"종료(입양)\",\"noticeEdt\":\"20261001\"}").isAdoptable(TODAY)).isFalse();
        assertThat(pet("{\"processState\":\"보호중\"}").isAdoptable(TODAY)).isFalse();
    }

    private Pet pet(String json) {
        return Pet.from(new PublicAnimalItem(jsonMapper.readTree(json)), Instant.parse("2026-10-29T00:00:00Z"));
    }
}
