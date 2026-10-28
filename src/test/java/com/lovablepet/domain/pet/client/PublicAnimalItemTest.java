package com.lovablepet.domain.pet.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 응답에서 고른 3마리(public-animal/protect-page.json, 담당자 이름·전화번호는 지움)로 값 변환을 확인한다.
 */
class PublicAnimalItemTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(nullValues = "null", value = {
            "5.0(Kg), 5.00",
            "0.15(Kg), 0.15",
            "' 3 (kg) ', 3.00",
            "12..00(Kg), 12.00",   // 실제 데이터: 점 두 개
            "6~7(Kg), 6.50",       // 실제 데이터: 범위 → 가운데 값
            "'10,3(Kg)', 10.30",   // 실제 데이터: 쉼표 소수점
            "5.5kg(Kg), 5.50",     // 실제 데이터: 단위가 두 번
            "205(Kg), null",       // 실제 데이터: 20.5 를 잘못 쓴 것으로 보이지만 추측하지 않는다
            "5.0, null",           // 단위 없음
            "abc(Kg), null",
            "1.2.3(Kg), null",
            "0(Kg), null",
            "150(Kg), null",
            "null, null"
    })
    @DisplayName("체중 원문을 kg 숫자로 바꾼다")
    void parseWeightKg(String text, BigDecimal expected) {
        assertThat(PublicAnimalItem.parseWeightKg(text)).isEqualTo(expected);
    }

    @Test
    @DisplayName("실제 응답 3마리: 체중·출생연도·공고 기간·수정 시각·코드 값을 읽는다")
    void fixtureValues() throws IOException {
        Map<String, PublicAnimalItem> items = fixtureItems();

        PublicAnimalItem twoDots = items.get("413588202600395");
        assertThat(twoDots.weightText()).isEqualTo("12..00(Kg)");
        assertThat(twoDots.weightKg()).isEqualByComparingTo("12");
        assertThat(twoDots.birthYear()).isEqualTo(2024);
        assertThat(twoDots.noticeStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(twoDots.noticeEnd()).isEqualTo(LocalDate.of(2026, 10, 26));
        // 2026-10-12 08:35:22 (한국 시각)
        assertThat(twoDots.updatedAt()).isEqualTo(Instant.parse("2026-10-11T23:35:22Z"));
        assertThat(twoDots.sex()).isEqualTo("M");
        assertThat(twoDots.neutered()).isEqualTo("U");
        assertThat(twoDots.processState()).isEqualTo(PublicAnimalItem.STATE_PROTECTED);
        assertThat(twoDots.orgName()).isEqualTo("전남광주통합특별시 함평군");

        PublicAnimalItem puppy = items.get("445464202602934");
        assertThat(puppy.ageText()).isEqualTo("2026(60일미만)(년생)");
        assertThat(puppy.birthYear()).isEqualTo(2026);
        assertThat(puppy.weightKg()).isEqualByComparingTo("5.5");

        assertThat(items.get("445479202601110").weightKg()).isEqualByComparingTo("6.5");
    }

    @Test
    @DisplayName("사진 주소는 https 로 바꾸고 순서를 지킨다")
    void photoUrls_httpsInOrder() throws IOException {
        PublicAnimalItem item = fixtureItems().get("413588202600395");

        List<String> urls = item.photoUrls();

        assertThat(urls).isNotEmpty().allSatisfy(url -> assertThat(url).startsWith("https://openapi.animal.go.kr/"));
        String popfile1 = jsonMapper.readTree(item.rawJson()).path("popfile1").asString();
        assertThat(popfile1).startsWith("http://");
        assertThat(urls.getFirst()).isEqualTo("https://" + popfile1.substring("http://".length()));
    }

    @Test
    @DisplayName("같은 사진 주소는 하나만 남기고, 빈 값은 건너뛴다")
    void photoUrls_dedupe() {
        PublicAnimalItem item = new PublicAnimalItem(jsonMapper.readTree(
                "{\"popfile1\":\"http://a/1.jpg\",\"popfile2\":\"http://a/1.jpg\",\"popfile3\":\"\",\"popfile4\":\"https://a/2.jpg\"}"));

        assertThat(item.photoUrls()).containsExactly("https://a/1.jpg", "https://a/2.jpg");
    }

    @Test
    @DisplayName("값이 없거나 형식이 다르면 예외 없이 null")
    void missingOrMalformed_isNull() {
        PublicAnimalItem item = new PublicAnimalItem(jsonMapper.readTree(
                "{\"desertionNo\":\" \",\"age\":\"모름\",\"noticeSdt\":\"2026-10-12\",\"updTm\":\"어제\",\"sexCd\":\"MF\"}"));

        assertThat(item.desertionNo()).isNull();
        assertThat(item.birthYear()).isNull();
        assertThat(item.noticeStart()).isNull();
        assertThat(item.updatedAt()).isNull();
        assertThat(item.sex()).isNull();
        assertThat(item.weightKg()).isNull();
        assertThat(item.photoUrls()).isEmpty();
    }

    private Map<String, PublicAnimalItem> fixtureItems() throws IOException {
        return jsonMapper.readTree(PublicAnimalClientTest.fixture("protect-page.json"))
                .path("response").path("body").path("items").path("item").values().stream()
                .map(PublicAnimalItem::new)
                .collect(Collectors.toMap(PublicAnimalItem::desertionNo, item -> item));
    }
}
