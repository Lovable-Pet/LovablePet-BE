package com.lovablepet.global.exception;

import com.lovablepet.global.response.ApiResponse;
import com.lovablepet.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 예외 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return toResponse(e.getErrorCode(), ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    /** @Valid @RequestBody / @ModelAttribute 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), maskSensitive(fe.getField(), fe.getRejectedValue()), fe.getDefaultMessage()))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    /** @Validated 파라미터(@PathVariable, @RequestParam) 검증 실패 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), maskSensitive(v.getPropertyPath().toString(), v.getInvalidValue()), v.getMessage()))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    /** 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable 등)에 직접 붙인 제약 검증 실패 (Spring 6.1+ 내장 메서드 검증) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(),
                                maskSensitive(result.getMethodParameter().getParameterName(), result.getArgument()),
                                error.getDefaultMessage())))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "'%s' 값의 타입이 올바르지 않습니다.".formatted(e.getName());
        return toResponse(ErrorCode.INVALID_TYPE, ErrorResponse.of(ErrorCode.INVALID_TYPE, message));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(Exception e) {
        // 스프링 기본 메시지(영어) 대신, 누락된 파라미터 이름을 붙인 한국어 메시지로 응답한다.
        // 예) "필수 요청 파라미터가 누락되었습니다. (username)"
        String name = null;
        if (e instanceof MissingServletRequestParameterException ex) {
            name = ex.getParameterName();
        } else if (e instanceof MissingServletRequestPartException ex) {
            name = ex.getRequestPartName();
        }
        String message = (name == null)
                ? ErrorCode.MISSING_PARAMETER.getMessage()
                : "%s (%s)".formatted(ErrorCode.MISSING_PARAMETER.getMessage(), name);
        return toResponse(ErrorCode.MISSING_PARAMETER, ErrorResponse.of(ErrorCode.MISSING_PARAMETER, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.MALFORMED_BODY, ErrorResponse.of(ErrorCode.MALFORMED_BODY));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.PAYLOAD_TOO_LARGE, ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    /** DB 유니크 제약 위반 (예: 같은 이메일로 동시에 가입 요청) — 500 대신 409로 응답 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMessage());
        return toResponse(ErrorCode.CONFLICT, ErrorResponse.of(ErrorCode.CONFLICT));
    }

    /** 외부(Python AI 서버) 호출 타임아웃/연결 실패 */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalTimeout(ResourceAccessException e) {
        log.error("External API access failed", e);
        return toResponse(ErrorCode.EXTERNAL_API_TIMEOUT, ErrorResponse.of(ErrorCode.EXTERNAL_API_TIMEOUT));
    }

    /** 외부(Python AI 서버) 호출 오류 응답 (4xx/5xx 등) */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalError(RestClientException e) {
        log.error("External API call failed", e);
        return toResponse(ErrorCode.EXTERNAL_API_ERROR, ErrorResponse.of(ErrorCode.EXTERNAL_API_ERROR));
    }

    /** 그 외 처리되지 않은 모든 예외 — 내부 메시지는 노출하지 않는다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    /** 비밀번호 등 민감한 입력값은 오류 응답(rejectedValue)에 되돌려주지 않는다. */
    private static Object maskSensitive(String field, Object value) {
        if (field != null && field.toLowerCase(Locale.ROOT).contains("password")) {
            return null;
        }
        return value;
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(body));
    }
}
