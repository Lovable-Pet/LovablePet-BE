package com.lovablepet.domain.auth.entity;

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
import java.util.Locale;

@Entity
@Table(name = "local_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class LocalCredential {
    //로컬 계정 정보
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //db의 id

    // member 생성 시 db에서 BIGSERIAL로 부여받는 id를 받아서 사용
    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private LocalCredential(Long memberId, String email, String passwordHash) {
        this.memberId = memberId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static LocalCredential create(Long memberId, String email, String passwordHash) {
        String normalizedEmail = normalizeEmail(email);
        validateInputs(memberId, normalizedEmail, passwordHash);
        return LocalCredential.builder()
            .memberId(memberId)
            .email(normalizedEmail)
            .passwordHash(passwordHash)
            .build();
    }

    // 이메일 비교/저장 기준: 앞뒤 공백 제거 + 소문자
    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    // 비밀번호 해쉬값 변경
    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호 해시값은 필수입니다.");
        }
        this.passwordHash = newPasswordHash;
    }

    private static void validateInputs(Long memberId, String email, String passwordHash) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원 ID는 필수입니다.");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일은 필수입니다.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호 해시값은 필수입니다.");
        }
    }
}
