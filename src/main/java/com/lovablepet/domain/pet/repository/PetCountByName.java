package com.lovablepet.domain.pet.repository;

/** 이름별 마릿수 (필터 선택지용 집계 결과) */
public interface PetCountByName {

    String getName();

    long getCount();
}
