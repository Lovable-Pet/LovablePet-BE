package com.lovablepet.domain.favorite.service;

import com.lovablepet.domain.favorite.dto.FavoriteStatusResponse;
import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.dto.PetSummaryResponse;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * 찜하기·찜 취소·내 찜 목록. 찜하기와 취소는 여러 번 호출해도 결과가 같다 (이미 찜했어도, 이미 취소했어도 성공).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    // 공고 날짜는 한국 날짜다 (입양 가능 여부 판단)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final FavoriteRepository favoriteRepository;
    private final PetRepository petRepository;
    private final PetModelRepository petModelRepository;
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

    /** 최근에 찜한 순. 입양·반환된 동물도 나오고 adoptable 이 false 다 */
    public PetPageResponse list(Long memberId, int page, int size) {
        Page<Pet> pets = favoriteRepository.findFavoritePets(memberId, PageRequest.of(page, size));
        Set<Long> withModel = pets.isEmpty() ? Set.of()
                : petModelRepository.findPetIdsByStatus(pets.map(Pet::getId).getContent(), PetModelStatus.DONE);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        return PetPageResponse.from(pets.map(pet -> PetSummaryResponse.from(pet, today, withModel.contains(pet.getId()))));
    }
}
