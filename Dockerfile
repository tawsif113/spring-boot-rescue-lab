# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle gradle.properties ./
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

