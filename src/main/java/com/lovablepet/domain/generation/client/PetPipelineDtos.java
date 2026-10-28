package com.lovablepet.domain.generation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 펫 파이프라인 API 의 요청·응답 본문 (LovablePet-AI-AR 저장소 docs/API.md).
 */
public final class PetPipelineDtos {

    private PetPipelineDtos() {
    }

    /** 보호소 기록. "record" 폼 필드에 JSON 으로 보낸다. */
    public record PetRecord(
            @JsonProperty("pet_id") String petId,          // 관리번호. 파일 이름으로도 쓰인다
            @JsonProperty("species") String species,       // "개"만 가능
            @JsonProperty("weight_kg") double weightKg,    // 실제 체중: 모델의 실제 크기를 정한다
            @JsonProperty("coat_color") String coatColor,  // 보호소 털색 표기, 예: "황색", "흰색"
            @JsonProperty("name") String name,
            @JsonProperty("sex") String sex,
            @JsonProperty("neutered") boolean neutered,
            @JsonProperty("age_text") String ageText) {    // 예: "2022(년생)", "2026(60일미만)(년생)"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fit(String level, String label, int score, List<String> reasons, List<String> notes,
                      String template,                                // 만들 모델: jindo, jindo_puppy, maltese, ...
                      @JsonProperty("picked_by") String pickedBy,     // "vision"(사진과 회색 모델 비교) 또는 "looks"
                      String match,                                   // close | rough | poor ("vision"일 때)
                      List<String> differences,                       // 모델과 다른 부위: ears, muzzle, ...
                      String looks) {                                 // DINOv2 생김새. "none"이면 맞는 템플릿 없음
        public boolean isPoor() {
            return "poor".equals(level);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Size(@JsonProperty("weight_kg") double weightKg,
                       @JsonProperty("withers_m") double withersM,
                       double scale,
                       String rule) {                                 // "weight" 또는 "shape"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(@JsonProperty("glb_url") String glbUrl,
                        @JsonProperty("texture_url") String textureUrl,
                        Map<String, String> renders) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Behavior(@JsonProperty("playback_rate") double playbackRate,
                           @JsonProperty("tap_animation") String tapAnimation,
                           @JsonProperty("tap_is_retreat") boolean tapIsRetreat) {
    }

    /** GET /v1/jobs/{id}, POST /v1/jobs 의 202 응답, 콜백 본문이 모두 이 형식이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Job(@JsonProperty("job_id") String jobId,
                      @JsonProperty("pet_id") String petId,
                      String status,               // queued | running | done | failed | photo_rejected | unsuitable
                      Fit fit,
                      PhotoQuality photo,          // done: 사진 품질
                      Size size,
                      Model model,
                      List<String> animations,
                      Behavior behavior,
                      List<String> reasons,        // photo_rejected: 사유 (한국어)
                      String error,
                      @JsonProperty("cost_usd") Double costUsd) {
        public boolean isFinished() {
            return switch (status) {
                case "done", "failed", "photo_rejected", "unsuitable" -> true;
                default -> false;
            };
        }
    }

    /**
     * 사진 자체의 품질: 전체와 항목별(size, sharpness, exposure)로 good | fair | poor.
     * fair/poor 항목은 이미 Fit.reasons 에 한국어 사유로 들어 있고, 여기에는 그 근거 수치가 담긴다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoQuality(String level,
                               Map<String, String> levels,                  // size / sharpness / exposure -> level
                               @JsonProperty("short_side_px") int shortSidePx,
                               double sharpness,
                               @JsonProperty("clipped_share") double clippedShare) {
        public boolean isPoor() {
            return "poor".equals(level);
        }
    }

    /** POST /v1/fit 응답. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FitResult(@JsonProperty("pet_id") String petId, Fit fit, Size size, Map<String, String> shape,
                            PhotoQuality photo) {
    }
}
