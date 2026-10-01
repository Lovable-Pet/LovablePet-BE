package com.lovablepet.global.response;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 응답 부가 정보.
 * timestamp 는 서버가 어디서 실행되든(로컬/Docker) 항상 한국 시간(KST)으로, 초 단위까지 표시한다. 예) 2026-06-30T22:10:00
 * path 는 요청 경로(쿼리스트링 제외)다.
 */
public record ResponseMeta(
        String timestamp,
        String path
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static ResponseMeta of(String path) {
        return new ResponseMeta(LocalDateTime.now(KST).format(TIMESTAMP_FORMAT), path);
    }
}
