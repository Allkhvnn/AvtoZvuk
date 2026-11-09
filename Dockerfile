FROM node:22-alpine AS frontend
WORKDIR /workspace/design
RUN corepack enable
COPY design/package.json design/pnpm-lock.yaml ./
RUN corepack pnpm install --frozen-lockfile
COPY design/ ./
RUN corepack pnpm build

FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/
COPY --from=frontend /workspace/design/dist design/dist
RUN chmod +x gradlew && ./gradlew bootJar -x frontendBuild --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 avtozvuk \
    && adduser -S -D -u 10001 -G avtozvuk avtozvuk
WORKDIR /app
COPY --from=backend --chown=avtozvuk:avtozvuk /workspace/build/libs/kz-0.0.1-SNAPSHOT.jar app.jar
USER avtozvuk
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

