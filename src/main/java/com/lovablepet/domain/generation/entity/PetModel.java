package com.lovablepet.domain.generation.entity;

import com.lovablepet.domain.generation.client.PetPipelineDtos.Fit;
import com.lovablepet.domain.generation.client.PetPipelineDtos.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * AR(3D 모델) 생성 시도 하나. 컬럼 설명은 V8__create_pet_model_table.sql.
 * PENDING 으로 만들어지고, 파이프라인 결과(콜백 또는 진행 중 작업 확인)로 한 번만 끝난 상태가 된다.
 */
@Entity
@Table(name = "pet_model")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long petId;

    @Column(unique = true, length = 100)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PetModelStatus status;

    @Column(nullable = false, updatable = false)
    private String photoUrl;

    @Column(nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false, updatable = false)
    private boolean forced;

    @Column(updatable = false)
    private Long requestedBy;

    private String fitLevel;
    private Integer fitScore;
    private String template;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> reasons;

    private String error;
    private String glbPath;

    // 끝 글자가 대문자면 기본 이름 규칙이 withersm 으로 바꾸므로 이름을 적어 준다
    @Column(name = "withers_m", precision = 5, scale = 3)
    private BigDecimal withersM;

    private String tapAnimation;

    @Column(precision = 4, scale = 2)
    private BigDecimal playbackRate;

    @Column(precision = 6, scale = 3)
    private BigDecimal costUsd;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant finishedAt;

    /** 작업 등록 전에 자리를 잡는다. 같은 동물에 PENDING 이 이미 있으면 유니크 인덱스에 걸린다. */
    public static PetModel reserve(Long petId, String photoUrl, BigDecimal weightKg, boolean forced, Long requestedBy,
                                   Instant now) {
        PetModel model = new PetModel();
        model.petId = petId;
        model.status = PetModelStatus.PENDING;
        model.photoUrl = photoUrl;
        model.weightKg = weightKg;
        model.forced = forced;
        model.requestedBy = requestedBy;
        model.reasons = List.of();
        model.createdAt = now;
        return model;
    }

    public void attachJob(String jobId) {
        this.jobId = jobId;
    }

    public boolean isPending() {
        return status == PetModelStatus.PENDING;
    }

    /** done: GLB 를 저장한 뒤 부른다. */
    public void complete(Job job, String glbPath, Instant now) {
        applyFit(job.fit());
        this.glbPath = glbPath;
        if (job.size() != null && job.size().withersM() != null) {
            this.withersM = BigDecimal.valueOf(job.size().withersM());
        }
        if (job.behavior() != null) {
            this.tapAnimation = job.behavior().tapAnimation();
            this.playbackRate = job.behavior().playbackRate() == null ? null : BigDecimal.valueOf(job.behavior().playbackRate());
        }
        finish(PetModelStatus.DONE, job, now);
    }

    /** photo_rejected · unsuitable · failed */
    public void finishWithoutModel(Job job, Instant now) {
        applyFit(job.fit());
        PetModelStatus result = switch (job.status()) {
            case "photo_rejected" -> PetModelStatus.PHOTO_REJECTED;
            case "unsuitable" -> PetModelStatus.UNSUITABLE;
            default -> PetModelStatus.FAILED;
        };
        // 사진 거부 사유는 작업의 reasons, 부적합 사유는 fit.reasons 에 온다
        List<String> source = result == PetModelStatus.UNSUITABLE && job.fit() != null ? job.fit().reasons() : job.reasons();
        this.reasons = source == null ? List.of() : List.copyOf(source);
        this.error = job.error();
        finish(result, job, now);
    }

    /** 파이프라인에서 결과를 받을 수 없을 때 (작업이 없어짐, 시간 초과, 등록 중 중단) */
    public void fail(String error, Instant now) {
        this.status = PetModelStatus.FAILED;
        this.error = error;
        this.finishedAt = now;
    }

    private void applyFit(Fit fit) {
        if (fit != null) {
            this.fitLevel = fit.level();
            this.fitScore = fit.score();
            this.template = fit.template();
        }
    }

    private void finish(PetModelStatus result, Job job, Instant now) {
        this.status = result;
        if (job.costUsd() != null) {
            this.costUsd = BigDecimal.valueOf(job.costUsd());
        }
        this.finishedAt = now;
    }
}
