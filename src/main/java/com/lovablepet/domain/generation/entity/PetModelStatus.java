package com.lovablepet.domain.generation.entity;

public enum PetModelStatus {
    /** 파이프라인이 만드는 중 (대기 포함) */
    PENDING,
    /** 완성. GLB 를 저장했다 */
    DONE,
    /** 사진이 못 쓰는 사진 (사람 손이 가림, 여러 마리 등). 다른 사진으로 다시 */
    PHOTO_REJECTED,
    /** 템플릿 부적합으로 멈춤 (fit_stop). 다른 사진이나 force 로 다시. 맞는 템플릿이 없는 개(no_template)는 force 여도 여기서 끝난다 */
    UNSUITABLE,
    /** 파이프라인 오류·재시작 등. 다시 시도 */
    FAILED,
    /**
     * 완성이었지만 같은 동물의 새 모델이 완성돼 교체됨. 파일은 지웠다.
     * DB 안에서만 쓰고 앱에는 DONE + replaced=true 로 내보낸다 (PetModelResponse) — 앱이 모르는 상태를 받지 않게
     */
    REPLACED
}
