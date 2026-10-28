import { defineConfig, devices } from '@playwright/test';

// APP_URL всегда из окружения (docker compose задаёт http://online-store.test:8080;
// локально — передать явно). Хардкод-фолбэка нет: нет переменной → понятная ошибка.
// Хост online-store.test (reserved-TLD .test) — чтобы Chromium не апгрейдил http→https.
const APP_URL = process.env.APP_URL;
if (!APP_URL) {
  throw new Error('APP_URL environment variable is required');
}

export default defineConfig({
  testDir: '__tests__',
  // Артефакты — в tmp/artifacts/traces/<файл-тест-проект>/: ровно под glob
  // грейдера Hexlet/project-action `tmp/artifacts/*/**` → GitHub-артефакт "test-results".
  // preserveOutput: 'failures-only' → для прошедших тестов каталог пуст, грейдер ничего не грузит.
  // Имя каталога = `<файл>-<название теста>` с усечением до 60 символов, поэтому названия
  // кейсов — только ASCII и короткие (см. __tests__/README.md).
  outputDir: 'tmp/artifacts/traces',
  preserveOutput: 'failures-only',
  // Один браузер: тесты делят одну базу, параллельный прогон ломал бы предусловия.
  fullyParallel: false,
  workers: 1,
  timeout: 20_000,
  expect: { timeout: 5_000 },
  // HTML-репорт — в tmp/artifacts/playwright-report, чтобы грейдер положил его в артефакт test-results
  // и студент открыл index.html локально. open: 'never' — не открывать в контейнере.
  reporter: [
    ['list'],
    ['html', { outputFolder: 'tmp/artifacts/playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: APP_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
