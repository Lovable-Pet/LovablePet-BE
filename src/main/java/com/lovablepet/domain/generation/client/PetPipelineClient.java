package com.lovablepet.domain.generation.client;

import com.lovablepet.domain.generation.client.PetPipelineDtos.FitResult;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.PetRecord;
import com.lovablepet.global.config.PetPipelineProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Python 펫 파이프라인(uvicorn api.server:app) 호출. 계약은 LovablePet-AI-AR 저장소 docs/API.md.
 * RestClient 는 RestClientConfig 의 petPipelineRestClient(HTTP/1.1 고정, X-API-Key 기본 헤더)를 쓴다.
 */
@Component
public class PetPipelineClient {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final RestClient petPipelineRestClient;
    private final JsonMapper jsonMapper;
    private final String callbackUrl;

    public PetPipelineClient(
            @Qualifier("petPipelineRestClient") RestClient petPipelineRestClient,
            JsonMapper jsonMapper,
            PetPipelineProperties properties
    ) {
        this.petPipelineRestClient = petPipelineRestClient;
        this.jsonMapper = jsonMapper;
        this.callbackUrl = properties.callbackUrl();
    }

    /** 5~20초, 약 $0.013: 템플릿이 이 개에 맞는지, 실제 크기는 얼마인지 판정한다. */
    public FitResult fit(PetRecord record, byte[] photo, String photoName) {
        return petPipelineRestClient.post().uri("/v1/fit")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form(record, photo, photoName))
                .retrieve().body(FitResult.class);
    }

    /** 모델 생성을 등록한다(1.5~4분). 바로 status "queued"로 돌아오고, 결과는 콜백으로 온다. */
    public Job createJob(PetRecord record, byte[] photo, String photoName, boolean fitStop) {
        MultiValueMap<String, Object> form = form(record, photo, photoName);
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            form.add("callback_url", callbackUrl);
        }
        form.add("fit_stop", String.valueOf(fitStop));
        return petPipelineRestClient.post().uri("/v1/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve().body(Job.class);
    }

    public Job getJob(String jobId) {
        return petPipelineRestClient.get().uri("/v1/jobs/{id}", jobId).retrieve().body(Job.class);
    }

    /** model.glb, texture.png 또는 render_side.png / render_face.png / render_body.png */
    public byte[] download(String jobId, String fileName) {
        return petPipelineRestClient.get().uri("/v1/jobs/{id}/files/{name}", jobId, fileName).retrieve().body(byte[].class);
    }

    public void delete(String jobId) {
        petPipelineRestClient.delete().uri("/v1/jobs/{id}", jobId).retrieve().toBodilessEntity();
    }

    // MultipartBodyBuilder 대신 파트를 직접 담은 MultiValueMap 을 쓴다:
    // Spring 7 의 MultipartBodyBuilder 는 reactive-streams 가 필요해 webmvc 프로젝트에서 NoClassDefFoundError 가 난다
    private MultiValueMap<String, Object> form(PetRecord record, byte[] photo, String photoName) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("record", part(jsonMapper.writeValueAsString(record), JSON_UTF8));
        HttpHeaders photoHeaders = new HttpHeaders();
        photoHeaders.setContentType(photoName.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG);
        form.add("photo", new HttpEntity<>(new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return photoName;
            }
        }, photoHeaders));
        return form;
    }

    private static HttpEntity<String> part(String value, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        return new HttpEntity<>(value, headers);
    }
}
