package com.lovablepet.domain.auth.repository;

import com.lovablepet.domain.auth.entity.LocalCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    /**
     * 이메일로 로컬 인증 정보를 조회합니다. (로그인 시 사용)
     */
    Optional<LocalCredential> findByEmail(String email);

    /**
     * 특정 이메일이 이미 가입되어 있는지 확인합니다. (회원가입 중복 체크 시 사용)
     */
    boolean existsByEmail(String email);

    /**
     * 회원 ID를 기반으로 로컬 인증 정보를 조회합니다.
     */
    Optional<LocalCredential> findByMemberId(Long memberId);
}
