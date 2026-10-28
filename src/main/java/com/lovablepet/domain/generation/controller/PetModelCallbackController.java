package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.domain.generation.service.PetModelCallbackService;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 파이프라인이 작업을 끝내면 callback_url(이 주소)로 작업 JSON 을 POST 한다.
 * 2xx 가 아니면 파이프라인이 세 번까지 다시 보낸다. 콜백을 받을 수 없을 때는 GET /v1/jobs/{id} 폴링으로 같은 본문을 얻는다.
 * <p>
 * 앱이 부르는 API 가 아니므로 Swagger 에서 숨긴다. SecurityConfig 에서 JWT 인증 없이 허용하고,
 * 대신 파이프라인이 붙여 보내는 X-API-Key 를 여기서 검사한다.
 */
@Hidden
@RestController
public class PetModelCallbackController {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final PetModelCallbackService petModelCallbackService;
    private final byte[] apiKey;

    public PetModelCallbackController(PetModelCallbackService petModelCallbackService, PetPipelineProperties properties) {
        this.petModelCallbackService = petModelCallbackService;
        this.apiKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/pet-models/callback")
    public ApiResponse<Void> onJobFinished(
            @RequestHeader(value = API_KEY_HEADER, required = false) String key,
            @RequestBody Job job
    ) throws IOException {
        // 8080 은 인터넷에 열려 있으므로 키 비교에 걸리는 시간으로 키를 추측할 수 없게 상수 시간 비교를 쓴다
        if (key == null || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_API_KEY);
        }
        petModelCallbackService.handle(job);
        return ApiResponse.ok();
    }
}
