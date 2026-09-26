package com.lovablepet.domain.auth.entity;


import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RefreshToken(Long memberId, String tokenHash, LocalDateTime expiryDate) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiryDate = expiryDate;
    }

    public static RefreshToken create(Long memberId, String tokenHash, LocalDateTime expiryDate) {
        validateInputs(memberId, tokenHash, expiryDate);
        return RefreshToken.builder()
            .memberId(memberId)
            .tokenHash(tokenHash)
            .expiryDate(expiryDate)
            .build();
    }

    // RTR 방식: 유저가 리프레시토큰 사용해 엑세스 토큰 재발급 시 리프레시 토큰도 새로 발급
    public void updateToken(String newTokenHash, LocalDateTime newExpiryDate) {
        if (this.isExpired()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 만료된 토큰은 갱신할 수 없습니다.");
        }

        if (newTokenHash == null || newTokenHash.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "갱신할 토큰 해시값은 필수입니다.");
        }
        if (newExpiryDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 만료 일시는 필수입니다.");
        }
        if (newExpiryDate.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 만료 일시는 현재 시간보다 과거일 수 없습니다.");
        }

        this.tokenHash = newTokenHash;
        this.expiryDate = newExpiryDate;
    }

    // 만료 여부 확인 로직
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    private static void validateInputs(Long memberId, String tokenHash, LocalDateTime expiryDate) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원 ID는 필수입니다.");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "토큰 해시값은 필수입니다.");
        }
        if (expiryDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "만료 일시는 필수입니다.");
        }
    }
}
