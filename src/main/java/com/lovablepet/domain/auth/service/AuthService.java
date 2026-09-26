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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    // 없는 이메일로 로그인해도 비밀번호 검증을 한 번 수행해 응답 시간 차이로 가입 여부를 추측하지 못하게 한다.
    private static final String DUMMY_PASSWORD = "lovablepet-timing-dummy-password";

    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // 역할이 분리된 두 개의 암호화 컴포넌트 주입
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenHashService refreshTokenHashService;
    private final JwtProvider jwtProvider;

    private volatile String dummyPasswordHash;

    /**
     * 이메일 회원가입. 가입 직후 바로 로그인 상태가 되도록 토큰을 발급한다.
     * 동시 가입으로 유니크 제약에 걸리면 GlobalExceptionHandler가 409로 응답한다.
     */
    @Transactional
    public AuthTokenResponse signUpLocal(String email, String rawPassword, String nickname) {
        // 1. 이메일 정규화 및 중복 검증
        String normalizedEmail = LocalCredential.normalizeEmail(email);
        if (localCredentialRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
        }

        // 2. 회원 생성
        Member member = Member.create(nickname);
        memberRepository.save(member);

        // 3. 비밀번호는 DelegatingPasswordEncoder로 인코딩 ({bcrypt}...)
        String encodedPassword = passwordEncoder.encode(rawPassword);
        localCredentialRepository.save(LocalCredential.create(member.getId(), normalizedEmail, encodedPassword));

        return issueTokens(member.getId());
    }

    /**
     * 이메일 로그인. 이메일이 없든 비밀번호가 틀리든 같은 오류로 응답해 가입 여부를 노출하지 않는다.
     */
    @Transactional
    public AuthTokenResponse loginLocal(String email, String rawPassword) {
        Optional<LocalCredential> credential =
                localCredentialRepository.findByEmail(LocalCredential.normalizeEmail(email));

        // 비밀번호 검증은 무조건 matches() 사용
        String passwordHash = credential.map(LocalCredential::getPasswordHash).orElseGet(this::dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, passwordHash);

        if (credential.isEmpty() || !passwordMatches) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return issueTokensForActiveMember(credential.get().getMemberId());
    }

    /**
     * 소셜 로그인. 연동된 계정이 없으면 회원을 새로 만든다.
     * 외부 API 호출은 호출하는 쪽(KakaoAuthService 등)에서 트랜잭션 밖에서 끝낸 뒤 이 메서드를 부른다.
     */
    @Transactional
    public AuthTokenResponse loginWithOAuth(OAuthProvider provider, String providerSubject, String nickname) {
        Optional<OAuthAccount> account =
                oauthAccountRepository.findByProviderAndProviderSubject(provider, providerSubject);
        if (account.isPresent()) {
            return issueTokensForActiveMember(account.get().getMemberId());
        }

        Member member = Member.create(nickname);
        memberRepository.save(member);
        oauthAccountRepository.save(OAuthAccount.create(member.getId(), provider, providerSubject));

        return issueTokens(member.getId());
    }

    @Transactional
    public AuthTokenResponse reissueToken(String rawRefreshToken) {
        // HMAC-SHA-256은 결정적이므로, 입력받은 토큰을 해싱해서 바로 DB 조회 가능
        String hashedToken = refreshTokenHashService.hash(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        if (!isActiveMember(refreshToken.getMemberId())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_INACTIVE_MEMBER);
        }

        // 새 토큰 발급 및 엔티티 업데이트 (RTR)
        String newRawRefreshToken = jwtProvider.createRefreshToken();
        String newHashedToken = refreshTokenHashService.hash(newRawRefreshToken);

        refreshToken.updateToken(newHashedToken, jwtProvider.getRefreshExpiration());
        String newAccessToken = jwtProvider.createAccessToken(refreshToken.getMemberId());

        return new AuthTokenResponse(newAccessToken, newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hashedToken = refreshTokenHashService.hash(rawRefreshToken);
        refreshTokenRepository.deleteByTokenHash(hashedToken);
    }

    private AuthTokenResponse issueTokensForActiveMember(Long memberId) {
        if (!isActiveMember(memberId)) {
            throw new BusinessException(ErrorCode.AUTH_INACTIVE_MEMBER);
        }
        return issueTokens(memberId);
    }

    // 인증 방식과 무관하게 재사용하는 내부 토큰 발급 로직
    private AuthTokenResponse issueTokens(Long memberId) {
        String accessToken = jwtProvider.createAccessToken(memberId);
        String rawRefreshToken = jwtProvider.createRefreshToken();

        // DB 저장 시에는 HMAC-SHA-256 해시값만 저장
        RefreshToken refreshTokenEntity = RefreshToken.create(
            memberId,
            refreshTokenHashService.hash(rawRefreshToken),
            jwtProvider.getRefreshExpiration()
        );
        refreshTokenRepository.save(refreshTokenEntity);

        // 클라이언트에게는 평문 반환
        return new AuthTokenResponse(accessToken, rawRefreshToken);
    }

    private boolean isActiveMember(Long memberId) {
        return memberRepository.existsByIdAndStatus(memberId, MemberStatus.ACTIVE);
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
        }
        return dummyPasswordHash;
    }
}
