package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.dto.AuthTokenResponse;
import com.lovablepet.domain.auth.entity.LocalCredential;
import com.lovablepet.domain.auth.entity.OAuthAccount;
import com.lovablepet.domain.auth.entity.OAuthProvider;
import com.lovablepet.domain.auth.entity.RefreshToken;
import com.lovablepet.domain.auth.repository.LocalCredentialRepository;
import com.lovablepet.domain.auth.repository.OAuthAccountRepository;
import com.lovablepet.domain.auth.repository.RefreshTokenRepository;
import com.lovablepet.domain.member.entity.Member;
import com.lovablepet.domain.member.entity.MemberStatus;
import com.lovablepet.domain.member.repository.MemberRepository;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import com.lovablepet.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 단위 테스트 (DB 없이 실행).
 * 저장소는 Mock, 암호화/토큰 컴포넌트는 실제 구현을 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET = "v/Z5Y1B/L8xXbK2G7zUeT9aR3vC5bN8xQ2W1yM4kZ6U=";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private LocalCredentialRepository localCredentialRepository;
    @Mock
    private OAuthAccountRepository oauthAccountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final RefreshTokenHashService hashService = new RefreshTokenHashService("test-token-hash-secret");
    private final JwtProvider jwtProvider = new JwtProvider(JWT_SECRET, 1_800_000L, 1_209_600_000L);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                memberRepository,
                localCredentialRepository,
                oauthAccountRepository,
                refreshTokenRepository,
                passwordEncoder,
                hashService,
                jwtProvider
        );
    }

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입: 이메일을 정규화하고 비밀번호를 해시로 저장한 뒤 토큰을 발급한다")
    void signUp_success() {
        given(localCredentialRepository.existsByEmail(EMAIL)).willReturn(false);
        givenMemberSaveAssignsId(1L);

        AuthTokenResponse tokens = authService.signUpLocal("  User@Example.com ", PASSWORD, "댕댕이");

        ArgumentCaptor<LocalCredential> credentialCaptor = ArgumentCaptor.forClass(LocalCredential.class);
        verify(localCredentialRepository).save(credentialCaptor.capture());
        LocalCredential saved = credentialCaptor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        assertThat(jwtProvider.getMemberId(tokens.accessToken())).isEqualTo(1L);
        assertRefreshTokenStoredAsHash(tokens.refreshToken());
    }

    @Test
    @DisplayName("회원가입: 이미 가입된 이메일이면 AUTH_DUPLICATE_EMAIL")
    void signUp_duplicateEmail() {
        given(localCredentialRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signUpLocal(EMAIL, PASSWORD, "댕댕이"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL);
        verify(memberRepository, never()).save(any());
    }

    // ---------- 로그인 ----------

    @Test
    @DisplayName("로그인: 올바른 이메일/비밀번호면 토큰을 발급한다 (이메일 대소문자 무시)")
    void login_success() {
        given(localCredentialRepository.findByEmail(EMAIL)).willReturn(Optional.of(credential(1L)));
        given(memberRepository.existsByIdAndStatus(1L, MemberStatus.ACTIVE)).willReturn(true);

        AuthTokenResponse tokens = authService.loginLocal(" USER@example.com", PASSWORD);

        assertThat(jwtProvider.getMemberId(tokens.accessToken())).isEqualTo(1L);
        assertRefreshTokenStoredAsHash(tokens.refreshToken());
    }

    @Test
    @DisplayName("로그인: 없는 이메일과 틀린 비밀번호는 같은 오류 코드로 응답한다")
    void login_failuresShareSameErrorCode() {
        given(localCredentialRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());
        given(localCredentialRepository.findByEmail(EMAIL)).willReturn(Optional.of(credential(1L)));

        assertThatThrownBy(() -> authService.loginLocal("nobody@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThatThrownBy(() -> authService.loginLocal(EMAIL, "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그인: 탈퇴한 회원이면 AUTH_INACTIVE_MEMBER")
    void login_inactiveMember() {
        given(localCredentialRepository.findByEmail(EMAIL)).willReturn(Optional.of(credential(1L)));
        given(memberRepository.existsByIdAndStatus(1L, MemberStatus.ACTIVE)).willReturn(false);

        assertThatThrownBy(() -> authService.loginLocal(EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INACTIVE_MEMBER);
    }

    // ---------- 소셜 로그인 ----------

    @Test
    @DisplayName("소셜 로그인: 처음 로그인하면 회원과 OAuth 계정을 만들고 토큰을 발급한다")
    void oauth_firstLoginCreatesMember() {
        given(oauthAccountRepository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.empty());
        givenMemberSaveAssignsId(7L);

        AuthTokenResponse tokens = authService.loginWithOAuth(OAuthProvider.KAKAO, "12345", "카카오유저");

        ArgumentCaptor<OAuthAccount> accountCaptor = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getMemberId()).isEqualTo(7L);
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(accountCaptor.getValue().getProviderSubject()).isEqualTo("12345");
        assertThat(jwtProvider.getMemberId(tokens.accessToken())).isEqualTo(7L);
    }

    @Test
    @DisplayName("소셜 로그인: 이미 연동된 계정이면 새 회원을 만들지 않는다")
    void oauth_existingAccount() {
        given(oauthAccountRepository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(OAuthAccount.create(3L, OAuthProvider.KAKAO, "12345")));
        given(memberRepository.existsByIdAndStatus(3L, MemberStatus.ACTIVE)).willReturn(true);

        AuthTokenResponse tokens = authService.loginWithOAuth(OAuthProvider.KAKAO, "12345", "카카오유저");

        assertThat(jwtProvider.getMemberId(tokens.accessToken())).isEqualTo(3L);
        verify(memberRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    // ---------- 토큰 재발급 / 로그아웃 ----------

    @Test
    @DisplayName("재발급: 리프레시 토큰도 새 값으로 교체된다 (RTR)")
    void reissue_rotatesRefreshToken() {
        String oldRawToken = jwtProvider.createRefreshToken();
        RefreshToken stored = RefreshToken.create(1L, hashService.hash(oldRawToken), LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(hashService.hash(oldRawToken))).willReturn(Optional.of(stored));
        given(memberRepository.existsByIdAndStatus(1L, MemberStatus.ACTIVE)).willReturn(true);

        AuthTokenResponse tokens = authService.reissueToken(oldRawToken);

        assertThat(tokens.refreshToken()).isNotEqualTo(oldRawToken);
        assertThat(stored.getTokenHash()).isEqualTo(hashService.hash(tokens.refreshToken()));
        assertThat(jwtProvider.getMemberId(tokens.accessToken())).isEqualTo(1L);
    }

    @Test
    @DisplayName("재발급: 모르는 리프레시 토큰이면 AUTH_INVALID_REFRESH_TOKEN")
    void reissue_unknownToken() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissueToken("unknown-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("재발급: 만료된 리프레시 토큰은 삭제하고 AUTH_INVALID_REFRESH_TOKEN")
    void reissue_expiredToken() {
        String rawToken = jwtProvider.createRefreshToken();
        RefreshToken expired = RefreshToken.create(1L, hashService.hash(rawToken), LocalDateTime.now().minusMinutes(1));
        given(refreshTokenRepository.findByTokenHash(hashService.hash(rawToken))).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.reissueToken(rawToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    @DisplayName("로그아웃: 전달한 리프레시 토큰의 해시로 삭제한다")
    void logout_deletesByHash() {
        String rawToken = jwtProvider.createRefreshToken();

        authService.logout(rawToken);

        verify(refreshTokenRepository).deleteByTokenHash(hashService.hash(rawToken));
    }

    // ---------- helpers ----------

    private LocalCredential credential(Long memberId) {
        return LocalCredential.create(memberId, EMAIL, passwordEncoder.encode(PASSWORD));
    }

    /** save 시 DB가 BIGSERIAL id를 채워 주는 동작을 흉내 낸다. */
    private void givenMemberSaveAssignsId(long id) {
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", id);
            return member;
        });
    }

    /** DB에는 평문이 아닌 HMAC 해시만 저장되어야 한다. */
    private void assertRefreshTokenStoredAsHash(String rawRefreshToken) {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(hashService.hash(rawRefreshToken))
                .isNotEqualTo(rawRefreshToken);
    }
}
