FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend /build/frontend/dist frontend/dist
RUN mvn -B -ntp -f backend/pom.xml clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=backend /build/backend/target/enterprise-kb-1.0.0.jar /app/enterprise-kb.jar
ENV APP_BIND_ADDRESS=0.0.0.0 APP_PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/enterprise-kb.jar"]
