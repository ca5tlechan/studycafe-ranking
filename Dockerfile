# syntax=docker/dockerfile:1
# 단일 오리진 배포 이미지: 프론트(PWA)를 빌드해 Spring 정적 리소스로 넣고, 실행가능 jar 하나로 서빙한다.
# 빌드 컨텍스트 = 레포 루트.

# 1) 프론트엔드(PWA) 빌드
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 — 프론트 dist 를 static 으로 넣고 실행가능 bootJar 생성
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY backend/ ./
COPY --from=frontend /fe/dist/ src/main/resources/static/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# 3) 런타임(슬림 JRE). Render 는 PORT 를 주입하며 앱은 server.port=${PORT}로 그 포트를 리슨한다.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/build/libs/*.jar app.jar
# 루트가 아닌 비특권 사용자로 실행(Trivy DS-0002 / Checkov CKV_DOCKER_3). app.jar 은 world-readable,
# JVM 임시파일은 world-writable 인 /tmp 를 쓰므로 uid 만 지정하면 된다.
USER 10001:10001
# 512MB 컨테이너에 맞춰 힙 상한을 컨테이너 메모리 비율로 제한(기본 25%는 부하 시 빠듯).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]
