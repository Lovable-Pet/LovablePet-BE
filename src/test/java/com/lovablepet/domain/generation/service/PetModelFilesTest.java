package com.lovablepet.domain.generation.service;

import com.lovablepet.domain.generation.service.PetModelFiles.Format;
import com.lovablepet.domain.generation.service.PetModelFiles.ModelFile;
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

    private static final String DESERTION_NO = "448567202600123";
    private static final String JOB_ID = "448567202600123-a5ca257e";

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("{model-dir}/{유기번호}/{jobId}.glb 에 저장하고 상대 경로를 돌려준다. 같은 작업이면 덮어쓴다")
    void saveGlb() throws IOException {
        PetModelFiles files = files();

        String path = files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{1, 2, 3});
        files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{4, 5});

        assertThat(path).isEqualTo("448567202600123/448567202600123-a5ca257e.glb");
        Path saved = tempDir.resolve("models").resolve(path);
        assertThat(Files.readAllBytes(saved)).containsExactly(4, 5);
        assertThat(Files.list(saved.getParent())).hasSize(1); // 임시 파일이 남지 않는다
    }

    @Test
    @DisplayName("USDZ 는 GLB 옆에 같은 이름의 .usdz 로 저장한다")
    void saveUsdz() throws IOException {
        PetModelFiles files = files();

        files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{1});
        String path = files.save(DESERTION_NO, JOB_ID, Format.USDZ, new byte[]{'P', 'K'});

        assertThat(path).isEqualTo("448567202600123/448567202600123-a5ca257e.usdz");
        Path saved = tempDir.resolve("models").resolve(path);
        assertThat(Files.readAllBytes(saved)).containsExactly('P', 'K');
        assertThat(Files.list(saved.getParent())).hasSize(2);
    }

    @Test
    @DisplayName("경로를 벗어나는 이름(../, /, 점)은 거부한다")
    void rejectsUnsafeNames() {
        PetModelFiles files = files();

        for (String[] names : new String[][]{{"..", "j1"}, {"1", "../j1"}, {"1/2", "j1"}, {"1", "j1.x"}, {"", "j1"}, {null, "j1"}}) {
            for (Format format : Format.values()) {
                assertThatThrownBy(() -> files.save(names[0], names[1], format, new byte[]{1}))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.GENERATION_INVALID_PET_ID);
            }
        }
    }

    @Test
    @DisplayName("앱에 내려주는 주소: model-base-url + 상대 경로. 저장 전이면 null")
    void publicUrl() {
        assertThat(files().publicUrl("448567202600123/j1.glb")).isEqualTo("/models/448567202600123/j1.glb");
        assertThat(files().publicUrl("448567202600123/j1.usdz")).isEqualTo("/models/448567202600123/j1.usdz");
        assertThat(files().publicUrl(null)).isNull();
        PetModelFiles cdn = new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "", "models",
                Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "https://cdn.example.com/models/"));
        assertThat(cdn.publicUrl("1/j1.glb")).isEqualTo("https://cdn.example.com/models/1/j1.glb");
    }

    @Test
    @DisplayName("파일 찾기: 저장한 GLB·USDZ 만 형식과 함께 찾고, 없는 파일·다른 확장자·경로를 벗어나는 이름은 empty")
    void find() {
        PetModelFiles files = files();
        files.save(DESERTION_NO, JOB_ID, Format.GLB, new byte[]{1});
        files.save(DESERTION_NO, JOB_ID, Format.USDZ, new byte[]{2});

        assertThat(files.find(DESERTION_NO, JOB_ID + ".glb")).get().extracting(ModelFile::format).isEqualTo(Format.GLB);
        assertThat(files.find(DESERTION_NO, JOB_ID + ".usdz")).get().extracting(ModelFile::format).isEqualTo(Format.USDZ);
        assertThat(files.find(DESERTION_NO, "nope.glb")).isEmpty();
        assertThat(files.find(DESERTION_NO, "nope.usdz")).isEmpty();
        assertThat(files.find(DESERTION_NO, JOB_ID + ".tmp")).isEmpty();
        assertThat(files.find(DESERTION_NO, JOB_ID)).isEmpty();
        assertThat(files.find("..", JOB_ID + ".glb")).isEmpty();
        assertThat(files.find(DESERTION_NO, "..glb")).isEmpty();
        assertThat(files.find(DESERTION_NO, "..usdz")).isEmpty();
        assertThat(files.find(DESERTION_NO, ".usdz")).isEmpty();
    }

    private PetModelFiles files() {
        return new PetModelFiles(new PetPipelineProperties("http://pipeline:8000", "key", "",
                tempDir.resolve("models").toString(), Duration.ofSeconds(10), Duration.ofSeconds(90), null, 30, "/models"));
    }
}
