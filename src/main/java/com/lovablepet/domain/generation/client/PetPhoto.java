package com.lovablepet.domain.generation.client;

import org.springframework.http.MediaType;

import java.util.Optional;

/**
 * 파이프라인에 보낼 사진. 공공데이터 사진 서버는 형식을 알려 주지 않으므로(application/octet-stream)
 * 파일 앞부분 바이트로 jpeg·png·webp 를 판별한다. 파이프라인은 이 세 형식만 받는다(그 밖은 415).
 */
public record PetPhoto(byte[] bytes, MediaType mediaType, String filename) {

    /** 파이프라인 한도 (넘으면 413) */
    public static final int MAX_BYTES = 15 * 1024 * 1024;
    public static final MediaType IMAGE_WEBP = new MediaType("image", "webp");

    /** jpeg·png·webp 이고 15MB 이하면 사진, 아니면 empty */
    public static Optional<PetPhoto> from(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new PetPhoto(bytes, MediaType.IMAGE_JPEG, "photo.jpg"));
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G')) {
            return Optional.of(new PetPhoto(bytes, MediaType.IMAGE_PNG, "photo.png"));
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(new PetPhoto(bytes, IMAGE_WEBP, "photo.webp"));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
