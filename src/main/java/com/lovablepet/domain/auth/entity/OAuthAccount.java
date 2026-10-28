package com.lovablepet.domain.auth.entity;

import com.lovablepet.domain.auth.exception.AuthException;
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
@Table(
    name = "oauth_account",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_oauth_provider_subject",
            columnNames = {"provider", "provider_subject"}
        )
    }
    //같은 카카오계정으로 2번 회원가입 막는 용도, 나중에 확장 시 카카오와 구글의 id가 우연히 겹칠 수 있으니까 privder,provider_subject를 같이unique로 묶음
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원당 하나의 OAuth 계정만 허용
    @Column(nullable = false, unique = true)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    // 카카오 등에서 사용자에게 부여하는 고유식별자(ID) - 카카오는 long타입이지만 확장성 위해 String을 저장함
    @Column(name = "provider_subject", nullable = false, length = 100)
    private String providerSubject;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private OAuthAccount(Long memberId, OAuthProvider provider, String providerSubject) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    public static OAuthAccount create(Long memberId, OAuthProvider provider, String providerSubject) {
        validateInputs(memberId, provider, providerSubject);
        return OAuthAccount.builder()
            .memberId(memberId)
            .provider(provider)
            .providerSubject(providerSubject)
            .build();
    }

    private static void validateInputs(Long memberId, OAuthProvider provider, String providerSubject) {
        if (memberId == null) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "회원 ID는 필수입니다.");
        }
        if (provider == null) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "소셜 제공자는 필수입니다.");
        }
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "소셜 식별자(Subject)는 필수입니다.");
        }
    }
}
