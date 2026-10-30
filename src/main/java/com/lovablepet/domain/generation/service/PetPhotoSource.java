package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.client.PetPhoto;
import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자가 고른 공고 사진을 내려받는다.
 * <p>
 * 받은 바이트를 30분 동안 보관해, 적합도 확인과 생성에 바이트가 같은 사진을 넘긴다.
 * 파이프라인은 사진 판정을 24시간 기억하는데 바이트가 같을 때만 다시 쓰기 때문이다.
 * 적합도 확인에서 사진이 거부되면 그 사실도 함께 기억해, 같은 사진으로 생성하려 하면 막는다(force 로도 못 넘는다).
 */
@Component
public class PetPhotoSource {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_ENTRIES = 50; // 사진 한 장 수백 KB

    private final RestClient petPhotoRestClient;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public PetPhotoSource(@Qualifier("petPhotoRestClient") RestClient petPhotoRestClient, Clock clock) {
        this.petPhotoRestClient = petPhotoRestClient;
        this.clock = clock;
    }

    public PetPhoto get(String url) {
        Entry entry = fresh(url);
        if (entry != null) {
            return entry.photo();
        }
        PetPhoto photo = PetPhoto.from(download(url)).orElseThrow(() -> new GenerationException(
                ErrorCode.GENERATION_PHOTO_UNAVAILABLE, "jpeg·png·webp 이고 15MB 이하인 사진만 쓸 수 있습니다. 다른 사진을 골라 주세요."));
        put(url, new Entry(photo, List.of(), clock.instant()));
        return photo;
    }

    /** 적합도 확인 결과의 사진 거부 사유 코드를 기억한다 (통과면 빈 목록) */
    public void rememberRejection(String url, List<String> photoRejected) {
        Entry entry = fresh(url);
        if (entry != null) {
            put(url, new Entry(entry.photo(), photoRejected == null ? List.of() : List.copyOf(photoRejected), entry.fetchedAt()));
        }
    }

    /** 30분 안에 적합도 확인에서 거부된 사진이면 그 사유 코드, 아니면 빈 목록 */
    public List<String> rejection(String url) {
        Entry entry = fresh(url);
        return entry == null ? List.of() : entry.photoRejected();
    }

    private byte[] download(String url) {
        try {
            return petPhotoRestClient.get().uri(url).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new GenerationException(ErrorCode.GENERATION_PHOTO_UNAVAILABLE);
        }
    }

    private Entry fresh(String url) {
        Entry entry = entries.get(url);
        if (entry == null || entry.fetchedAt().plus(TTL).isBefore(clock.instant())) {
            return null;
        }
        return entry;
    }

    private void put(String url, Entry entry) {
        Instant expired = clock.instant().minus(TTL);
        entries.values().removeIf(e -> e.fetchedAt().isBefore(expired));
        while (entries.size() >= MAX_ENTRIES && !entries.containsKey(url)) {
            entries.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().fetchedAt()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
        entries.put(url, entry);
    }

    private record Entry(PetPhoto photo, List<String> photoRejected, Instant fetchedAt) {
    }
}
