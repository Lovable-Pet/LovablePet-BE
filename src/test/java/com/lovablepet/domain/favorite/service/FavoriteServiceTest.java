package com.lovablepet.domain.favorite.service;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.domain.pet.service.PetQueryService;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T04:00:00Z");
    private static final Long MEMBER_ID = 3L;
    private static final Long PET_ID = 7L;

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private PetQueryService petQueryService;

    @Test
    @DisplayName("찜하기: 찜한 시각을 넣고 favorited=true 로 답한다")
    void add() {
        given(petRepository.existsById(PET_ID)).willReturn(true);
        given(favoriteRepository.insertIfAbsent(MEMBER_ID, PET_ID, NOW)).willReturn(1);

        FavoriteStatusResponse response = service().add(MEMBER_ID, PET_ID);

        assertThat(response.petId()).isEqualTo(PET_ID);
        assertThat(response.favorited()).isTrue();
        verify(favoriteRepository).insertIfAbsent(MEMBER_ID, PET_ID, NOW);
    }

    @Test
    @DisplayName("찜하기: 이미 찜했어도(새로 넣은 행이 없어도) 에러 없이 favorited=true")
    void add_alreadyFavorited() {
        given(petRepository.existsById(PET_ID)).willReturn(true);
        given(favoriteRepository.insertIfAbsent(MEMBER_ID, PET_ID, NOW)).willReturn(0);

        assertThat(service().add(MEMBER_ID, PET_ID).favorited()).isTrue();
    }

    @Test
    @DisplayName("찜하기: 없는 동물이면 PET_404 이고 저장하지 않는다")
    void add_petNotFound() {
        given(petRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service().add(MEMBER_ID, 99L))
                .isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PET_NOT_FOUND);
        verify(favoriteRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("찜 취소: 찜하지 않았어도(지운 행이 없어도) 에러 없이 favorited=false")
    void remove_notFavorited() {
        given(favoriteRepository.deleteByMemberIdAndPetId(MEMBER_ID, PET_ID)).willReturn(0);

        FavoriteStatusResponse response = service().remove(MEMBER_ID, PET_ID);

        assertThat(response.petId()).isEqualTo(PET_ID);
        assertThat(response.favorited()).isFalse();
    }

    @Test
    @DisplayName("내 찜 목록: 요청한 페이지를 조회하고, 목록 응답은 PetQueryService 가 만든다")
    void list() {
        Page<Pet> favoritePets = Page.empty();
        PetPageResponse expected = new PetPageResponse(List.of(), 2, 10, 0, 0, false);
        given(favoriteRepository.findFavoritePets(eq(MEMBER_ID), any(Pageable.class))).willReturn(favoritePets);
        given(petQueryService.toFavoritePageResponse(favoritePets)).willReturn(expected);

        PetPageResponse response = service().list(MEMBER_ID, 2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(favoriteRepository).findFavoritePets(eq(MEMBER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(response).isSameAs(expected);
    }

    private FavoriteService service() {
        return new FavoriteService(favoriteRepository, petRepository, petQueryService, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
