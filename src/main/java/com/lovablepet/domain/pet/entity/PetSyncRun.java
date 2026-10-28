package com.lovablepet.domain.pet.entity;

import com.lovablepet.domain.pet.dto.PetSyncResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 성공한 공공데이터 동기화 한 번. 실패한 동기화는 남기지 않는다.
 */
@Entity
@Table(name = "pet_sync_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant finishedAt;

    private int fetched;
    private int created;
    private int updated;
    private int unlisted;

    public static PetSyncRun of(Instant startedAt, Instant finishedAt, PetSyncResult result) {
        PetSyncRun run = new PetSyncRun();
        run.startedAt = startedAt;
        run.finishedAt = finishedAt;
        run.fetched = result.fetched();
        run.created = result.created();
        run.updated = result.updated();
        run.unlisted = result.unlisted();
        return run;
    }
}
