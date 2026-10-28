package com.lovablepet.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(
        Long id,
        Properties properties
) {

    public String nickname() {
        return properties == null ? null : properties.nickname();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(String nickname) {
    }
}
