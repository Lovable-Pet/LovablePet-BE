package com.lovablepet.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lovablePetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lovable Pet API")
                        .description("유기동물 입양 매칭 + AR 서비스 백엔드 API")
                        .version("v0.0.1"));
    }
}
