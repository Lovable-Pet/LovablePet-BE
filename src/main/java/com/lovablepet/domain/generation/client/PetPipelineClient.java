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
import org.springframework.util.StringUtils;
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
    private final String scripted;

    public PetPipelineClient(
            @Qualifier("petPipelineRestClient") RestClient petPipelineRestClient,
            JsonMapper jsonMapper,
            PetPipelineProperties properties
    ) {
        this.petPipelineRestClient = petPipelineRestClient;
        this.jsonMapper = jsonMapper;
        this.callbackUrl = properties.callbackUrl();
        this.scripted = properties.scripted();
    }

    /**
     * 5~20초(배포 직후 첫 호출은 30초 넘게), 약 $0.02: 사진이 쓸 만한지, 템플릿이 이 개에 맞는지, 실제 크기는 얼마인지 판정한다.
     * 사진이 거부되면 fit.photo_rejected 에 사유 코드가 오고 size·shape·photo 는 null 이다.
     * 판정은 파이프라인이 24시간 기억해서, 같은 바이트의 사진으로 createJob 을 부르면 사진 판정을 다시 하지 않는다.
     */
    public FitResult fit(PetRecord record, PetPhoto photo) {
        return petPipelineRestClient.post().uri("/v1/fit")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form(record, photo))
                .retrieve().body(FitResult.class);
    }

    /**
     * 모델 생성을 등록한다(1.5~4분). 바로 status "queued"로 돌아오고, 결과는 콜백으로 온다.
     * fitStop 이면 템플릿 부적합일 때 비싼 단계 전에 멈추고 unsuitable 로 끝난다. 사진 거부(photo_rejected)는 fitStop 과 상관없다.
     */
    public Job createJob(PetRecord record, PetPhoto photo, boolean fitStop) {
        MultiValueMap<String, Object> form = form(record, photo);
        if (StringUtils.hasText(callbackUrl)) {
            form.add("callback_url", callbackUrl);
        }
        form.add("fit_stop", String.valueOf(fitStop));
        if (StringUtils.hasText(scripted)) {
            form.add("scripted", scripted); // 파이프라인이 PIPELINE_ALLOW_SCRIPTED=1 일 때만 받는다 (아니면 403)
        }
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
    private MultiValueMap<String, Object> form(PetRecord record, PetPhoto photo) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("record", part(jsonMapper.writeValueAsString(record), JSON_UTF8));
        HttpHeaders photoHeaders = new HttpHeaders();
        photoHeaders.setContentType(photo.mediaType());
        form.add("photo", new HttpEntity<>(new ByteArrayResource(photo.bytes()) {
            @Override
            public String getFilename() {
                return photo.filename();
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
