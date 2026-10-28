package com.lovablepet.domain.pet.client;

import java.util.List;

/**
 * 구조동물 조회 한 페이지.
 *
 * @param totalCount 조건에 맞는 전체 건수 (페이지 수 계산용)
 */
public record PublicAnimalPage(List<PublicAnimalItem> items, int totalCount) {
}
