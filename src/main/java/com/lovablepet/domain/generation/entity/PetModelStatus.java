package com.lovablepet.domain.generation.entity;

public enum PetModelStatus {
    /** 파이프라인이 만드는 중 (대기 포함) */
    PENDING,
    /** 완성. GLB 를 저장했다 */
    DONE,
    /** 사진이 못 쓰는 사진 (사람 손이 가림, 여러 마리 등). 다른 사진으로 다시 */
    PHOTO_REJECTED,
    /** 템플릿 부적합으로 멈춤 (fit_stop). 다른 사진이나 force 로 다시 */
    UNSUITABLE,
    /** 파이프라인 오류·재시작 등. 다시 시도 */
    FAILED
}
