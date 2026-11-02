package com.lovablepet.domain.favorite.service;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 찜하기·찜 취소. 둘 다 여러 번 호출해도 결과가 같다 (이미 찜했어도, 이미 취소했어도 성공).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PetRepository petRepository;
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
}
