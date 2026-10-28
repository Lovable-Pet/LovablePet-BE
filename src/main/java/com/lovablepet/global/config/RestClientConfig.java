package com.lovablepet.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 외부 서버 호출용 RestClient 설정 (WebFlux 없이 spring-web 내장 RestClient 사용).
 */
@Configuration
public class RestClientConfig {

    /**
     * Python 펫 파이프라인 호출용. generation 도메인의 PetPipelineClient 가 주입받아 사용한다.
     * HTTP/1.1 고정: JDK HttpClient 는 기본으로 "Upgrade: h2c"를 붙여 보내는데,
     * 파이프라인(uvicorn)은 그 요청의 본문을 읽지 않아 422 "record/photo missing"으로 답한다.
     */
    @Bean
    public RestClient petPipelineRestClient(RestClient.Builder builder, PetPipelineProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-API-Key", properties.apiKey())
                .build();
    }

    @Bean
    public RestClient kakaoAuthRestClient(RestClient.Builder builder, KakaoOAuthProperties properties) {
        return builder
                .baseUrl(properties.authBaseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public RestClient kakaoApiRestClient(RestClient.Builder builder, KakaoOAuthProperties properties) {
        return builder
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(java.time.Duration connectTimeout, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
