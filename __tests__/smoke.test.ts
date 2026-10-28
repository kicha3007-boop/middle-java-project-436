import { expect, test } from '@playwright/test';

// Минимальная проверка: приложение поднялось, главная отдаёт промо-блоки, из неё открывается
// каталог, а прямой заход на внутренний путь не даёт 404. Если этот файл красный, остальные
// тесты разбирать бессмысленно.

test('home page opens and shows promos', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('/', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('home-promo')).toBeVisible();
  expect(errors).toEqual([]);
});

test('catalog opens from the header', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-catalog').click();

  await expect(page.getByTestId('catalog-list')).toBeVisible();
  expect(await page.getByTestId('catalog-item').count()).toBeGreaterThan(0);
});

test('client routing survives a direct URL visit', async ({ page }) => {
  // Прямой заход на внутренний путь — классическое место, где ломается SPA без fallback
  // на index.html. Проверяем, что сервер отдаёт приложение, а не 404.
  const response = await page.goto('/cart', { waitUntil: 'domcontentloaded' });

  expect(response?.status()).toBe(200);
  await expect(page.getByTestId('nav-cart')).toBeVisible();
});
