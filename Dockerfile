# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build the React frontend
# ---------------------------------------------------------------------------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2 — build the Spring Boot jar with the React build bundled in
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /app
COPY backend/ ./
# Serve the compiled SPA from Spring Boot's static resources (same origin, no CORS)
COPY --from=frontend /app/frontend/dist/ ./src/main/resources/static/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ---------------------------------------------------------------------------
# Stage 3 — minimal runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=backend /app/build/libs/*.jar app.jar
# Cap heap so the JVM stays within small free-tier containers (e.g. Render 512 MB)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
