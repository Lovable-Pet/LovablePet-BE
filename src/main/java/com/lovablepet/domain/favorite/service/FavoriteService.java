package com.lovablepet.domain.favorite.service;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.domain.pet.service.PetQueryService;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 찜하기·찜 취소·내 찜 목록. 찜하기와 취소는 여러 번 호출해도 결과가 같다 (이미 찜했어도, 이미 취소했어도 성공).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PetRepository petRepository;
    private final PetQueryService petQueryService;
    private final Clock clock;

    /** 입양된 동물도 찜할 수 있다 (상세는 목록에서 빠진 동물도 열리므로) */
    @Transactional
    public FavoriteStatusResponse add(Long memberId, Long petId) {
        if (!petRepository.existsById(petId)) {
            throw new PetException(ErrorCode.PET_NOT_FOUND);
        }
        favoriteRepository.insertIfAbsent(memberId, petId, clock.instant());
        return new FavoriteStatusResponse(petId, true);
    }

    @Transactional
    public FavoriteStatusResponse remove(Long memberId, Long petId) {
        favoriteRepository.deleteByMemberIdAndPetId(memberId, petId);
        return new FavoriteStatusResponse(petId, false);
    }

    /**
     * 최근에 찜한 순. 입양·반환된 동물도 나오고 adoptable 이 false 다.
     * 목록 응답은 일반 목록과 같은 규칙으로 PetQueryService 가 만든다 (AR 유무·입양 가능 여부).
     */
    public PetPageResponse list(Long memberId, int page, int size) {
        return petQueryService.toFavoritePageResponse(
                favoriteRepository.findFavoritePets(memberId, PageRequest.of(page, size)));
    }
}
