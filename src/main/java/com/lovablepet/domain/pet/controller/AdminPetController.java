package com.lovablepet.domain.pet.controller;

import com.lovablepet.domain.pet.dto.PetSyncResult;
import com.lovablepet.domain.pet.service.PetSyncService;
import com.lovablepet.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 동물 API. /api/admin/** 는 SecurityConfig 에서 ADMIN 만 허용한다.
 */
@Tag(name = "Admin - Pet", description = "관리자 전용: 공공데이터 동기화")
@RestController
@RequestMapping("/api/admin/pets")
@RequiredArgsConstructor
public class AdminPetController {

    private final PetSyncService petSyncService;

    @Operation(summary = "공공데이터 동기화 바로 실행",
            description = "매주 월요일 새벽 4시(한국 시각)에 자동으로 도는 동기화를 지금 실행하고, 끝나면 결과를 돌려준다(10초 안팎). "
                    + "공고중·보호중 전체와 지난번 이후 수정분을 받아 덮어쓰고, 공공데이터 목록에서 빠진 동물은 목록에서 내린다(삭제하지 않음).\n\n"
                    + "실패: `COMMON_403` 관리자가 아님, `PET_409_SYNC` 이미 동기화 중, "
                    + "`PET_503_SYNC_KEY` 서버에 공공데이터 인증키가 없음, `EXTERNAL_502` 공공데이터 API 오류")
    @PostMapping("/sync")
    public ApiResponse<PetSyncResult> sync() {
        return ApiResponse.ok(petSyncService.sync());
    }
}
