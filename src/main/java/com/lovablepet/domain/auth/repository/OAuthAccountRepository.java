package com.lovablepet.domain.auth.repository;

import com.lovablepet.domain.auth.entity.OAuthAccount;
import com.lovablepet.domain.auth.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount,Long> {

    /**
     * 소셜 제공자(ex. KAKAO)와 제공자가 발급한 고유 ID로 연동된 계정을 찾습니다.
     * (카카오 로그인 성공 후, 우리 서비스에 이미 가입된 유저인지 확인할 때 사용)
     */
    Optional<OAuthAccount> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    /**
     * 특정 소셜 플랫폼과 고유 ID 조합이 이미 가입되어 있는지 확인합니다.
     */
    boolean existsByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    /**
     * 회원 ID를 기반으로 소셜 연동 정보를 조회합니다.
     */
    Optional<OAuthAccount> findByMemberId(Long memberId);
}
