.DEFAULT_GOAL := build

FRONTEND_DIST = node_modules/@hexlet/java-online-store-frontend/dist
APP_URL ?= http://localhost:8080

# Зависимости: npm (фронтенд Хекслета, TypeSpec, Playwright) и Gradle.
install:
	npm ci
	./gradlew classes

# Раскладка собранного фронтенда Хекслета в public/, откуда его раздаёт сервер.
frontend:
	rm -rf public
	mkdir -p public
	cp -R $(FRONTEND_DIST)/. public/

# Сборка приложения: фронтенд + дистрибутив с jar-ами в build/install/app.
build: frontend
	./gradlew installDist

# Запуск собранного приложения. Нужны DATABASE_URL и (необязательно) PORT.
start:
	./build/install/app/bin/app

# Запуск из исходников через Gradle — для разработки.
dev:
	./gradlew run

# Свои тесты API: поднимают приложение и бьют по нему HTTP-клиентом.
# База — TEST_DATABASE_URL, иначе DATABASE_URL.
test:
	./gradlew test

test-api: test

# Браузерные тесты Хекслета против запущенного приложения.
test-e2e:
	APP_URL=$(APP_URL) npx playwright test

lint:
	./gradlew spotlessCheck

lint-fix:
	./gradlew spotlessApply

report:
	./gradlew jacocoTestReport

# Пересборка спецификации OpenAPI из TypeSpec: contract/main.tsp -> contract/openapi.yaml.
contract:
	npx tsp compile contract

clean:
	./gradlew clean
	rm -rf public tmp

# Локальный запуск в Docker: PostgreSQL + приложение из Dockerfile.
compose-up:
	docker compose up -d --build

compose-down:
	docker compose down -v

.PHONY: install frontend build start dev test test-api test-e2e lint lint-fix report contract clean compose-up compose-down
