# 1) 빌드 스테이지: Gradle + JDK
FROM gradle:7.6-jdk11 AS builder
WORKDIR /workspace

# Gradle 래퍼, 설정 파일 복사 (Groovy DSL)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./


# 소스 전체 복사 & 빌드
COPY src src
RUN ./gradlew clean bootJar --no-daemon

# 2) 런타임 스테이지: multi-arch 지원 JRE
FROM eclipse-temurin:11-jre-focal
WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]