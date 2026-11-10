package com.lovablepet.domain.favorite.entity;

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
 * 회원이 찜한 동물 하나. 컬럼 설명은 V9__create_favorite_table.sql.
 * 찜하기는 FavoriteRepository#insertIfAbsent 로 넣으므로 이 엔티티는 조회에 쓴다.
 */
@Entity
@Table(name = "favorite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Long petId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
