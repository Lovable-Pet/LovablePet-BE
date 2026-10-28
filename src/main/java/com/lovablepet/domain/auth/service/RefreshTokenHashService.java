package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.exception.AuthException;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class RefreshTokenHashService {

    private final String secretKey;
    private static final String ALGORITHM = "HmacSHA256";

    // JWT 서명키와는 별도의 환경변수를 주입받습니다.
    public RefreshTokenHashService(@Value("${app.auth.token-hash-secret}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hashBytes = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            // DB에 저장하기 좋도록 Base64 인코딩하여 반환 (VARCHAR 길이에 충분히 수용됨)
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new AuthException(ErrorCode.INTERNAL_ERROR, "토큰 해싱 중 오류가 발생했습니다.");
        }
    }
}
