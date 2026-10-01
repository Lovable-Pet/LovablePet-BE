package com.lovablepet.global.security;

import com.lovablepet.global.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer {accessToken} 헤더를 검증해 SecurityContext에 회원 ID를 넣는다.
 * <p>
 * 스프링 빈(@Component)으로 등록하지 않고 SecurityConfig에서 직접 생성한다.
 * 빈으로 등록하면 Spring Boot가 일반 서블릿 필터로도 한 번 더 등록하기 때문이다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_API_PREFIX = "/api/auth/";

    private final JwtProvider jwtProvider;

    /**
     * 로그인/재발급 등 인증 API는 토큰 검사를 하지 않는다.
     * (액세스 토큰이 만료된 상태로 재발급을 요청해도 401로 막히지 않도록)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(AUTH_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = resolveAccessToken(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long memberId;
        try {
            memberId = jwtProvider.getMemberId(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            // 만료/위조 토큰: 클라이언트가 재발급을 시도할 수 있도록 AUTH_401_TOKEN 코드로 응답
            SecurityContextHolder.clearContext();
            SecurityErrorResponseWriter.write(request, response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(memberId, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        return accessToken.isEmpty() ? null : accessToken;
    }
}
