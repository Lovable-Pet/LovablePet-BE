package com.lovablepet.global.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String JWT_SECRET = "v/Z5Y1B/L8xXbK2G7zUeT9aR3vC5bN8xQ2W1yM4kZ6U=";

    private final JwtProvider jwtProvider = new JwtProvider(JWT_SECRET, 1_800_000L, 1_209_600_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰의 역할이 ROLE_ 권한으로 들어가 /api/admin/** 규칙(hasRole ADMIN)에 쓰인다")
    void roleBecomesAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/pets/sync");
        request.addHeader("Authorization", "Bearer " + jwtProvider.createAccessToken(5L, "ADMIN"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(5L);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("일반 회원 토큰은 ROLE_USER 권한만 가진다")
    void userToken_hasOnlyUserRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pets");
        request.addHeader("Authorization", "Bearer " + jwtProvider.createAccessToken(7L, "USER"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }
}
