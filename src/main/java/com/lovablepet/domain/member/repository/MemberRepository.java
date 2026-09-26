package com.lovablepet.domain.member.repository;
import com.lovablepet.domain.member.entity.Member;
import com.lovablepet.domain.member.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByIdAndStatus(Long id, MemberStatus status);
}
