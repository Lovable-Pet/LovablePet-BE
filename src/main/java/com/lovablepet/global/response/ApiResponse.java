package com.lovablepet.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API의 공통 응답 포맷 (API 명세서 양식).
 *
 * <pre>
 * 성공: { "resultType": "SUCCESS", "success": { "data": {...} }, "error": null, "meta": {...} }
 * 실패: { "resultType": "FAIL", "success": null, "error": { "code", "message", "details" }, "meta": {...} }
 * </pre>
 * 값이 없는 필드도 null 로 항상 포함한다.
 * meta(timestamp, path)는 컨트롤러에서 채우지 않는다. 응답 직전에 {@link ApiResponseMetaAdvice}가 자동으로 채운다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        ResultType resultType,
        SuccessBody<T> success,
        ErrorResponse error,
        ResponseMeta meta
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ResultType.SUCCESS, new SuccessBody<>(data), null, null);
    }

    /** 반환할 데이터가 없는 성공 (예: 로그아웃) → "success": { "data": null } */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ResultType.SUCCESS, new SuccessBody<>(null), null, null);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(ResultType.FAIL, null, error, null);
    }

    /** meta 를 채운 응답을 만든다. (record 는 값을 바꿀 수 없어 복사본을 만든다) */
    public ApiResponse<T> withMeta(ResponseMeta meta) {
        return new ApiResponse<>(resultType, success, error, meta);
    }

    /** 성공 응답의 데이터 상자 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SuccessBody<D>(D data) {
    }
}
