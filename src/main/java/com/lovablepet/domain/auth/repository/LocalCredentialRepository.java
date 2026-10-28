package com.lovablepet.domain.auth.repository;

import com.lovablepet.domain.auth.entity.LocalCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    /**
     * 아이디로 로컬 인증 정보를 조회합니다. (로그인 시 사용)
     */
    Optional<LocalCredential> findByUsername(String username);

    /**
     * 특정 아이디가 이미 사용 중인지 확인합니다. (회원가입 중복 체크, 아이디 중복 확인 API)
     */
    boolean existsByUsername(String username);

    /**
     * 이메일로 로컬 인증 정보를 조회합니다. (추후 아이디·비밀번호 찾기에서 사용)
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
