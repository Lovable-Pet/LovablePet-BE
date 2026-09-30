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
import java.util.Locale;

@Entity
@Table(name = "local_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class LocalCredential {
    //로컬 계정 정보

    /** 아이디 형식: 영문·숫자·밑줄(_) 4~20자. 대문자로 입력해도 소문자로 저장한다. */
    public static final String USERNAME_REGEX = "^[A-Za-z0-9_]{4,20}$";
    public static final String USERNAME_MESSAGE = "아이디는 영문, 숫자, 밑줄(_)로 4~20자여야 합니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //db의 id

    // member 생성 시 db에서 BIGSERIAL로 부여받는 id를 받아서 사용
    @Column(nullable = false, unique = true)
    private Long memberId;

    // 로그인 아이디
    @Column(nullable = false, unique = true, length = 20)
    private String username;

    // 연락·계정 찾기용 (로그인에는 사용하지 않음)
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
    private LocalCredential(Long memberId, String username, String email, String passwordHash) {
        this.memberId = memberId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static LocalCredential create(Long memberId, String username, String email, String passwordHash) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        validateInputs(memberId, normalizedUsername, normalizedEmail, passwordHash);
        return LocalCredential.builder()
            .memberId(memberId)
            .username(normalizedUsername)
            .email(normalizedEmail)
            .passwordHash(passwordHash)
            .build();
    }

    // 아이디 비교/저장 기준: 앞뒤 공백 제거 + 소문자 (대소문자 구분 없음)
    public static String normalizeUsername(String username) {
        return username != null ? username.trim().toLowerCase(Locale.ROOT) : null;
    }

    // 이메일 비교/저장 기준: 앞뒤 공백 제거 + 소문자
    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    // 비밀번호 해쉬값 변경
    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "새 비밀번호 해시값은 필수입니다.");
        }
        this.passwordHash = newPasswordHash;
    }

    private static void validateInputs(Long memberId, String username, String email, String passwordHash) {
        if (memberId == null) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "회원 ID는 필수입니다.");
        }
        if (username == null || username.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "아이디는 필수입니다.");
        }
        if (email == null || email.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "이메일은 필수입니다.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_INPUT, "비밀번호 해시값은 필수입니다.");
        }
    }
}
