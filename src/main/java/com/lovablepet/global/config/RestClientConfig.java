package com.lovablepet.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Python AI 서버 호출용 RestClient 설정 (WebFlux 없이 spring-web 내장 RestClient 사용).
 * generation 도메인의 Python 클라이언트에서 {@code petgenRestClient} 빈을 주입받아 사용한다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient petgenRestClient(RestClient.Builder builder, PetgenProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
