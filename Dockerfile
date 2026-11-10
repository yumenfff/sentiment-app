# ---- Этап 1: сборка jar и минимальной JRE ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests
RUN jlink \
    --add-modules java.base,java.logging,java.xml,java.naming,java.desktop,java.management,java.sql,java.instrument,java.net.http,java.security.jgss,jdk.unsupported,jdk.crypto.ec,jdk.management \
    --strip-debug --no-man-pages --no-header-files --compress=zip-9 \
    --output /jre

# ---- Этап 2: минимальный runtime-образ ----
FROM alpine:3.22
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /jre /opt/jre
COPY --from=build /src/target/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["/opt/jre/bin/java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
