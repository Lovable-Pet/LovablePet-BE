package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * GLB 파일 저장소: {model-dir}/{유기번호}/{jobId}.glb (노션 계획의 S3 키 models/{petId}/{jobId}.glb 와 같은 모양).
 * 다시 만들면 jobId 가 바뀌어 파일 이름도 바뀌므로 오래 캐시해도 옛 모델이 남지 않는다. S3 로 옮기면 이 클래스만 바꾼다.
 */
@Component
public class PetModelFiles {

    // 점(.)을 받지 않아 "../" 같은 경로를 만들 수 없다. 유기번호는 숫자, jobId 는 "{유기번호}-{16진수}"
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private static final String GLB_SUFFIX = ".glb";

    private final Path modelDir;
    private final String baseUrl;

    public PetModelFiles(PetPipelineProperties properties) {
        this.modelDir = Path.of(properties.modelDir()).toAbsolutePath().normalize();
        String url = properties.modelBaseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 저장하고 model-dir 기준 상대 경로("{유기번호}/{jobId}.glb")를 돌려준다. 같은 작업이면 덮어쓴다. */
    public String saveGlb(String desertionNo, String jobId, byte[] glb) {
        if (desertionNo == null || jobId == null
                || !SAFE_NAME.matcher(desertionNo).matches() || !SAFE_NAME.matcher(jobId).matches()) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_PET_ID);
        }
        Path dir = modelDir.resolve(desertionNo);
        Path target = dir.resolve(jobId + GLB_SUFFIX);
        try {
            Files.createDirectories(dir);
            // 다 쓴 뒤에 이름을 바꿔, 받는 도중에 반쯤 쓴 파일이 서빙되지 않게 한다
            Path temp = Files.createTempFile(dir, jobId, ".tmp");
            Files.write(temp, glb);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("GLB 저장 실패: " + target, e);
        }
        return desertionNo + "/" + jobId + GLB_SUFFIX;
    }

    /** 앱에 내려주는 GLB 주소 (예: /models/448567202600123/448567202600123-a5ca257e.glb). 저장 전이면 null */
    public String publicUrl(String glbPath) {
        return glbPath == null ? null : baseUrl + "/" + glbPath;
    }

    /** GET /models/{유기번호}/{파일} 의 실제 파일. 이름이 안전하지 않으면 empty (→ 404) */
    public Optional<Path> findGlb(String desertionNo, String fileName) {
        if (desertionNo == null || fileName == null || !fileName.endsWith(GLB_SUFFIX)
                || !SAFE_NAME.matcher(desertionNo).matches()
                || !SAFE_NAME.matcher(fileName.substring(0, fileName.length() - GLB_SUFFIX.length())).matches()) {
            return Optional.empty();
        }
        Path file = modelDir.resolve(desertionNo).resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }
}
