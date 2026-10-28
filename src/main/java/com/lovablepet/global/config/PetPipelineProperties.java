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
        // POST /v1/fit 은 5~20초, 배포 직후 첫 호출은 배경 제거 Lambda 와 생김새 모델을 처음 읽느라 30초 넘게 걸린다. 줄이지 말 것
        @DefaultValue("90s") Duration readTimeout,
        String scripted,                // 비용 없는 오프라인 대본 이름(예: black_shiba). 연동 시험용, 운영에서는 비워 둔다
        @DefaultValue("30") int dailyLimit   // 하루(한국 날짜) AR 생성 시도 상한. 실패한 시도도 비용이 들어 센다
) {
}
