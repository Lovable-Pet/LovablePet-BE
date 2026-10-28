package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPhoto;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PetPhotoSourceTest {

    private static final String URL = "https://openapi.animal.go.kr/files/a.jpg";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T03:00:00Z"));

    @Test
    @DisplayName("한 번 받은 사진은 30분 동안 같은 바이트를 그대로 돌려준다 (파이프라인이 사진 판정을 다시 쓰도록)")
    void reusesBytesWithinTtl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(JPEG, MediaType.APPLICATION_OCTET_STREAM));
        PetPhotoSource source = new PetPhotoSource(builder.build(), clock);

        PetPhoto first = source.get(URL);
        clock.advance(Duration.ofMinutes(29));
        PetPhoto second = source.get(URL);

        assertThat(second.bytes()).isSameAs(first.bytes());
        assertThat(first.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        server.verify();
    }

    @Test
    @DisplayName("적합도 확인에서 거부된 사진은 30분 동안 기억하고, 지나면 잊는다")
    void remembersRejection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.twice(), requestTo(URL))
                .andRespond(withSuccess(JPEG, MediaType.APPLICATION_OCTET_STREAM));
        PetPhotoSource source = new PetPhotoSource(builder.build(), clock);

        source.get(URL);
        source.rememberRejection(URL, List.of("person_covers"));
        assertThat(source.rejection(URL)).containsExactly("person_covers");

        clock.advance(Duration.ofMinutes(31));
        assertThat(source.rejection(URL)).isEmpty();
        source.get(URL); // 기한이 지나 다시 받는다
        server.verify();
    }

    @Test
    @DisplayName("사진을 못 받거나(404) 사진이 아니면 GENERATION_502_PHOTO")
    void downloadFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>오류</html>", MediaType.TEXT_HTML));
        PetPhotoSource source = new PetPhotoSource(builder.build(), clock);

        assertThatThrownBy(() -> source.get(URL)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_PHOTO_UNAVAILABLE);
        assertThatThrownBy(() -> source.get(URL)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("jpeg·png·webp")
                .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_PHOTO_UNAVAILABLE);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
