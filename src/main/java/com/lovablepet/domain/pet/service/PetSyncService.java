package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.client.PublicAnimalClient;
import com.lovablepet.domain.pet.client.PublicAnimalPage;
import com.lovablepet.domain.pet.dto.PetSyncResult;
import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.domain.pet.repository.PetRepository;
import com.lovablepet.global.config.PublicAnimalProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * 공공데이터 구조동물(개)을 pet 테이블로 동기화한다. 하루 한 번 자동으로, 또는 관리자가 바로 실행한다.
 * <ol>
 *   <li>공고중·보호중 전체를 받아 유기번호 기준으로 넣거나 덮어쓴다</li>
 *   <li>지난번 이후 수정된 동물을 받는다 (입양·반환 등으로 종료된 동물의 최종 상태)</li>
 *   <li>1에서 받지 못한 동물은 공공데이터 목록에서 빠진 것이므로 목록에서 내린다 (listed = false). 지우지는 않는다</li>
 * </ol>
 * 호출은 하루 5번 안팎이다 (개발계정 한도 하루 10,000번).
 */
@Slf4j
@Service
public class PetSyncService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAILY_SYNC_TIME = LocalTime.of(4, 0);
    // 응답의 상태 값은 둘 다 "보호중"이고, 공고 종료일이 지났는지로만 갈린다
    private static final List<String> LISTED_STATES = List.of("notice", "protect");
    // 1000마리씩 받는다. 개 공고중·보호중은 각각 2천 마리 안팎이라 넉넉한 상한
    private static final int MAX_PAGES = 20;

    private final PublicAnimalClient client;
    private final PetSyncWriter writer;
    private final PetRepository petRepository;
    private final PublicAnimalProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Instant lastSuccessAt;
    private volatile boolean warnedNotConfigured;

    @Autowired
    public PetSyncService(PublicAnimalClient client, PetSyncWriter writer, PetRepository petRepository,
                          PublicAnimalProperties properties) {
        this(client, writer, petRepository, properties, Clock.systemUTC());
    }

    PetSyncService(PublicAnimalClient client, PetSyncWriter writer, PetRepository petRepository,
                   PublicAnimalProperties properties, Clock clock) {
        this.client = client;
        this.writer = writer;
        this.petRepository = petRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 30분마다 확인해, 가장 최근의 새벽 4시(한국 시각) 이후에 동기화한 적이 없으면 실행한다.
     * EC2 가 꺼져 있어 새벽 작업을 놓쳐도, 켜진 뒤 첫 확인(기동 1분 후) 때 따라잡는다.
     */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT30M")
    public void syncIfDue() {
        if (!properties.isConfigured()) {
            if (!warnedNotConfigured) {
                log.warn("DATA_GO_KR_SERVICE_KEY 가 없어 공공데이터 동물 동기화를 건너뜁니다.");
                warnedNotConfigured = true;
            }
            return;
        }
        if (!isDue()) {
            return;
        }
        try {
            sync();
        } catch (BusinessException e) {
            log.warn("공공데이터 동물 동기화 실패: {}", e.getMessage());
        }
    }

    boolean isDue() {
        Instant last = lastSuccessAt != null ? lastSuccessAt : petRepository.findLatestSyncedAt();
        return last == null || last.isBefore(latestDailySyncTime());
    }

    /** 가장 최근의 새벽 4시(한국 시각) */
    Instant latestDailySyncTime() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(KST));
        ZonedDateTime today = now.with(DAILY_SYNC_TIME);
        return (now.isBefore(today) ? today.minusDays(1) : today).toInstant();
    }

    public PetSyncResult sync() {
        if (!properties.isConfigured()) {
            throw new PetException(ErrorCode.PET_SYNC_NOT_CONFIGURED);
        }
        if (!running.compareAndSet(false, true)) {
            throw new PetException(ErrorCode.PET_SYNC_RUNNING);
        }
        try {
            return doSync();
        } finally {
            running.set(false);
        }
    }

    private PetSyncResult doSync() {
        long started = System.nanoTime();
        // DB 에 저장되는 정밀도로 맞춰야 3단계의 "이번에 받지 못한 동물" 비교가 정확하다
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant latestSourceUpdate = petRepository.findLatestSourceUpdatedAt();
        Tally tally = new Tally();

        for (String state : LISTED_STATES) {
            fetchAll(pageNo -> client.fetchByState(state, pageNo), startedAt, tally);
        }
        if (latestSourceUpdate != null) {
            // 수정일 조건은 한국 날짜다. 하루 겹치게 받는다
            LocalDate from = LocalDate.ofInstant(latestSourceUpdate, KST).minusDays(1);
            LocalDate to = LocalDate.now(clock.withZone(KST));
            fetchAll(pageNo -> client.fetchUpdated(from, to, pageNo), startedAt, tally);
        }
        int unlisted = writer.unlistNotSyncedSince(startedAt);

        lastSuccessAt = startedAt;
        PetSyncResult result = new PetSyncResult(tally.fetched, tally.created, tally.updated, unlisted, tally.skipped,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        log.info("공공데이터 동물 동기화 완료: {}", result);
        return result;
    }

    /** 전체 건수만큼 받을 때까지 페이지를 넘긴다. 중간에 실패하면 예외가 나가 목록 내리기(3단계)를 하지 않는다. */
    private void fetchAll(IntFunction<PublicAnimalPage> fetchPage, Instant syncedAt, Tally tally) {
        int received = 0;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            PublicAnimalPage page = fetchPage.apply(pageNo);
            if (page.items().isEmpty()) {
                return;
            }
            PetSyncWriter.UpsertCount count = writer.upsert(page.items(), syncedAt);
            tally.fetched += page.items().size();
            tally.created += count.created();
            tally.updated += count.updated();
            tally.skipped += count.skipped();
            received += page.items().size();
            if (received >= page.totalCount()) {
                return;
            }
        }
        throw new PetException(ErrorCode.EXTERNAL_API_ERROR,
                "공공데이터 구조동물 조회 실패: " + MAX_PAGES + "페이지를 넘어 중단했습니다");
    }

    private static final class Tally {
        int fetched;
        int created;
        int updated;
        int skipped;
    }
}
