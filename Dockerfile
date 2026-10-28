# Собранный фронтенд Хекслета: из пакета берётся готовый бандл.
FROM node:22-alpine AS frontend
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Сборка приложения. К базе не обращается: её на этом этапе нет.
# Gradle берётся из образа той же версии, что и в wrapper, — без скачивания дистрибутива.
FROM gradle:9.7.1-jdk25 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Отдельный слой с зависимостями: пересобирается только при правке файлов сборки.
RUN gradle --no-daemon dependencies > /dev/null
COPY src src
RUN gradle --no-daemon installDist

# Рантайм: только JRE, собранный дистрибутив и статика фронтенда.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/install/app ./
COPY --from=frontend /build/node_modules/@hexlet/java-online-store-frontend/dist ./public
ENV STATIC_DIR=/app/public
# При старте: миграции, заливка каталога (повторяемая), затем сервер на PORT (по умолчанию 8080).
CMD ["/app/bin/app"]
