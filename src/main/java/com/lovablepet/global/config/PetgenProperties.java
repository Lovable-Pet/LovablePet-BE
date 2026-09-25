package com.lovablepet.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Python 3D 생성(AI) 서버 연동 설정. application.yml 의 petgen.* 에 바인딩된다.
 */
@Validated
@ConfigurationProperties(prefix = "petgen")
public record PetgenProperties(
        @NotBlank String baseUrl,
        @NotBlank String token,
        @NotBlank String callbackUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout
) {
}
