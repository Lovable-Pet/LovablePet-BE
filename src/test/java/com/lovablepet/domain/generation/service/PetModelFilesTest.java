package com.lovablepet.domain.generation.service;

import com.lovablepet.global.config.PetPipelineProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PetModelFilesTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("{model-dir}/{유기번호}/{jobId}.glb 에 저장하고 상대 경로를 돌려준다. 같은 작업이면 덮어쓴다")
    void saveGlb() throws IOException {
        PetModelFiles files = files();

        String path = files.saveGlb("448567202600123", "448567202600123-a5ca257e", new byte[]{1, 2, 3});
        files.saveGlb("448567202600123", "448567202600123-a5ca257e", new byte[]{4, 5});

        assertThat(path).isEqualTo("448567202600123/448567202600123-a5ca257e.glb");
        Path saved = tempDir.resolve("models").resolve(path);
        assertThat(Files.readAllBytes(saved)).containsExactly(4, 5);
        assertThat(Files.list(saved.getParent())).hasSize(1); // 임시 파일이 남지 않는다
    }

    @Test
    @DisplayName("경로를 벗어나는 이름(../, /, 점)은 거부한다")
    void rejectsUnsafeNames() {
        PetModelFiles files = files();

        for (String[] names : new String[][]{{"..", "j1"}, {"1", "../j1"}, {"1/2", "j1"}, {"1", "j1.x"}, {"", "j1"}, {null, "j1"}}) {
            assertThatThrownBy(() -> files.saveGlb(names[0], names[1], new byte[]{1}))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_PET_ID);
        }
    }

    @Test
    @DisplayName("앱에 내려주는 주소: model-base-url + 상대 경로. 저장 전이면 null")
    void publicUrl() {
        assertThat(files().publicUrl("448567202600123/j1.glb")).isEqualTo("/models/448567202600123/j1.glb");
        assertThat(files().publicUrl(null)).isNull();
        PetModelFiles cdn = new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "", "models",
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "https://cdn.example.com/models/"));
        assertThat(cdn.publicUrl("1/j1.glb")).isEqualTo("https://cdn.example.com/models/1/j1.glb");
    }

    @Test
    @DisplayName("파일 찾기: 저장한 GLB 만 찾고, 없는 파일·.glb 가 아닌 이름·경로를 벗어나는 이름은 empty")
    void findGlb() {
        PetModelFiles files = files();
        files.saveGlb("448567202600123", "448567202600123-a5ca257e", new byte[]{1});

        assertThat(files.findGlb("448567202600123", "448567202600123-a5ca257e.glb")).isPresent();
        assertThat(files.findGlb("448567202600123", "nope.glb")).isEmpty();
        assertThat(files.findGlb("448567202600123", "448567202600123-a5ca257e.tmp")).isEmpty();
        assertThat(files.findGlb("..", "448567202600123-a5ca257e.glb")).isEmpty();
        assertThat(files.findGlb("448567202600123", "..glb")).isEmpty();
    }

    private PetModelFiles files() {
        return new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "",
                tempDir.resolve("models").toString(), Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "/models"));
    }
}
