package com.lovablepet.domain.generation.controller;

import com.lovablepet.domain.generation.service.PetModelFiles;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;

/**
 * AR 모델 파일(GLB). 주소는 동물 상세의 model.glbUrl 로 내려간다. GET /models/** 는 SecurityConfig 에서 로그인 없이 허용한다.
 */
@Tag(name = "Pet", description = "유기견 목록 / 상세 (로그인 없이 조회)")
@SecurityRequirements
@RestController
@RequiredArgsConstructor
public class ModelFileController {

    // Spring 이 아는 확장자 목록에 .glb 가 없어 직접 지정한다
    static final MediaType GLTF_BINARY = MediaType.valueOf("model/gltf-binary");
    // 다시 만들면 jobId 가 바뀌어 주소도 바뀌므로 같은 주소의 파일은 바뀌지 않는다
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final PetModelFiles files;

    @Operation(summary = "AR 모델 파일(GLB)",
            description = "동물 상세의 `model.glbUrl` 주소. 미터 단위 실제 크기이고 텍스처가 들어 있다(약 10MB). "
                    + "AR 에서 크기를 다시 조정하지 않는다. 애니메이션 이름은 idle, tail_wag 로 고정.\n\n실패: `COMMON_404` 없는 파일")
    @GetMapping("/models/{desertionNo}/{fileName}")
    public ResponseEntity<Resource> glb(@PathVariable String desertionNo, @PathVariable String fileName) {
        Path file = files.findGlb(desertionNo, fileName)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(GLTF_BINARY)
                .cacheControl(IMMUTABLE)
                .body(new FileSystemResource(file));
    }
}
