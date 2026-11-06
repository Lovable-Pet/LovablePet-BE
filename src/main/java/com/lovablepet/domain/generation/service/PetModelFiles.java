package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.exception.GenerationException;
import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * AR 모델 파일 저장소: {model-dir}/{유기번호}/{jobId}.glb 와 같은 이름의 .usdz (노션 계획의 S3 키 models/{petId}/{jobId}.glb 와 같은 모양).
 * 다시 만들면 jobId 가 바뀌어 파일 이름도 바뀌므로 오래 캐시해도 옛 모델이 남지 않는다. S3 로 옮기면 이 클래스만 바꾼다.
 */
@Component
public class PetModelFiles {

    // 점(.)을 받지 않아 "../" 같은 경로를 만들 수 없다. 유기번호는 숫자, jobId 는 "{유기번호}-{16진수}"
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    /** 같은 모델의 두 형식. 안드로이드·데스크톱 AR 은 GLB, 아이폰 AR(Quick Look)은 USDZ 만 읽는다 */
    public enum Format {
        // Spring 이 아는 확장자 목록에 둘 다 없어 Content-Type 을 직접 지정한다
        GLB(".glb", MediaType.valueOf("model/gltf-binary")),
        USDZ(".usdz", MediaType.valueOf("model/vnd.usdz+zip"));

        private final String suffix;
        private final MediaType mediaType;

        Format(String suffix, MediaType mediaType) {
            this.suffix = suffix;
            this.mediaType = mediaType;
        }

        public String suffix() {
            return suffix;
        }

        public MediaType mediaType() {
            return mediaType;
        }

        static Optional<Format> ofFileName(String fileName) {
            return Arrays.stream(values()).filter(format -> fileName.endsWith(format.suffix)).findFirst();
        }
    }

    /** GET /models/{유기번호}/{파일} 로 찾은 파일 */
    public record ModelFile(Path path, Format format) {
    }

    private final Path modelDir;
    private final String baseUrl;

    public PetModelFiles(PetPipelineProperties properties) {
        this.modelDir = Path.of(properties.modelDir()).toAbsolutePath().normalize();
        String url = properties.modelBaseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 저장하고 model-dir 기준 상대 경로(예: "{유기번호}/{jobId}.glb")를 돌려준다. 같은 작업이면 덮어쓴다. */
    public String save(String desertionNo, String jobId, Format format, byte[] content) {
        if (desertionNo == null || jobId == null
                || !SAFE_NAME.matcher(desertionNo).matches() || !SAFE_NAME.matcher(jobId).matches()) {
            throw new GenerationException(ErrorCode.GENERATION_INVALID_PET_ID);
        }
        Path dir = modelDir.resolve(desertionNo);
        Path target = dir.resolve(jobId + format.suffix);
        try {
            Files.createDirectories(dir);
            // 다 쓴 뒤에 이름을 바꿔, 받는 도중에 반쯤 쓴 파일이 서빙되지 않게 한다
            Path temp = Files.createTempFile(dir, jobId, ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(format + " 저장 실패: " + target, e);
        }
        return desertionNo + "/" + jobId + format.suffix;
    }

    /** 앱에 내려주는 주소 (예: /models/448567202600123/448567202600123-a5ca257e.glb). 저장 전이거나 없는 파일이면 null */
    public String publicUrl(String path) {
        return path == null ? null : baseUrl + "/" + path;
    }

    /** GET /models/{유기번호}/{파일} 의 실제 파일. .glb·.usdz 가 아니거나 이름이 안전하지 않으면 empty (→ 404) */
    public Optional<ModelFile> find(String desertionNo, String fileName) {
        if (desertionNo == null || fileName == null || !SAFE_NAME.matcher(desertionNo).matches()) {
            return Optional.empty();
        }
        return Format.ofFileName(fileName)
                .filter(format -> SAFE_NAME.matcher(fileName.substring(0, fileName.length() - format.suffix.length())).matches())
                .map(format -> new ModelFile(modelDir.resolve(desertionNo).resolve(fileName), format))
                .filter(file -> Files.isRegularFile(file.path()));
    }
}
