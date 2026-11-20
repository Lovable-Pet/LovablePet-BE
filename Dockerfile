# syntax=docker/dockerfile:1

# ---------- 1) Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사해 의존성을 받아둔다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src ./src
# playwrightDriver: AR 미리보기 그림을 찍는 Playwright 드라이버(리눅스 x64 의 node + package/)를 build/playwright-driver 로 꺼낸다
RUN ./gradlew bootJar playwrightDriver --no-daemon -x test

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# uid/gid 고정: 서버에서 /data/storage 에 바인드 마운트하는 폴더를 이 번호(10001) 소유로 만들어 둔다
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app \
    && mkdir -p /data/storage && chown -R app:app /data

# AR 미리보기 그림(PetModelPreviewRenderer)을 찍는 화면 없는 크롬과 그 드라이버.
# 드라이버는 jar 에 넣지 않고 여기에 두며(PLAYWRIGHT_DRIVER_DIR), 크롬은 이미지를 만들 때 한 번 깐다(실행 중에는 내려받지 않는다).
# --with-deps 가 크롬에 필요한 리눅스 라이브러리를 apt 로 함께 깐다. 이미지가 약 300MB 커진다
ENV PLAYWRIGHT_DRIVER_DIR=/opt/playwright-driver \
    PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright
COPY --from=build /workspace/build/playwright-driver /opt/playwright-driver
RUN chmod +x /opt/playwright-driver/node \
    && /opt/playwright-driver/node /opt/playwright-driver/package/cli.js install --with-deps --only-shell chromium \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_ROOT=/data/storage

# JVM 옵션은 실행 시 JAVA_TOOL_OPTIONS 로 전달 (예: -e JAVA_TOOL_OPTIONS="-Xmx512m")
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
