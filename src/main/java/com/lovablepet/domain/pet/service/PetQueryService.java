package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.dto.PetDetailResponse;
import com.lovablepet.domain.pet.dto.PetFilterOptionsResponse;
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

/**
 * 유기견 목록·상세 조회. 목록에는 입양 가능한(보호중이고 공고 기간이 끝난) 동물만 나온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetQueryService {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("noticeStart"), Sort.Order.desc("id"));
    private static final Sort OLDEST = Sort.by(Sort.Order.asc("noticeStart"), Sort.Order.asc("id"));

    private final PetRepository petRepository;
    private final Clock clock;

    public PetPageResponse search(PetSearchCondition condition, int page, int size) {
        List<Specification<Pet>> specs = new ArrayList<>();
        specs.add(PetSpecifications.adoptable(today()));
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
        Sort sort = condition.sort() == PetSort.OLDEST ? OLDEST : NEWEST;

        return PetPageResponse.from(petRepository
                .findAll(Specification.allOf(specs), PageRequest.of(page, size, sort))
                .map(PetSummaryResponse::from));
    }

    /** 입양·반환 등으로 목록에서 빠진 동물도 조회된다 (찜 목록에서 열 때). adoptable 로 구분한다. */
    public PetDetailResponse get(Long id) {
        Pet pet = petRepository.findById(id).orElseThrow(() -> new PetException(ErrorCode.PET_NOT_FOUND));
        return PetDetailResponse.of(pet, today());
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

    // 공고 날짜는 한국 날짜다
    private LocalDate today() {
        return LocalDate.now(clock.withZone(PetSyncService.KST));
    }
}
