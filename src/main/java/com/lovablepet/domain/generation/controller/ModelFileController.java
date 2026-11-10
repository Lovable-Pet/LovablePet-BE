package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.domain.generation.service.PetModelFiles.ModelFile;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * AR 모델 파일(GLB·USDZ). 주소는 동물 상세의 model.glbUrl·model.usdzUrl 로 내려간다.
 * GET /models/** 는 SecurityConfig 에서 로그인 없이 허용한다.
 */
@Tag(name = "Pet", description = "유기견 목록 / 상세 (로그인 없이 조회)")
@SecurityRequirements
@RestController
@RequiredArgsConstructor
public class ModelFileController {

    // 다시 만들면 jobId 가 바뀌어 주소도 바뀌므로 같은 주소의 파일은 바뀌지 않는다
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final PetModelFiles files;

    @Operation(summary = "AR 모델 파일(GLB·USDZ)",
            description = "동물 상세의 `model.glbUrl`(`model/gltf-binary`, 약 10MB) 또는 `model.usdzUrl`(`model/vnd.usdz+zip`, 약 5~7MB) 주소. "
                    + "둘 다 미터 단위 실제 크기이고 텍스처가 들어 있어 AR 에서 크기를 다시 조정하지 않는다. "
                    + "GLB 의 애니메이션 이름은 idle, tail_wag 로 고정이고, USDZ 는 아이폰 AR Quick Look 용으로 idle 하나만 반복 재생한다."
                    + "\n\n실패: `COMMON_404` 없는 파일")
    @GetMapping("/models/{desertionNo}/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String desertionNo, @PathVariable String fileName) {
        ModelFile file = files.find(desertionNo, fileName)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(file.format().mediaType())
                .cacheControl(IMMUTABLE)
                .body(new FileSystemResource(file.path()));
    }
}
