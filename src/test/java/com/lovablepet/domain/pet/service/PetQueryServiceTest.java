package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Behavior;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Fit;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Size;
import com.lovablepet.domain.generation.entity.PetModel;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.dto.PetDetailResponse;
import com.lovablepet.domain.pet.dto.PetFilterOptionsResponse;
import com.lovablepet.domain.pet.dto.PetModelView;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.dto.PetSearchCondition;
import com.lovablepet.domain.pet.dto.PetSort;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetCountByName;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PetQueryServiceTest {

    // UTC 로는 10-26 이지만 한국 날짜로는 10-27 01:00
    private static final Instant NOW = Instant.parse("2026-10-26T16:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PetRepository petRepository;
    @Mock
    private PetModelRepository petModelRepository;
    @Mock
    private FavoriteRepository favoriteRepository;

    @Test
    @DisplayName("상세: 실제 응답 동물을 그대로 내려주고, 원본 JSON(담당자 이름 포함)은 응답에 없다")
    void get_mapsFieldsWithoutRaw() throws IOException {
        Pet pet = fixturePet("413588202600395");
        given(petRepository.findById(7L)).willReturn(Optional.of(pet));

        PetDetailResponse detail = service().get(7L, null);
        String json = jsonMapper.writeValueAsString(detail);

        assertThat(detail.id()).isEqualTo(7L);
        assertThat(detail.desertionNo()).isEqualTo("413588202600395");
        assertThat(detail.displayName()).isEqualTo("믹스견 · 전남광주통합특별시 함평군 · 12kg");
        assertThat(detail.photoUrls()).isNotEmpty().allSatisfy(url -> assertThat(url).startsWith("https://"));
        assertThat(json).doesNotContain("\"raw\"").doesNotContain("careOwnerNm").doesNotContain("popfile")
                .contains("\"noticeEnd\":\"2026-10-26\"");
        assertThat(detail.model()).isNull(); // 아직 AR 모델이 없다
    }

    @Test
    @DisplayName("상세: 완성된 AR 모델이 있으면 glbUrl·usdzUrl·체고·탭 동작을 내려준다")
    void get_withModel() throws IOException {
        given(petRepository.findById(7L)).willReturn(Optional.of(fixturePet("413588202600395")));
        PetModel done = doneModel("413588202600395/413588202600395-a5ca257e.usdz");
        given(petModelRepository.findByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(Optional.of(done));

        PetModelView model = service().get(7L, null).model();

        assertThat(model.glbUrl()).isEqualTo("/models/413588202600395/413588202600395-a5ca257e.glb");
        assertThat(model.usdzUrl()).isEqualTo("/models/413588202600395/413588202600395-a5ca257e.usdz");
        assertThat(model.withersM()).isEqualByComparingTo("0.402");
        assertThat(model.template()).isEqualTo("jindo");
        assertThat(model.behavior().tapAnimation()).isEqualTo("tail_wag");
        assertThat(model.behavior().playbackRate()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("상세: USDZ 가 없는 모델(변환 실패·USDZ 이전 모델)은 glbUrl 만 있고 usdzUrl 은 null")
    void get_withModelWithoutUsdz() throws IOException {
        given(petRepository.findById(7L)).willReturn(Optional.of(fixturePet("413588202600395")));
        given(petModelRepository.findByPetIdAndStatus(7L, PetModelStatus.DONE)).willReturn(Optional.of(doneModel(null)));

        PetModelView model = service().get(7L, null).model();

        assertThat(model.glbUrl()).isEqualTo("/models/413588202600395/413588202600395-a5ca257e.glb");
        assertThat(model.usdzUrl()).isNull();
    }

    private static PetModel doneModel(String usdzPath) {
        PetModel done = PetModel.reserve(7L, "https://a/1.jpg", new BigDecimal("12.00"), false, 3L, NOW);
        done.attachJob("413588202600395-a5ca257e");
        done.complete(new Job("413588202600395-a5ca257e", "413588202600395", "done",
                new Fit("good", "적합", 100, List.of(), List.of(), "jindo", "vision", "close", List.of(), "jindo", List.of()),
                null, new Size(12.0, 0.402, 0.7, "weight"), null, List.of("idle", "tail_wag"),
                new Behavior(1.0, "tail_wag", false), null, null, 0.075),
                "413588202600395/413588202600395-a5ca257e.glb", usdzPath, NOW);
        return done;
    }

    @Test
    @DisplayName("상세: 입양 가능 여부는 한국 날짜로 판단한다 (공고 종료 10-26, 한국 날짜 10-27 → 입양 가능)")
    void get_adoptableUsesKoreanDate() throws IOException {
        given(petRepository.findById(7L)).willReturn(Optional.of(fixturePet("413588202600395")));

        assertThat(service().get(7L, null).adoptable()).isTrue();
    }

    @Test
    @DisplayName("상세: 없는 동물이면 PET_404")
    void get_notFound() {
        given(petRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(99L, null))
                .isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PET_NOT_FOUND);
    }

    @Test
    @DisplayName("목록: 정렬(NEWEST 최근 공고순 / OLDEST 오래 기다린 순)과 페이지를 넘기고, 첫 사진을 대표 사진으로 쓴다")
    void search_sortAndPaging() throws IOException {
        Pet pet = fixturePet("445464202602934");
        given(petRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(pet), invocation.getArgument(1), 41));
        PetQueryService service = service();

        given(petModelRepository.findPetIdsByStatus(List.of(7L), PetModelStatus.DONE)).willReturn(Set.of(7L));

        PetPageResponse newest = service.search(new PetSearchCondition(" ", null, null, null, null, PetSort.NEWEST), 2, 20, null);
        service.search(new PetSearchCondition("경기도", "믹스견", null, null, true, PetSort.OLDEST), 0, 10, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(petRepository, times(2)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isEqualTo(2);
        assertThat(pageable.getAllValues().get(0).getSort().getOrderFor("noticeStart").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getAllValues().get(1).getSort().getOrderFor("noticeStart").getDirection()).isEqualTo(Sort.Direction.ASC);

        assertThat(newest.totalElements()).isEqualTo(41);
        assertThat(newest.totalPages()).isEqualTo(3);
        assertThat(newest.hasNext()).isFalse();
        assertThat(newest.items()).singleElement().satisfies(item -> {
            assertThat(item.thumbnailUrl()).isEqualTo(pet.getPhotoUrls().getFirst());
            assertThat(item.displayName()).isEqualTo(pet.displayName());
            assertThat(item.hasModel()).isTrue();
        });
    }

    @Test
    @DisplayName("필터 선택지: 시도·품종별 마릿수를 한국 날짜 기준으로 집계해 그대로 내려준다")
    void filterOptions() {
        LocalDate koreanToday = LocalDate.of(2026, 10, 27);
        given(petRepository.countAdoptableBySido(koreanToday)).willReturn(List.of(count("경기도", 412), count("서울특별시", 120)));
        given(petRepository.countAdoptableByKind(koreanToday)).willReturn(List.of(count("믹스견", 1490)));

        PetFilterOptionsResponse options = service().filterOptions();

        assertThat(options.regions()).extracting(PetFilterOptionsResponse.Option::name).containsExactly("경기도", "서울특별시");
        assertThat(options.kinds()).singleElement().satisfies(kind -> {
            assertThat(kind.name()).isEqualTo("믹스견");
            assertThat(kind.count()).isEqualTo(1490);
        });
    }

    @Test
    @DisplayName("목록: 로그인했으면 이 페이지에서 찜한 동물의 favorited 가 true")
    void search_favoritedWhenLoggedIn() throws IOException {
        Pet pet = fixturePet("445464202602934");
        given(petRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(pet), invocation.getArgument(1), 1));
        given(favoriteRepository.findFavoritePetIds(3L, List.of(7L))).willReturn(Set.of(7L));

        PetPageResponse page = service().search(new PetSearchCondition(null, null, null, null, null, PetSort.NEWEST), 0, 20, 3L);

        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.favorited()).isTrue());
    }

    @Test
    @DisplayName("목록: 로그인하지 않았으면 찜을 조회하지 않고 favorited 가 false")
    void search_notLoggedIn() throws IOException {
        Pet pet = fixturePet("445464202602934");
        given(petRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(pet), invocation.getArgument(1), 1));

        PetPageResponse page = service().search(new PetSearchCondition(null, null, null, null, null, PetSort.NEWEST), 0, 20, null);

        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.favorited()).isFalse());
        verifyNoInteractions(favoriteRepository);
    }

    @Test
    @DisplayName("상세: 로그인했으면 찜 여부를 채우고, 로그인하지 않았으면 조회하지 않고 false")
    void get_favorited() throws IOException {
        given(petRepository.findById(7L)).willReturn(Optional.of(fixturePet("413588202600395")));
        given(favoriteRepository.existsByMemberIdAndPetId(3L, 7L)).willReturn(true);

        assertThat(service().get(7L, 3L).favorited()).isTrue();
        assertThat(service().get(7L, null).favorited()).isFalse();
        verify(favoriteRepository, times(1)).existsByMemberIdAndPetId(3L, 7L);
    }

    @Test
    @DisplayName("찜 목록 응답: 모두 favorited=true 이고(찜을 다시 조회하지 않음), 보호가 끝난 동물은 adoptable=false")
    void toFavoritePageResponse() throws IOException {
        Pet ended = fixturePet("413588202600395");
        ReflectionTestUtils.setField(ended, "listed", false); // 입양·반환으로 목록에서 내려감

        PetPageResponse page = service().toFavoritePageResponse(new PageImpl<>(List.of(ended)));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.favorited()).isTrue();
            assertThat(item.adoptable()).isFalse();
        });
        verifyNoInteractions(favoriteRepository);
    }

    private static PetCountByName count(String name, long count) {
        return new PetCountByName() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private PetQueryService service() {
        PetModelFiles files = new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "", "models",
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "/models"));
        return new PetQueryService(petRepository, petModelRepository, files, favoriteRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Pet fixturePet(String desertionNo) throws IOException {
        String fixture = new ClassPathResource("public-animal/protect-page.json").getContentAsString(StandardCharsets.UTF_8);
        PublicAnimalItem item = jsonMapper.readTree(fixture)
                .path("response").path("body").path("items").path("item").values().stream()
                .map(PublicAnimalItem::new)
                .filter(it -> desertionNo.equals(it.desertionNo()))
                .findFirst().orElseThrow();
        Pet pet = Pet.from(item, NOW);
        ReflectionTestUtils.setField(pet, "id", 7L);
        return pet;
    }
}
