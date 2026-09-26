package com.lovablepet.domain.auth.repository;

import com.lovablepet.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    /**
     * 해싱된 토큰 값으로 RefreshToken 엔티티를 찾습니다.
     * (클라이언트가 엑세스 토큰 재발급을 요청할 때 사용)
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 특정 회원의 모든 리프레시 토큰을 삭제합니다.
     * (모든 기기에서 로그아웃 처리, 또는 회원 탈퇴 시 사용)
     */
    void deleteByMemberId(Long memberId);

    /**
     * 특정 해싱된 토큰 값을 삭제합니다.
     * (현재 사용 중인 기기에서만 단일 로그아웃 처리할 때 사용)
     */
    void deleteByTokenHash(String tokenHash);
}
