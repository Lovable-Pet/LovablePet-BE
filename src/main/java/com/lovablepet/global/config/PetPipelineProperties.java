package com.lovablepet.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Python 3D 생성(AI) 파이프라인 연동 설정. application.yml 의 pet-pipeline.* 에 바인딩된다.
 * HTTP 계약은 LovablePet-AI-AR 저장소의 docs/API.md 를 따른다.
 */
@Validated
@ConfigurationProperties(prefix = "pet-pipeline")
public record PetPipelineProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,        // 파이프라인의 PIPELINE_API_KEY 와 같은 값. 요청과 콜백 검증 모두 X-API-Key 로 쓴다
        String callbackUrl,             // 비우면 콜백 없이 GET /v1/jobs/{id} 로 폴링해야 한다
        @NotBlank @DefaultValue("models") String modelDir,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("90s") Duration readTimeout   // POST /v1/fit 이 5~20초 걸린다
) {
}
