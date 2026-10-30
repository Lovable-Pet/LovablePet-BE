package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.client.PublicAnimalClient;
import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.client.PublicAnimalPage;
import com.lovablepet.domain.pet.dto.PetSyncResult;
import com.lovablepet.domain.pet.entity.PetSyncRun;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.domain.pet.repository.PetSyncRunRepository;
import com.lovablepet.global.config.PublicAnimalProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    // 2026-10-29(목) 12:00 한국 시각
    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    // 2026-10-26(월) 04:00 한국 시각
    private static final Instant THIS_MONDAY_4AM = Instant.parse("2026-10-25T19:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PublicAnimalClient client;
    @Mock
    private PetSyncWriter writer;
    @Mock
    private PetRepository petRepository;
    @Mock
    private PetSyncRunRepository runRepository;

    @Test
    @DisplayName("인증키가 없으면 자동 실행은 로그만 남기고, 관리자 실행은 PET_503_SYNC_KEY")
    void notConfigured() {
        PetSyncService service = service("", NOW);

        service.weeklySync();
        verifyNoInteractions(client, writer, petRepository, runRepository);

        assertThatThrownBy(service::sync)
                .isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PET_SYNC_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("기준 시각: 가장 최근의 월요일 새벽 4시 (한국 시각)")
    void latestWeeklySyncTime() {
        assertThat(service("key", NOW).latestWeeklySyncTime()).isEqualTo(THIS_MONDAY_4AM);

        // 월요일 03:00 이면 아직 이번 주 기준 전 → 지난주 월요일
        PetSyncService mondayBeforeFour = service("key", Instant.parse("2026-10-25T18:00:00Z"));
        assertThat(mondayBeforeFour.latestWeeklySyncTime()).isEqualTo(Instant.parse("2026-10-18T19:00:00Z"));
    }

    @Test
    @DisplayName("기동 시: 동기화 기록이 없거나 이번 주 월요일 새벽 4시 전이면 놓친 것으로 본다")
    void isOverdue() {
        PetSyncService service = service("key", NOW);

        given(runRepository.findFirstByOrderByStartedAtDesc()).willReturn(Optional.empty());
        assertThat(service.isOverdue()).isTrue();

        given(runRepository.findFirstByOrderByStartedAtDesc()).willReturn(Optional.of(run(THIS_MONDAY_4AM.minusSeconds(60))));
        assertThat(service.isOverdue()).isTrue();

        given(runRepository.findFirstByOrderByStartedAtDesc()).willReturn(Optional.of(run(THIS_MONDAY_4AM.plusSeconds(60))));
        assertThat(service.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("기동 시 이번 주 동기화를 이미 했으면 다시 하지 않는다")
    void catchUpOnStartup_skipsWhenDone() {
        PetSyncService service = service("key", NOW);
        given(runRepository.findFirstByOrderByStartedAtDesc()).willReturn(Optional.of(run(THIS_MONDAY_4AM.plusSeconds(60))));

        service.catchUpOnStartup();

        verifyNoInteractions(client, writer);
    }

    @Test
    @DisplayName("동기화: 공고중·보호중을 전체 건수만큼 받고, 지난번 이후 수정분도 받고, 빠진 동물을 내리고, 기록을 남긴다")
    void sync() {
        PetSyncService service = service("key", NOW);
        // 2026-10-19 23:30 UTC = 2026-10-20 08:30 한국 시각 → 수정분은 10-19 부터 오늘까지
        given(petRepository.findLatestSourceUpdatedAt()).willReturn(Instant.parse("2026-10-19T23:30:00Z"));
        given(client.fetchByState("notice", 1)).willReturn(page(2, 2));
        given(client.fetchByState("protect", 1)).willReturn(page(2, 3));
        given(client.fetchByState("protect", 2)).willReturn(page(1, 3));
        given(client.fetchUpdated(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 29), 1)).willReturn(page(1, 1));
        given(writer.upsert(anyList(), eq(NOW))).willAnswer(invocation ->
                new PetSyncWriter.UpsertCount(0, invocation.<List<?>>getArgument(0).size(), 0));
        given(writer.unlistNotSyncedSince(NOW)).willReturn(4);

        PetSyncResult result = service.sync();

        assertThat(result.fetched()).isEqualTo(6);
        assertThat(result.updated()).isEqualTo(6);
        assertThat(result.unlisted()).isEqualTo(4);
        verify(client, never()).fetchByState("protect", 3);

        ArgumentCaptor<PetSyncRun> captor = ArgumentCaptor.forClass(PetSyncRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getStartedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getUnlisted()).isEqualTo(4);
    }

    @Test
    @DisplayName("처음 동기화: 받아 둔 동물이 없으면 수정분 조회는 건너뛴다")
    void sync_firstRun() {
        PetSyncService service = service("key", NOW);
        given(petRepository.findLatestSourceUpdatedAt()).willReturn(null);
        given(client.fetchByState(any(), eq(1))).willReturn(page(1, 1));
        given(writer.upsert(anyList(), eq(NOW))).willReturn(new PetSyncWriter.UpsertCount(1, 0, 0));

        PetSyncResult result = service.sync();

        assertThat(result.created()).isEqualTo(2);
        verify(client, never()).fetchUpdated(any(), any(), anyInt());
    }

    @Test
    @DisplayName("전체 목록을 받다가 실패하면 목록 내리기와 기록을 하지 않고, 자동 실행은 예외를 밖으로 던지지 않는다")
    void sync_failure() {
        PetSyncService service = service("key", NOW);
        given(client.fetchByState("notice", 1)).willThrow(new PetException(ErrorCode.EXTERNAL_API_ERROR, "실패"));

        assertThatThrownBy(service::sync).isInstanceOf(PetException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
        service.weeklySync(); // 예외 없이 로그만

        verify(writer, never()).unlistNotSyncedSince(any());
        verify(runRepository, never()).save(any());
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

    private PetSyncService service(String serviceKey, Instant now) {
        PublicAnimalProperties properties = new PublicAnimalProperties(
                "https://apis.example.test/svc", serviceKey, 1000, Duration.ofSeconds(10), Duration.ofSeconds(60));
        return new PetSyncService(client, writer, petRepository, runRepository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private PetSyncRun run(Instant startedAt) {
        return PetSyncRun.of(startedAt, startedAt.plusSeconds(5), new PetSyncResult(0, 0, 0, 0, 0, 5000));
    }

    private PublicAnimalPage page(int size, int totalCount) {
        List<PublicAnimalItem> items = IntStream.range(0, size)
                .mapToObj(i -> new PublicAnimalItem(jsonMapper.readTree("{\"desertionNo\":\"" + i + "\",\"processState\":\"보호중\"}")))
                .toList();
        return new PublicAnimalPage(items, totalCount);
    }
}
