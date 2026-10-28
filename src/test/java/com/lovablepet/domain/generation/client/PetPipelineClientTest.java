package com.lovablepet.domain.generation.client;

import com.lovablepet.domain.generation.client.PetPipelineDtos.FitResult;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import com.lovablepet.domain.generation.client.PetPipelineDtos.PetRecord;
import com.lovablepet.global.config.PetPipelineProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PetPipelineClientTest {

    private static final String BASE_URL = "http://pipeline:8000";
    private static final PetRecord RECORD = new PetRecord("448567202600123", "개", 5.5, "갈색&흰색",
            "믹스견 · 경기도 화성시 · 5.5kg", "F", false, "2024(년생)");
    private static final PetPhoto WEBP = PetPhoto.from("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)).orElseThrow();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("작업 등록: 사진은 판별한 형식(webp)으로, 기록은 JSON 으로, fit_stop·callback_url·scripted 를 함께 보낸다")
    void createJob_sendsMultipart() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetPipelineClient client = client(builder, "black_shiba");
        server.expect(requestTo(BASE_URL + "/v1/jobs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body)
                            .contains("Content-Type: image/webp")
                            .contains("filename=\"photo.webp\"")
                            .contains("\"pet_id\":\"448567202600123\"")
                            .contains("\"neutered\":false")
                            .containsPattern("name=\"fit_stop\"[\\s\\S]*?true")
                            .containsPattern("name=\"callback_url\"[\\s\\S]*?http://spring:8080/internal/pet-models/callback")
                            .containsPattern("name=\"scripted\"[\\s\\S]*?black_shiba");
                })
                .andRespond(withSuccess("{\"job_id\":\"448567202600123-a5ca257e\",\"pet_id\":\"448567202600123\",\"status\":\"queued\"}",
                        MediaType.APPLICATION_JSON));

        Job job = client.createJob(RECORD, WEBP, true);

        assertThat(job.jobId()).isEqualTo("448567202600123-a5ca257e");
        assertThat(job.isFinished()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("작업 등록: scripted 를 설정하지 않으면 보내지 않는다 (운영)")
    void createJob_withoutScripted() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/jobs"))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8))
                        .doesNotContain("name=\"scripted\""))
                .andRespond(withSuccess("{\"job_id\":\"j1\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        client(builder, "").createJob(RECORD, WEBP, false);

        server.verify();
    }

    @Test
    @DisplayName("적합도: 사진이 거부되면 size·shape·photo 가 null 이고 숫자도 비어 있어도 읽는다")
    void fit_photoRejectedWithNulls() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/fit"))
                .andRespond(withSuccess("""
                        {"pet_id":"448567202600123",
                         "fit":{"level":"poor","label":"부적합","score":null,"reasons":["사람 손이 털을 가림"],"notes":[],
                                "template":null,"photo_rejected":["person_covers"]},
                         "size":null,"shape":null,"photo":null}""", MediaType.APPLICATION_JSON));

        FitResult result = client(builder, "").fit(RECORD, WEBP);

        assertThat(result.fit().isPhotoRejected()).isTrue();
        assertThat(result.fit().photoRejected()).containsExactly("person_covers");
        assertThat(result.size()).isNull();
        assertThat(result.photo()).isNull();
    }

    @Test
    @DisplayName("사진 판정이 없던 이전 버전 응답(photo_rejected 없음)은 거부가 아닌 것으로 본다")
    void fit_olderPipelineWithoutPhotoRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/fit"))
                .andRespond(withSuccess("""
                        {"pet_id":"448567202600123",
                         "fit":{"level":"good","label":"적합","score":100,"reasons":[],"notes":[],"template":"jindo"},
                         "size":{"weight_kg":5.5,"withers_m":0.312,"scale":0.61,"rule":"weight"}}""", MediaType.APPLICATION_JSON));

        FitResult result = client(builder, "").fit(RECORD, WEBP);

        assertThat(result.fit().isPhotoRejected()).isFalse();
        assertThat(result.size().withersM()).isEqualTo(0.312);
    }

    @Test
    @DisplayName("콜백 본문: photo_rejected 결과는 size·behavior 등이 없어도 읽는다")
    void job_photoRejectedBody() {
        Job job = jsonMapper.readValue("""
                {"job_id":"448567202600123-a5ca257e","pet_id":"448567202600123","status":"photo_rejected",
                 "reasons":["개가 여러 마리"],"error":"photo rejected","cost_usd":0.006}""", Job.class);

        assertThat(job.isFinished()).isTrue();
        assertThat(job.reasons()).containsExactly("개가 여러 마리");
        assertThat(job.size()).isNull();
    }

    private PetPipelineClient client(RestClient.Builder builder, String scripted) {
        PetPipelineProperties properties = new PetPipelineProperties(BASE_URL, "key",
                "http://spring:8080/internal/pet-models/callback", "models",
                Duration.ofSeconds(10), Duration.ofSeconds(90), scripted, 30, "/models");
        return new PetPipelineClient(builder.build(), jsonMapper, properties);
    }
}
