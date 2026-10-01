package com.lovablepet.global.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 컨트롤러와 GlobalExceptionHandler 가 돌려준 ApiResponse 에 meta(timestamp, path)를 응답 직전에 자동으로 채운다.
 * 그래서 각 API 는 ApiResponse.ok(data) 만 반환하면 된다.
 * (보안 필터 단계의 401/403 응답은 MVC 를 거치지 않으므로 SecurityErrorResponseWriter 가 직접 채운다)
 */
@RestControllerAdvice
public class ApiResponseMetaAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?> apiResponse && apiResponse.meta() == null) {
            return apiResponse.withMeta(ResponseMeta.of(requestPath(request)));
        }
        return body;
    }

    // 보안 필터 응답과 같은 기준(HttpServletRequest#getRequestURI, 쿼리스트링 제외)으로 경로를 구한다.
    private static String requestPath(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRequestURI();
        }
        return request.getURI().getPath();
    }
}
