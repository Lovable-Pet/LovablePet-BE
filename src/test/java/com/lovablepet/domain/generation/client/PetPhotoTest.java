package com.lovablepet.domain.generation.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PetPhotoTest {

    @Test
    @DisplayName("파일 앞부분으로 jpeg·png·webp 를 판별한다")
    void detectsType() {
        assertThat(PetPhoto.from(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0))).get()
                .satisfies(photo -> {
                    assertThat(photo.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
                    assertThat(photo.filename()).isEqualTo("photo.jpg");
                });
        assertThat(PetPhoto.from(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A))).get()
                .extracting(PetPhoto::mediaType).isEqualTo(MediaType.IMAGE_PNG);

        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(PetPhoto.from(webp)).get()
                .satisfies(photo -> {
                    assertThat(photo.mediaType()).isEqualTo(PetPhoto.IMAGE_WEBP);
                    assertThat(photo.filename()).isEqualTo("photo.webp");
                });
    }

    @Test
    @DisplayName("다른 형식(gif, html 오류 페이지 등)이나 15MB 초과는 사진으로 보지 않는다")
    void rejectsOthers() {
        assertThat(PetPhoto.from("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(PetPhoto.from("<html>404</html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(PetPhoto.from(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(PetPhoto.from(null)).isEmpty();

        byte[] huge = new byte[PetPhoto.MAX_BYTES + 1];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;
        assertThat(PetPhoto.from(huge)).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
