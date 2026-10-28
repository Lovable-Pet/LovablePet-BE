package com.lovablepet.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(/swagger-ui.html)와 OpenAPI 문서(/v3/api-docs) 설정.
 * 운영(prod)에서는 SWAGGER_ENABLED=true 일 때만 열린다.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final String DESCRIPTION = """
            유기동물 입양 매칭 + AR 서비스 백엔드 API

            - **인증**: `/api/auth/**` 는 토큰 없이 호출한다. 그 외 API 는 `Authorization: Bearer {accessToken}` 헤더가 필요하다.
              오른쪽 위 **Authorize** 에 로그인·회원가입 응답의 `accessToken` 을 넣으면 이후 요청에 자동으로 붙는다.
            - **토큰 만료**: 액세스 토큰(기본 30분)이 만료되면 `AUTH_401_TOKEN` 이 온다. `POST /api/auth/reissue` 로 재발급하고,
              이때 리프레시 토큰(기본 14일)도 새 값으로 바뀐다. 재발급까지 실패(`AUTH_401_REFRESH`)하면 다시 로그인한다.
            - **응답**: 모든 API 는 공통 응답 포맷(ApiResponse)으로 감싼다. 실패하면 `error.code` 로 원인을 구분한다.
            """;

    @Bean
    public OpenAPI lovablePetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lovable Pet API")
                        .description(DESCRIPTION)
                        .version("v0.0.1"))
                // Authorize 버튼: JWT 액세스 토큰을 넣으면 Authorization: Bearer 헤더로 보낸다.
                // 인증이 필요 없는 컨트롤러는 @SecurityRequirements 로 이 기본값을 비운다.
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
