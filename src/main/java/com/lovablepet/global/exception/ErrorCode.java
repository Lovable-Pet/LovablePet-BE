package com.lovablepet.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. 도메인별 에러 코드는 도메인 추가 시 여기에 이어서 정의한다.
 * (코드 접두어 규칙: COMMON_, AUTH_, MEMBER_, PET_, GENERATION_, MODEL_, MATCH_, SURVEY_, STORAGE_)
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 입력값입니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "COMMON_400_TYPE", "요청 값의 타입이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_400_PARAM", "필수 요청 파라미터가 누락되었습니다."),
    MALFORMED_BODY(HttpStatus.BAD_REQUEST, "COMMON_400_BODY", "요청 본문을 읽을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "요청이 현재 리소스 상태와 충돌합니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "COMMON_413", "업로드 가능한 파일 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_415", "지원하지 않는 Content-Type입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    // Auth
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "유효하지 않거나 만료된 액세스 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH", "유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요."),
    AUTH_KAKAO_INVALID_CODE(HttpStatus.UNAUTHORIZED, "AUTH_401_KAKAO", "카카오 인가 코드가 유효하지 않거나 만료되었습니다."),
    AUTH_INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "AUTH_403_INACTIVE", "탈퇴했거나 이용할 수 없는 회원입니다."),
    AUTH_DUPLICATE_USERNAME(HttpStatus.CONFLICT, "AUTH_409_USERNAME", "이미 사용 중인 아이디입니다."),
    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_409_EMAIL", "이미 가입된 이메일입니다."),

    // Pet (공공데이터 유기견)
    PET_SYNC_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "PET_503_SYNC_KEY", "공공데이터 인증키가 설정되지 않아 동기화할 수 없습니다."),
    PET_SYNC_RUNNING(HttpStatus.CONFLICT, "PET_409_SYNC", "동물 동기화가 이미 진행 중입니다."),
    PET_NOT_FOUND(HttpStatus.NOT_FOUND, "PET_404", "동물을 찾을 수 없습니다."),

    // Generation (3D 생성 파이프라인)
    GENERATION_INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "GENERATION_401_KEY", "파이프라인 API 키가 올바르지 않습니다."),
    GENERATION_INVALID_PET_ID(HttpStatus.BAD_REQUEST, "GENERATION_400_PET_ID", "모델 파일 이름으로 쓸 수 없는 관리번호입니다."),
    GENERATION_MODEL_EXISTS(HttpStatus.CONFLICT, "GENERATION_409_EXISTS", "이미 AR 모델이 있는 동물입니다. 한 번 만든 모델을 계속 씁니다."),
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "GENERATION_409_RUNNING", "이 동물의 AR 모델을 만드는 중입니다."),
    GENERATION_WEIGHT_REQUIRED(HttpStatus.BAD_REQUEST, "GENERATION_400_WEIGHT", "체중을 알 수 없습니다. 체중(kg)을 입력해 주세요."),
    GENERATION_INVALID_PHOTO(HttpStatus.BAD_REQUEST, "GENERATION_400_PHOTO", "사진 번호가 올바르지 않습니다."),
    GENERATION_PHOTO_REJECTED(HttpStatus.BAD_REQUEST, "GENERATION_400_PHOTO_REJECTED", "적합도 확인에서 거부된 사진입니다. 다른 사진을 골라 주세요."),
    GENERATION_PHOTO_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "GENERATION_502_PHOTO", "사진을 받을 수 없습니다. 다른 사진을 골라 주세요."),
    GENERATION_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "GENERATION_429_LIMIT", "오늘 AR 생성 횟수를 다 썼습니다. 내일 다시 시도해 주세요."),

    // External (Python AI 서버 등)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_502", "외부 서버 호출 중 오류가 발생했습니다."),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "EXTERNAL_504", "외부 서버 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
