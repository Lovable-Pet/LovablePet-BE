package com.lovablepet.domain.pet.dto;

import java.math.BigDecimal;

/**
 * 목록 검색 조건. null 이나 빈 문자열은 조건 없음으로 본다.
 *
 * @param region 관할 지자체 앞부분 (예: "경기도", "경기도 화성시")
 * @param kind   품종 (예: "믹스견")
 */
public record PetSearchCondition(String region, String kind, BigDecimal minWeight, BigDecimal maxWeight, PetSort sort) {
}
