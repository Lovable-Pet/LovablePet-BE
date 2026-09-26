package com.lovablepet.domain.member.entity;

import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 외부 무분별한 객체 생성을 막기 위해 빌더 패턴 접근 제한
    @Builder(access = AccessLevel.PRIVATE)
    private Member(String nickname, MemberStatus status) {
        this.nickname = nickname;
        // 상태 값이 넘어오지 않으면 기본값으로 ACTIVE를 설정합니다.
        this.status = status != null ? status : MemberStatus.ACTIVE;
    }

    // ==========================================================
    // 정적 팩토리 메서드
    // ==========================================================

    /**
     * 로컬/카카오 모두 Member 객체 생성 후 인증 수행
     */
    public static Member create(String nickname) {
        validateNickname(nickname);
        return Member.builder()
            .nickname(nickname)
            .status(MemberStatus.ACTIVE)
            .build();
    }

   //닉네임 변경
    public void changeNickname(String newNickname) {
        validateNickname(newNickname); // 수정 시 공백 검증
        this.nickname = newNickname;
    }

   //회원탈퇴(물리적 삭제는 아니고 회원상태만 변경)
    public void withdraw() {
        this.status = MemberStatus.DELETED;
    }

    //닉네임 검증 공통 로직(생성, 수정 시 적용)
    private static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 공백일 수 없습니다.");
        }
        if (nickname.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 50자를 초과할 수 없습니다.");
        }
    }
}
