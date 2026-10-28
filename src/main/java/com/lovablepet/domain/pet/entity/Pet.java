package com.lovablepet.domain.pet.entity;

import com.lovablepet.domain.pet.client.PublicAnimalItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터에서 동기화한 유기견. 컬럼 설명은 V6__create_pet_table.sql.
 * 값은 동기화로만 바뀐다 (원본이 바뀌면 다음 동기화 때 덮어쓴다).
 */
@Entity
@Table(name = "pet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private String desertionNo;

    private String noticeNo;
    private String kindName;
    private String color;
    private String ageText;
    private Integer birthYear;
    private String weightText;

    @Column(precision = 5, scale = 2)
    private BigDecimal weightKg;

    private String sex;
    private String neutered;

    @Column(nullable = false)
    private String processState;

    private LocalDate noticeStart;
    private LocalDate noticeEnd;

    @Column(nullable = false)
    private boolean listed;

    private String orgName;
    private String happenPlace;
    private String careName;
    private String careTel;
    private String careAddr;
    private String careRegNo;
    private String specialMark;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> photoUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String raw;

    // 한국 시각(원본)과 서버 시각을 비교하므로 절대 시각(Instant, TIMESTAMPTZ)으로 둔다
    private Instant sourceUpdatedAt;

    @Column(nullable = false)
    private Instant syncedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Pet from(PublicAnimalItem item, Instant syncedAt) {
        Pet pet = new Pet();
        pet.desertionNo = item.desertionNo();
        pet.apply(item, syncedAt);
        return pet;
    }

    /** 다음 동기화에서 받은 원본으로 덮어쓴다. */
    public void apply(PublicAnimalItem item, Instant syncedAt) {
        this.noticeNo = item.noticeNo();
        this.kindName = item.kindName();
        this.color = item.color();
        this.ageText = item.ageText();
        this.birthYear = item.birthYear();
        this.weightText = item.weightText();
        this.weightKg = item.weightKg();
        this.sex = item.sex();
        this.neutered = item.neutered();
        this.processState = item.processState() != null ? item.processState() : "";
        this.noticeStart = item.noticeStart();
        this.noticeEnd = item.noticeEnd();
        // 공공데이터 목록(공고중·보호중)에 있는 동물은 상태가 '보호중'으로 온다
        this.listed = PublicAnimalItem.STATE_PROTECTED.equals(this.processState);
        this.orgName = item.orgName();
        this.happenPlace = item.happenPlace();
        this.careName = item.careName();
        this.careTel = item.careTel();
        this.careAddr = item.careAddr();
        this.careRegNo = item.careRegNo();
        this.specialMark = item.specialMark();
        this.photoUrls = item.photoUrls();
        this.raw = item.rawJson();
        this.sourceUpdatedAt = item.updatedAt();
        this.syncedAt = syncedAt;
    }

    /** 공고에 이름이 없어 화면에 보여 줄 이름: "믹스견 · 경기도 화성시 · 5kg". 값이 없는 부분은 뺀다. */
    public String displayName() {
        List<String> parts = new ArrayList<>(3);
        if (kindName != null) {
            parts.add(kindName);
        }
        if (orgName != null) {
            parts.add(orgName);
        }
        if (weightKg != null) {
            parts.add(weightKg.stripTrailingZeros().toPlainString() + "kg");
        }
        return String.join(" · ", parts);
    }

    /** 입양 가능(보호중): 공공데이터 목록에 있고 공고 기간이 끝났다. 공고 기간 중(공고중)에는 원래 보호자가 찾아갈 수 있다. */
    public boolean isAdoptable(LocalDate today) {
        return listed && noticeEnd != null && noticeEnd.isBefore(today);
    }
}
