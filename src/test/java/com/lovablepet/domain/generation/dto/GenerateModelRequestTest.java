package com.lovablepet.domain.generation.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateModelRequestTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("force 를 빼고 보내도 읽히고 false 다 (Jackson 3 은 빠진 원시 타입 값을 거부한다)")
    void forceDefaultsToFalse() {
        GenerateModelRequest request = jsonMapper.readValue("{\"photoIndex\":1}", GenerateModelRequest.class);

        assertThat(request.photoIndex()).isEqualTo(1);
        assertThat(request.force()).isFalse();
        assertThat(request.weightKg()).isNull();
        assertThat(jsonMapper.readValue("{\"photoIndex\":0,\"force\":true}", GenerateModelRequest.class).force()).isTrue();
    }
}
