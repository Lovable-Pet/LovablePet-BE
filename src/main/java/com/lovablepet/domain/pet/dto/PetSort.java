package com.lovablepet.domain.pet.dto;

/** 목록 정렬 */
public enum PetSort {
    /**
     * 기본순: 지역·공고일 상관없이 섞은 순서 (pet.sort_key). 같은 날 함께 구조된 형제견이 줄줄이 붙어 나오지 않는다.
     * 동기화 때마다 다시 섞이고, 그 사이에는 페이지를 넘겨도 순서가 같다
     */
    MIXED,
    /** 최근에 공고된 순 */
    NEWEST,
    /** 오래 기다린 순 (공고가 오래된 순) */
    OLDEST
}
