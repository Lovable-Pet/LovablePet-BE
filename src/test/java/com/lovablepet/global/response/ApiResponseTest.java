package com.lovablepet.global.response;

import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통 응답 포맷(API 명세서 양식) 구조 검증.
 */
class ApiResponseTest {

    @Test
    @DisplayName("성공 응답: resultType=SUCCESS, success.data 에 데이터, error=null")
    void ok() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.resultType()).isEqualTo(ResultType.SUCCESS);
        assertThat(response.success().data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
        assertThat(response.meta()).isNull(); // meta 는 응답 직전에 ApiResponseMetaAdvice 가 채운다
    }

    @Test
    @DisplayName("데이터 없는 성공: success 는 null 이 아니라 { data: null }")
    void okWithoutData() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.resultType()).isEqualTo(ResultType.SUCCESS);
        assertThat(response.success()).isNotNull();
        assertThat(response.success().data()).isNull();
    }

    @Test
    @DisplayName("실패 응답: resultType=FAIL, success=null, error 에 코드와 메시지, details=null")
    void fail() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorResponse.of(ErrorCode.AUTH_INVALID_CREDENTIALS));

        assertThat(response.resultType()).isEqualTo(ResultType.FAIL);
        assertThat(response.success()).isNull();
        assertThat(response.error().code()).isEqualTo("AUTH_401_CREDENTIALS");
        assertThat(response.error().message()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS.getMessage());
        assertThat(response.error().details()).isNull();
    }

    @Test
    @DisplayName("검증 실패: details 에 항목별 오류, 목록이 비어 있으면 null")
    void details() {
        ErrorResponse withDetails = ErrorResponse.of(ErrorCode.INVALID_INPUT,
                List.of(new ErrorResponse.FieldError("username", "ab", "too short")));
        ErrorResponse emptyDetails = ErrorResponse.of(ErrorCode.INVALID_INPUT, List.of());

        assertThat(withDetails.details()).hasSize(1);
        assertThat(emptyDetails.details()).isNull();
    }

    @Test
    @DisplayName("meta: yyyy-MM-ddTHH:mm:ss 형식의 시각과 요청 경로, 나머지 필드는 그대로")
    void withMeta() {
        ApiResponse<String> response = ApiResponse.ok("x").withMeta(ResponseMeta.of("/api/auth/login"));

        assertThat(response.meta().path()).isEqualTo("/api/auth/login");
        assertThat(response.meta().timestamp()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(response.resultType()).isEqualTo(ResultType.SUCCESS);
        assertThat(response.success().data()).isEqualTo("x");
    }
}
