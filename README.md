# Бекенд для интернет-магазина (Java)

[![hexlet-check](https://github.com/kicha3007-boop/middle-java-project-436/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/kicha3007-boop/middle-java-project-436/actions)
[![CI](https://github.com/kicha3007-boop/middle-java-project-436/actions/workflows/ci.yml/badge.svg)](https://github.com/kicha3007-boop/middle-java-project-436/actions/workflows/ci.yml)

HTTP API интернет-магазина комплектующих: регистрация и вход, каталог с фильтрами и пагинацией,
промо-блоки главной, оформление заказа и личный кабинет с историей. Данные хранятся в PostgreSQL.
Фронтенд — готовое приложение Хекслета (npm-пакет `@hexlet/java-online-store-frontend`), сервер
раздаёт его статику и отвечает на `/api/...` на том же порту.

Учебный проект Хекслета: https://ru.hexlet.io/programs/middle-java
Как это должно работать: https://files.hexlet.app/a/qf7bsq

Приложение в облако не разворачивается: всё поднимается локально (Docker или JDK + PostgreSQL).

## Стек

- Java 25, Gradle 9 (wrapper в репозитории)
- Javalin 7, Jackson, JDBC + HikariCP, Flyway (миграции), PostgreSQL
- Пароли — PBKDF2WithHmacSHA256 из JDK, сессии — в таблице `sessions` (в cookie только токен)
- Sentry SDK для сборщика ошибок (Sentry или Bugsink), включается переменной `MONITORING_DSN`
- TypeSpec → OpenAPI для контракта, JUnit 5 + AssertJ для тестов API, Playwright для браузерных тестов
- Spotless (google-java-format, AOSP) — линтер

## Быстрый старт в Docker

Нужен только Docker. Поднимается PostgreSQL и приложение из `Dockerfile` — так же, как его запускает
проверка Хекслета:

```bash
git clone https://github.com/kicha3007-boop/middle-java-project-436.git
cd middle-java-project-436
cp .env.example .env      # при необходимости поправьте значения
make compose-up           # docker compose up -d --build
```

Магазин откроется на http://localhost:8080, проверка живости — `curl http://localhost:8080/api/health`.
Остановить и удалить данные базы: `make compose-down`.

При старте контейнер сам применяет миграции и заливает каталог; повторный запуск ничего не задваивает.

## Запуск без Docker

Нужны JDK 25, Node.js 22 и PostgreSQL (можно поднять только базу: `docker compose up -d db`).

```bash
make install              # npm ci + сборка классов
make build                # фронтенд в public/ + дистрибутив в build/install/app
export DATABASE_URL=postgres://postgres:password@localhost:5432/appdb
make start                # http://localhost:8080
```

Для разработки можно запускать из исходников: `make dev` (`./gradlew run`).

## Переменные окружения

Пример — в [.env.example](.env.example). Файл `.env` в git не попадает.

| Переменная | Обязательна | Назначение |
|---|---|---|
| `DATABASE_URL` | да | `postgres://user:password@host:5432/db` — приложение само переводит её в JDBC |
| `PORT` | нет | порт сервера, по умолчанию `8080`; слушается `0.0.0.0` |
| `STATIC_DIR` | нет | каталог со статикой фронтенда, по умолчанию `public` |
| `MONITORING_DSN` | нет | DSN сборщика ошибок; пусто — мониторинг выключен |
| `TEST_DATABASE_URL` | нет | отдельная база для тестов API; без неё тесты берут `DATABASE_URL` |

## Тесты

Свои тесты API (`src/test`) поднимают приложение целиком на свободном порту и бьют по нему HTTP-клиентом
с cookie. Перед каждым тестом база приводится в известное состояние: пользователи, сессии и заказы
очищаются, каталог заливается заново. Поэтому тестам нужна отдельная база:

```bash
docker compose up -d db
docker compose exec db createdb -U postgres appdb_test
export TEST_DATABASE_URL=postgres://postgres:password@localhost:5432/appdb_test
make test                 # ./gradlew test, отчёт покрытия — make report
```

Браузерные тесты Хекслета (`__tests__`, Playwright) гоняются против запущенного приложения:

```bash
npx playwright install chromium
make start                # в соседнем терминале
make test-e2e             # APP_URL=http://localhost:8080 npx playwright test
```

Следы упавших сценариев (скриншоты, видео, трассировка) — в `tmp/artifacts`.

## Линтер

```bash
make lint                 # ./gradlew spotlessCheck
make lint-fix             # ./gradlew spotlessApply
```

## Контракт API

Контракт описан в TypeSpec: [contract/main.tsp](contract/main.tsp), сгенерированная спецификация —
[contract/openapi.yaml](contract/openapi.yaml). Пересборка: `make contract`. CI проверяет, что
`openapi.yaml` совпадает с тем, что генерируется из `main.tsp`.

| Метод и путь | Назначение |
|---|---|
| `GET /api/health` | проверка живости |
| `POST /api/users` | регистрация (сразу открывает сессию) |
| `POST /api/session` / `DELETE /api/session` | вход / выход |
| `GET /api/me` | текущий пользователь, аноним — `401` |
| `GET /api/categories` | категории |
| `GET /api/promos` | промо-блоки с вложенным товаром |
| `GET /api/products` | каталог: `category`, `q`, `priceMin`, `priceMax`, `available`, `page`, `perPage` |
| `GET /api/products/{id}` | товар |
| `POST /api/orders` | оформление заказа (итог считает сервер) |
| `GET /api/orders`, `GET /api/orders/{id}` | заказы текущего пользователя |

Ошибки — в формате Problem Details: `{"type": "https://hexlet.io/problems/<тип>", "title", "status", "detail"}`.

## Устройство кода

- `App` — сборка приложения: маршруты, статика, обработчики ошибок; `main` читает окружение.
- `controller` — тонкие обработчики: разбор запроса, вызов сервиса или репозитория, ответ.
- `request` — разбор и проверка тел и параметров запросов.
- `service` — регистрация и сессии, хеширование паролей, оформление заказа.
- `repository` — весь SQL (только подготовленные выражения).
- `dto` + `ResponseMapper` — преобразование записей базы в ответы API (id строками, суммы — `Money`).
- `http` — единый формат ошибок, SPA-fallback, сессионная cookie и проверка авторизации.
- `src/main/resources/db/migration` — миграции Flyway, `db/seed/catalog.sql` — повторяемая заливка каталога.

---

<details>
<summary>Автоматические тесты Хекслета</summary>

Тесты запускаются на каждый коммит. За запуск отвечает файл `.github/workflows/hexlet-check.yml` — не удаляйте и не переименовывайте ни его, ни репозиторий.

</details>

## О Хекслете

[Хекслет](https://ru.hexlet.io/) — школа программирования: авторские программы обучения с практикой, поддержкой наставников и реальными проектами, которые остаются в резюме. Этот репозиторий — один из таких проектов.
