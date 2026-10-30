package com.lovablepet.global.security;

import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorResponseWriterTest {

    @Test
    @DisplayName("보안 필터 단계의 401 응답도 공통 실패 형식(resultType/success/error/meta)으로 나간다")
    void writesCommonFailFormat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityErrorResponseWriter.write(request, response, ErrorCode.AUTH_INVALID_TOKEN);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .startsWith("{\"resultType\":\"FAIL\",\"success\":null,")
                .contains("\"code\":\"AUTH_401_TOKEN\"")
                .contains("\"details\":null")
                .contains("\"path\":\"/api/pets\"")
                .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"");
    }
}
