package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.dto.AuthTokenResponse;
import com.lovablepet.domain.auth.entity.LocalCredential;
import com.lovablepet.domain.auth.entity.RefreshToken;
import com.lovablepet.domain.auth.repository.LocalCredentialRepository;
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

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // 역할이 분리된 두 개의 암호화 컴포넌트 주입
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenHashService refreshTokenHashService;
    private final JwtProvider jwtProvider;

    @Transactional
    public void signUpLocal(String email, String rawPassword, String nickname) {
        // 1. 이메일 정규화 및 중복 검증
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
        if (localCredentialRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 가입된 이메일입니다.");
        }

        // 2. 회원 생성
        Member member = Member.create(nickname);
        memberRepository.save(member);

        // 3. 비밀번호는 DelegatingPasswordEncoder로 인코딩 ({bcrypt}...)
        String encodedPassword = passwordEncoder.encode(rawPassword);
        LocalCredential credential = LocalCredential.create(member.getId(), normalizedEmail, encodedPassword);
        localCredentialRepository.save(credential);
    }

    @Transactional
    public AuthTokenResponse loginLocal(String email, String rawPassword) {
        String normalizedEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
        LocalCredential credential = localCredentialRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "가입되지 않은 이메일입니다."));

        // 비밀번호 검증은 무조건 matches() 사용
        if (!passwordEncoder.matches(rawPassword, credential.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        return issueTokensForActiveMember(credential.getMemberId());
    }

    @Transactional
    public AuthTokenResponse reissueToken(String rawRefreshToken) {
        // HMAC-SHA-256은 결정적이므로, 입력받은 토큰을 해싱해서 바로 DB 조회 가능
        String hashedToken = refreshTokenHashService.hash(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        if (!isActiveMember(refreshToken.getMemberId())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "활성 상태가 아닌 회원입니다.");
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

    @Transactional
    public AuthTokenResponse issueTokensForActiveMember(Long memberId) {
        ensureActiveMember(memberId);
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

    private void ensureActiveMember(Long memberId) {
        if (!isActiveMember(memberId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "활성 상태가 아닌 회원입니다.");
        }
    }

    private boolean isActiveMember(Long memberId) {
        return memberRepository.findByIdAndStatus(memberId, MemberStatus.ACTIVE).isPresent();
    }
}
