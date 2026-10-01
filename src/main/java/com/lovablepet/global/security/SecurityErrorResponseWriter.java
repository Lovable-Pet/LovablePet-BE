package com.lovablepet.global.security;

import com.lovablepet.global.exception.ErrorCode;
import com.lovablepet.global.response.ResponseMeta;
import com.lovablepet.global.response.ResultType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계(컨트롤러 도달 전)에서 공통 실패 응답을 쓴다.
 * <p>
 * 필터 단계는 스프링 MVC 를 거치지 않아 GlobalExceptionHandler 와 ApiResponseMetaAdvice 가 적용되지 않는다.
 * 그래서 {@link com.lovablepet.global.response.ApiResponse} 실패 응답과 같은 형식의 JSON 을 여기서 직접 만들고,
 * meta(timestamp, path)도 직접 채운다.
 * 응답 본문이 ErrorCode 하나로 정해지는 단순한 구조라 Jackson 버전에 의존하지 않도록 문자열로 만든다.
 */
public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(toJson(errorCode, ResponseMeta.of(request.getRequestURI())));
    }

    static String toJson(ErrorCode errorCode, ResponseMeta meta) {
        return ("{\"resultType\":\"%s\",\"success\":null,"
                + "\"error\":{\"code\":\"%s\",\"message\":\"%s\",\"details\":null},"
                + "\"meta\":{\"timestamp\":\"%s\",\"path\":\"%s\"}}")
                .formatted(
                        ResultType.FAIL.name(),
                        escape(errorCode.getCode()),
                        escape(errorCode.getMessage()),
                        escape(meta.timestamp()),
                        escape(meta.path())
                );
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
