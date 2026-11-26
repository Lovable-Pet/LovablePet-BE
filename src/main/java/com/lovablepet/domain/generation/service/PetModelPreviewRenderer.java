package com.lovablepet.domain.generation.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GLB 한 개를 미리보기 그림(1200x930 투명 배경 WebP)으로 찍는다.
 * <p>
 * 화면 없는 크롬(Playwright)에 프론트 상세 화면과 같은 model-viewer · 같은 카메라를 띄워 찍으므로(resources/preview/index.html),
 * 카드를 뒤집자마자 이 그림을 보여 주다가 3D 로 바꿔도 모습이 그대로다.
 * 크롬은 찍을 때마다 띄우고 닫는다: 하루 수십 장이라 상주시켜 메모리(수백 MB)를 잡아 둘 까닭이 없다. 한 번에 한 장만 찍는다.
 * <p>
 * 크롬과 Playwright 드라이버는 도커 이미지에 미리 깔려 있다(Dockerfile 의 PLAYWRIGHT_DRIVER_DIR · PLAYWRIGHT_BROWSERS_PATH).
 * 로컬에서는 build.gradle 의 driver-bundle 이 드라이버를 풀고, 크롬은 ./gradlew playwrightInstall 로 한 번 깐다.
 */
@Slf4j
@Component
public class PetModelPreviewRenderer {

    static final int WIDTH = 1200;
    static final int HEIGHT = 930;
    // 투명 배경 1200x930 이 100KB 안팎이 되는 값
    private static final double QUALITY = 0.9;

    // 실제로 접속하지 않는 가짜 출처. 이 출처의 요청은 serve() 가 직접 답한다
    private static final String ORIGIN = "https://preview.lovablepet.internal";
    private static final List<String> CHROME_ARGS = List.of(
            "--use-angle=swiftshader",       // 서버에는 GPU 가 없어 WebGL 을 CPU 로 그린다
            "--enable-unsafe-swiftshader",   // 요즘 크롬은 이 옵션 없이는 CPU WebGL 을 막는다
            "--ignore-gpu-blocklist",
            "--disable-dev-shm-usage");      // 도커의 /dev/shm(64MB)은 작아 크롬이 죽을 수 있다

    private final byte[] page;
    private final byte[] modelViewer;

    public PetModelPreviewRenderer() {
        this.page = resource("preview/index.html");
        this.modelViewer = resource("preview/model-viewer.min.js");
    }

    /** 실패하면(크롬이 없음, GLB 를 못 읽음, 시간 초과) 예외 */
    public synchronized byte[] render(Path glb) {
        long started = System.nanoTime();
        // 크롬은 미리 깔아 둔다. 없다고 실행 중에 내려받지 않는다
        Playwright.CreateOptions options = new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (Playwright playwright = Playwright.create(options);
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setArgs(CHROME_ARGS))) {
            Page tab = browser.newPage(new Browser.NewPageOptions().setViewportSize(WIDTH, HEIGHT).setDeviceScaleFactor(1));
            tab.setDefaultTimeout(60_000);
            tab.route(ORIGIN + "/**", route -> serve(route, glb));
            tab.navigate(ORIGIN + "/index.html");
            String base64 = (String) tab.evaluate("quality => window.renderPreview('/model.glb', quality)", QUALITY);
            byte[] webp = Base64.getDecoder().decode(base64);
            log.info("미리보기 그림을 찍었습니다: {} ({}KB, {}ms)", glb.getFileName(), webp.length / 1024,
                    (System.nanoTime() - started) / 1_000_000);
            return webp;
        }
    }

    private void serve(Route route, Path glb) {
        String path = route.request().url().substring(ORIGIN.length());
        Route.FulfillOptions response = switch (path) {
            case "/index.html" -> new Route.FulfillOptions().setContentType("text/html; charset=utf-8").setBodyBytes(page);
            case "/model-viewer.min.js" -> new Route.FulfillOptions().setContentType("text/javascript").setBodyBytes(modelViewer);
            case "/model.glb" -> new Route.FulfillOptions().setContentType("model/gltf-binary").setPath(glb);
            default -> new Route.FulfillOptions().setStatus(404);
        };
        route.fulfill(response);
    }

    private static byte[] resource(String name) {
        try (var in = new ClassPathResource(name).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("미리보기 페이지 파일이 없습니다: " + name, e);
        }
    }
}
