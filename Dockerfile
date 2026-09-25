# syntax=docker/dockerfile:1

# ---------- 1) Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사해 의존성을 받아둔다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/storage && chown -R app:app /data

COPY --from=build /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_ROOT=/data/storage

# JVM 옵션은 실행 시 JAVA_TOOL_OPTIONS 로 전달 (예: -e JAVA_TOOL_OPTIONS="-Xmx512m")
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
