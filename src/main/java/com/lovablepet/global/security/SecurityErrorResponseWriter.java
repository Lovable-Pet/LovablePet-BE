package com.lovablepet.global.security;

import com.lovablepet.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계(컨트롤러 도달 전)에서 공통 실패 응답을 쓴다.
 * <p>
 * Spring Boot 4는 Jackson 3(tools.jackson)을 기본으로 쓰므로 Jackson 2의 ObjectMapper 빈이 없다.
 * 응답 본문이 ErrorCode 하나로 고정된 단순 구조라서, Jackson 버전에 의존하지 않도록 직접 JSON을 만든다.
 * 형식은 {@link com.lovablepet.global.response.ApiResponse} 실패 응답과 동일하다.
 */
public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(toJson(errorCode));
    }

    private static String toJson(ErrorCode errorCode) {
        return "{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}"
                .formatted(escape(errorCode.getCode()), escape(errorCode.getMessage()));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
