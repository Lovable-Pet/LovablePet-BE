package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.client.PublicAnimalClient;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.client.PublicAnimalPage;
import com.lovablepet.domain.pet.dto.PetSyncResult;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.config.PublicAnimalProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PetSyncServiceTest {

    // 2026-10-29 12:00 (한국 시각)
    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PublicAnimalClient client;
    @Mock
    private PetSyncWriter writer;
    @Mock
    private PetRepository petRepository;

    @Test
    @DisplayName("인증키가 없으면 자동 동기화는 조용히 건너뛰고, 수동 실행은 PET_503_SYNC_KEY")
    void notConfigured() {
        PetSyncService service = service("", NOW);

        service.syncIfDue();
        verifyNoInteractions(client, writer, petRepository);

        assertThatThrownBy(service::sync)
                .isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PET_SYNC_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("오늘 새벽 4시(한국 시각) 이후에 동기화했으면 건너뛰고, 그 전이면 실행한다")
    void isDue_afterFourAm() {
        PetSyncService service = service("key", NOW);
        // 2026-10-29 04:00 KST = 2026-10-28 19:00 UTC
        assertThat(service.latestDailySyncTime()).isEqualTo(Instant.parse("2026-10-28T19:00:00Z"));

        given(petRepository.findLatestSyncedAt()).willReturn(Instant.parse("2026-10-28T19:30:00Z"));
        assertThat(service.isDue()).isFalse();

        given(petRepository.findLatestSyncedAt()).willReturn(Instant.parse("2026-10-28T18:59:00Z"));
        assertThat(service.isDue()).isTrue();

        given(petRepository.findLatestSyncedAt()).willReturn(null); // 처음 기동
        assertThat(service.isDue()).isTrue();
    }

    @Test
    @DisplayName("새벽 4시 전이면 전날 새벽 4시를 기준으로 본다")
    void latestDailySyncTime_beforeFourAm() {
        // 2026-10-29 03:30 KST
        PetSyncService service = service("key", Instant.parse("2026-10-28T18:30:00Z"));

        assertThat(service.latestDailySyncTime()).isEqualTo(Instant.parse("2026-10-27T19:00:00Z"));
    }

    @Test
    @DisplayName("처음 동기화: 공고중·보호중을 전체 건수만큼 페이지를 넘겨 받고, 수정분 조회는 하지 않는다")
    void sync_firstRun() {
        PetSyncService service = service("key", NOW);
        given(petRepository.findLatestSourceUpdatedAt()).willReturn(null);
        given(client.fetchByState("notice", 1)).willReturn(page(2, 2));
        given(client.fetchByState("protect", 1)).willReturn(page(2, 3));
        given(client.fetchByState("protect", 2)).willReturn(page(1, 3));
        given(writer.upsert(anyList(), eq(NOW))).willAnswer(invocation ->
                new PetSyncWriter.UpsertCount(invocation.<List<?>>getArgument(0).size(), 0, 0));
        given(writer.unlistNotSyncedSince(NOW)).willReturn(0);

        PetSyncResult result = service.sync();

        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.created()).isEqualTo(5);
        verify(client, never()).fetchByState("protect", 3);
        verify(client, never()).fetchUpdated(any(), any(), anyInt());
        verify(writer).unlistNotSyncedSince(NOW);
    }

    @Test
    @DisplayName("다음 동기화: 받아 둔 가장 최근 수정일(한국 날짜) 하루 전부터 오늘까지 수정분도 받는다")
    void sync_incrementalWindow() {
        PetSyncService service = service("key", NOW);
        // 2026-10-27 23:30 UTC = 2026-10-28 08:30 KST → 10-27 부터
        given(petRepository.findLatestSourceUpdatedAt()).willReturn(Instant.parse("2026-10-27T23:30:00Z"));
        given(client.fetchByState(any(), eq(1))).willReturn(page(0, 0));
        given(client.fetchUpdated(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 29), 1)).willReturn(page(1, 1));
        given(writer.upsert(anyList(), eq(NOW))).willReturn(new PetSyncWriter.UpsertCount(0, 1, 0));

        PetSyncResult result = service.sync();

        assertThat(result.updated()).isEqualTo(1);
        verify(client).fetchUpdated(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 29), 1);
    }

    @Test
    @DisplayName("전체 목록을 받다가 실패하면 목록 내리기를 하지 않고, 다음 실행은 다시 할 수 있다")
    void sync_failureSkipsUnlist() {
        PetSyncService service = service("key", NOW);
        given(client.fetchByState("notice", 1)).willThrow(new PetException(ErrorCode.EXTERNAL_API_ERROR, "실패"));

        assertThatThrownBy(service::sync).isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
        verify(writer, never()).unlistNotSyncedSince(any());

        // 실행 중 표시가 풀렸는지: 다시 부르면 PET_409_SYNC 가 아니라 같은 실패가 난다
        assertThatThrownBy(service::sync).extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("동기화 중에 또 실행하면 PET_409_SYNC")
    void sync_whileRunning() {
        PetSyncService service = service("key", NOW);
        given(client.fetchByState("notice", 1)).willAnswer(invocation -> {
            assertThatThrownBy(service::sync).extracting("errorCode").isEqualTo(ErrorCode.PET_SYNC_RUNNING);
            return page(0, 0);
        });
        given(client.fetchByState("protect", 1)).willReturn(page(0, 0));

        service.sync();

        verify(client).fetchByState("notice", 1);
    }

    @Test
    @DisplayName("성공하면 그날은 다시 실행하지 않는다")
    void syncIfDue_runsOncePerDay() {
        PetSyncService service = service("key", NOW);
        given(petRepository.findLatestSyncedAt()).willReturn(null);
        given(client.fetchByState(any(), eq(1))).willReturn(page(0, 0));

        service.syncIfDue();
        service.syncIfDue();

        verify(client).fetchByState("notice", 1);
        verify(client).fetchByState("protect", 1);
    }

    private PetSyncService service(String serviceKey, Instant now) {
        PublicAnimalProperties properties = new PublicAnimalProperties(
                "https://apis.example.test/svc", serviceKey, 1000, Duration.ofSeconds(10), Duration.ofSeconds(60));
        return new PetSyncService(client, writer, petRepository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private PublicAnimalPage page(int size, int totalCount) {
        List<PublicAnimalItem> items = IntStream.range(0, size)
                .mapToObj(i -> new PublicAnimalItem(jsonMapper.readTree("{\"desertionNo\":\"" + i + "\",\"processState\":\"보호중\"}")))
                .toList();
        return new PublicAnimalPage(items, totalCount);
    }
}
