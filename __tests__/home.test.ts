import { expect, test } from '@playwright/test';

// Главная — не каталог: на ней промо-блоки магазина, каждый ведёт на страницу своего товара.
// Тексты промо студент придумывает сам и хранит в базе, поэтому тест не сверяет ни одну
// формулировку: проверяется, что блоки есть и что переход из них работает.

test('home shows promo blocks', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('home-promo')).toBeVisible();
  // Не меньше двух — по контракту на сиды из чеклиста. Один блок в списке не доказывает, что
  // главная показывает подборку, а не единственный захардкоженный баннер.
  expect(await page.getByTestId('home-promo-item').count()).toBeGreaterThan(1);
});

test('promo leads to its product page', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('home-promo')).toBeVisible();

  await page.getByTestId('home-promo-item').first().click();

  await expect(page.getByTestId('product-name')).not.toBeEmpty();
  await expect(page.getByTestId('product-price')).not.toBeEmpty();
  // В корзину товар из промо добавляется так же, как из каталога: промо-блоки ведут на
  // доступные товары (контракт на сиды), поэтому кнопка активна.
  await expect(page.getByTestId('product-add-to-cart')).toBeEnabled();
});

test('catalog is reachable from home', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });

  await page.getByTestId('nav-catalog').click();

  await expect(page.getByTestId('catalog-list')).toBeVisible();
  await expect(page.getByTestId('catalog-filters')).toBeVisible();
});
