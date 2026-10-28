package com.lovablepet.domain.pet.repository;

import com.lovablepet.domain.pet.entity.Pet;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 동물 목록 검색 조건.
 */
public final class PetSpecifications {

    private static final char LIKE_ESCAPE = '!';

    private PetSpecifications() {
    }

    /** 입양 가능(보호중): Pet#isAdoptable 과 같은 조건 */
    public static Specification<Pet> adoptable(LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("listed")),
                cb.lessThan(root.get("noticeEnd"), today));
    }

    /** 관할 지자체가 이 값으로 시작한다 (예: "경기도", "경기도 화성시") */
    public static Specification<Pet> orgNameStartsWith(String region) {
        return (root, query, cb) -> cb.like(root.get("orgName"), escapeLike(region) + "%", LIKE_ESCAPE);
    }

    public static Specification<Pet> kindName(String kind) {
        return (root, query, cb) -> cb.equal(root.get("kindName"), kind);
    }

    public static Specification<Pet> weightAtLeast(BigDecimal kg) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("weightKg"), kg);
    }

    public static Specification<Pet> weightAtMost(BigDecimal kg) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("weightKg"), kg);
    }

    // 사용자가 넣은 %, _ 를 와일드카드가 아닌 글자로 찾도록 이스케이프한다
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
