package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.dto.FitCheckRequest;
import com.lovablepet.domain.generation.dto.FitCheckResponse;
import com.lovablepet.domain.generation.dto.GenerateModelRequest;
import com.lovablepet.domain.generation.dto.PetModelListResponse;
import com.lovablepet.domain.generation.dto.PetModelResponse;
import com.lovablepet.domain.generation.entity.PetModelStatus;
import com.lovablepet.domain.generation.service.PetModelGenerationService;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 AR(3D 모델) 만들기. /api/admin/** 는 SecurityConfig 에서 ADMIN 만 허용한다.
 */
@Tag(name = "Admin - AR", description = "관리자 전용: 사진을 골라 AR(3D 모델) 만들기")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPetModelController {

    private final PetModelGenerationService generationService;

    @Operation(summary = "적합도 확인",
            description = "고른 사진으로 이 개를 만들 수 있는지 미리 본다. 5~20초(배포 직후 첫 호출은 30초 넘게), 약 $0.02.\n\n"
                    + "- `canGenerate=false`: 사진이 거부됨(사람 손이 가림, 여러 마리 등) → 다른 사진을 고르게 한다. force 로도 못 만든다\n"
                    + "- `needsForce=true`: 템플릿 부적합 → 만들어도 그 개처럼 안 보일 수 있어 force 로만 만든다\n"
                    + "- 사진이 거부되면 `withersM`·`photoLevel`·`template` 은 null\n\n"
                    + "실패: `PET_404` 없는 동물, `GENERATION_400_WEIGHT` 체중을 알 수 없음(weightKg 입력), `GENERATION_400_PHOTO` 사진 번호 오류, "
                    + "`GENERATION_502_PHOTO` 사진을 받을 수 없음, `EXTERNAL_502`·`EXTERNAL_504` 파이프라인 오류")
    @PostMapping("/pets/{id}/model/fit")
    public ApiResponse<FitCheckResponse> checkFit(@PathVariable Long id, @Valid @RequestBody FitCheckRequest request) {
        return ApiResponse.ok(generationService.checkFit(id, request));
    }

    @Operation(summary = "AR 만들기",
            description = "생성을 등록하고 바로 202 로 답한다. 1.5~4분 뒤 결과가 콜백으로 와서 상태가 바뀐다 "
                    + "(만드는 중 화면에서 `GET /api/admin/pets/{id}/model` 을 몇 초마다 조회). 한 마리 약 $0.075.\n\n"
                    + "실패: `GENERATION_409_EXISTS` 이미 완성 모델이 있음(한 번 만들면 계속 씀), `GENERATION_409_RUNNING` 만드는 중, "
                    + "`GENERATION_400_PHOTO_REJECTED` 적합도 확인에서 거부된 사진, `GENERATION_400_WEIGHT`, `GENERATION_400_PHOTO`, "
                    + "`GENERATION_429_LIMIT` 오늘 생성 횟수를 다 씀, `GENERATION_502_PHOTO`, `EXTERNAL_502`·`EXTERNAL_504` 파이프라인 오류")
    @PostMapping("/pets/{id}/model")
    public ResponseEntity<ApiResponse<PetModelResponse>> generate(
            @PathVariable Long id,
            @Valid @RequestBody GenerateModelRequest request,
            @AuthenticationPrincipal Long adminId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(generationService.requestGeneration(id, request, adminId)));
    }

    @Operation(summary = "이 개의 최근 AR 생성 상태", description = "생성 시도가 없으면 data 가 null")
    @GetMapping("/pets/{id}/model")
    public ApiResponse<PetModelResponse> latest(@PathVariable Long id) {
        return ApiResponse.ok(generationService.latest(id).orElse(null));
    }

    @Operation(summary = "AR 생성 시도 목록", description = "최근 100건과 오늘 사용량(todayCount / dailyLimit)")
    @GetMapping("/pet-models")
    public ApiResponse<PetModelListResponse> list(
            @Parameter(description = "상태로 거르기 (비우면 전체)")
            @RequestParam(required = false) PetModelStatus status) {
        return ApiResponse.ok(generationService.list(status));
    }
}
