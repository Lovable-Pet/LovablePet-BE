package com.lovablepet.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 공공데이터포털 구조동물 조회 서비스 (농림축산검역본부 abandonmentPublicService_v2).
 *
 * @param serviceKey 공공데이터포털 일반 인증키. Decoding 값을 넣는다(Encoding 값이면 디코딩해서 쓴다).
 *                   비어 있으면 동기화를 건너뛴다 (로컬·CI).
 */
@Validated
@ConfigurationProperties(prefix = "public-animal")
public record PublicAnimalProperties(
        @NotBlank @DefaultValue("https://apis.data.go.kr/1543061/abandonmentPublicService_v2") String baseUrl,
        String serviceKey,
        @DefaultValue("1000") int pageSize,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout) {

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
