package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.favorite.repository.FavoriteRepository;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.repository.PetModelRepository;
import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.domain.pet.dto.PetDetailResponse;
import com.lovablepet.domain.pet.dto.PetFilterOptionsResponse;
import com.lovablepet.domain.pet.dto.PetModelView;
import com.lovablepet.domain.pet.dto.PetPageResponse;
import com.lovablepet.domain.pet.dto.PetSearchCondition;
import com.lovablepet.domain.pet.dto.PetSort;
import com.lovablepet.domain.pet.dto.PetSummaryResponse;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetCountByName;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.domain.pet.repository.PetSpecifications;
import com.lovablepet.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 유기견 목록·상세 조회. 목록에는 입양 가능한(보호중이고 공고 기간이 끝난) 동물만 나온다.
 * 로그인 없이 호출할 수 있고, 로그인했으면(memberId 가 있으면) 찜 여부(favorited)를 채운다.
 * 목록 응답(PetSummaryResponse)은 내 찜 목록도 여기서 만든다 (AR 유무·입양 가능·찜 여부 규칙을 한곳에 둔다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetQueryService {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("noticeStart"), Sort.Order.desc("id"));
    private static final Sort OLDEST = Sort.by(Sort.Order.asc("noticeStart"), Sort.Order.asc("id"));

    private final PetRepository petRepository;
    private final PetModelRepository petModelRepository;
    private final PetModelFiles modelFiles;
    private final FavoriteRepository favoriteRepository;
    private final Clock clock;

    /** @param memberId 로그인한 회원. 로그인하지 않았으면 null (favorited 가 모두 false) */
    public PetPageResponse search(PetSearchCondition condition, int page, int size, Long memberId) {
        LocalDate today = today();
        List<Specification<Pet>> specs = new ArrayList<>();
        specs.add(PetSpecifications.adoptable(today));
        if (StringUtils.hasText(condition.region())) {
            specs.add(PetSpecifications.orgNameStartsWith(condition.region().trim()));
        }
        if (StringUtils.hasText(condition.kind())) {
            specs.add(PetSpecifications.kindName(condition.kind().trim()));
        }
        if (condition.minWeight() != null) {
            specs.add(PetSpecifications.weightAtLeast(condition.minWeight()));
        }
        if (condition.maxWeight() != null) {
            specs.add(PetSpecifications.weightAtMost(condition.maxWeight()));
        }
        if (condition.hasModel() != null) {
            specs.add(PetSpecifications.hasModel(condition.hasModel()));
        }
        Sort sort = condition.sort() == PetSort.OLDEST ? OLDEST : NEWEST;

        Page<Pet> pets = petRepository.findAll(Specification.allOf(specs), PageRequest.of(page, size, sort));
        Set<Long> favoriteIds = favoritePetIds(memberId, pets);
        return toPageResponse(pets, today, pet -> favoriteIds.contains(pet.getId()));
    }

    /** 내 찜 목록 한 페이지를 목록 응답으로 바꾼다. 모두 찜한 동물이라 찜 여부를 다시 조회하지 않는다. */
    public PetPageResponse toFavoritePageResponse(Page<Pet> favoritePets) {
        return toPageResponse(favoritePets, today(), pet -> true);
    }

    /**
     * 입양·반환 등으로 목록에서 빠진 동물도 조회된다 (찜 목록에서 열 때). adoptable 로 구분한다.
     *
     * @param memberId 로그인한 회원. 로그인하지 않았으면 null (favorited 가 false)
     */
    public PetDetailResponse get(Long id, Long memberId) {
        Pet pet = petRepository.findById(id).orElseThrow(() -> new PetException(ErrorCode.PET_NOT_FOUND));
        PetModelView model = petModelRepository.findByPetIdAndStatus(id, PetModelStatus.DONE)
                .map(done -> PetModelView.of(done, modelFiles.publicUrl(done.getGlbPath())))
                .orElse(null);
        boolean favorited = memberId != null && favoriteRepository.existsByMemberIdAndPetId(memberId, id);
        return PetDetailResponse.of(pet, today(), model, favorited);
    }

    public PetFilterOptionsResponse filterOptions() {
        LocalDate today = today();
        return new PetFilterOptionsResponse(
                toOptions(petRepository.countAdoptableBySido(today)),
                toOptions(petRepository.countAdoptableByKind(today)));
    }

    private static List<PetFilterOptionsResponse.Option> toOptions(List<PetCountByName> counts) {
        return counts.stream().map(count -> new PetFilterOptionsResponse.Option(count.getName(), count.getCount())).toList();
    }

    /**
     * 동물 한 페이지를 목록 응답으로 바꾼다. AR 모델 유무는 페이지 단위로 한 번에 조회한다 (동물마다 조회하지 않도록).
     *
     * @param favorited 동물마다 찜 여부
     */
    private PetPageResponse toPageResponse(Page<Pet> pets, LocalDate today, Predicate<Pet> favorited) {
        Set<Long> withModel = pets.isEmpty() ? Set.of()
                : petModelRepository.findPetIdsByStatus(pets.map(Pet::getId).getContent(), PetModelStatus.DONE);
        return PetPageResponse.from(pets.map(pet ->
                PetSummaryResponse.from(pet, today, withModel.contains(pet.getId()), favorited.test(pet))));
    }

    /** 이 페이지의 동물 중 회원이 찜한 동물 ID. 로그인하지 않았거나 페이지가 비었으면 조회하지 않는다 */
    private Set<Long> favoritePetIds(Long memberId, Page<Pet> pets) {
        if (memberId == null || pets.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findFavoritePetIds(memberId, pets.map(Pet::getId).getContent());
    }

    // 공고 날짜는 한국 날짜다
    private LocalDate today() {
        return LocalDate.now(clock.withZone(PetSyncService.KST));
    }
}
