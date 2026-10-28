import { expect, type Page, test } from '@playwright/test';

// Корзина клиентская: живёт в браузере и переживает перезагрузку. Сервер узнаёт её состав
// только при оформлении заказа, поэтому здесь никаких запросов к API не проверяется —
// только поведение интерфейса.

// В корзине хранятся идентификатор и количество, а названия и цены подтягиваются из каталога.
// Поэтому цену для проверки итога берём из интерфейса, а не из тестовых данных.
const parseAmount = (text: string): number => {
  const digits = text.replace(/[^\d]/g, '');
  return Number(digits);
};

// На корневом адресе — главная, каталог живёт отдельной страницей: попадаем в него ссылкой из
// шапки, потому что адрес каталога контрактом не зафиксирован.
const openCatalog = async (page: Page): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-catalog').click();
  await expect(page.getByTestId('catalog-list')).toBeVisible();
};

const openFirstAvailableProduct = async (page: Page): Promise<void> => {
  await openCatalog(page);

  // Берём доступный товар: недоступный в корзину положить нельзя, это отдельный тест.
  await page.getByTestId('filter-available').check();
  await expect
    .poll(async () => page.getByTestId('catalog-item').count())
    .toBeGreaterThan(0);

  await page.getByTestId('catalog-item-name').first().click();
  await expect(page.getByTestId('product-add-to-cart')).toBeVisible();
};

const addFirstAvailableToCart = async (page: Page): Promise<number> => {
  await openFirstAvailableProduct(page);
  const price = parseAmount(
    (await page.getByTestId('product-price').textContent()) ?? '',
  );
  await page.getByTestId('product-add-to-cart').click();
  return price;
};

const openCart = async (page: Page): Promise<void> => {
  await page.getByTestId('nav-cart').click();
  await expect(page.getByTestId('cart-total')).toBeVisible();
};

test('product page opens from the catalog', async ({ page }) => {
  await openFirstAvailableProduct(page);

  await expect(page.getByTestId('product-name')).not.toBeEmpty();
  await expect(page.getByTestId('product-price')).not.toBeEmpty();
  await expect(page.getByTestId('product-description')).toBeVisible();
});

test('product is added to the cart', async ({ page }) => {
  const price = await addFirstAvailableToCart(page);
  await openCart(page);

  await expect(page.getByTestId('cart-item')).toHaveCount(1);
  expect(
    parseAmount((await page.getByTestId('cart-total').textContent()) ?? ''),
  ).toBe(price);
});

test('changing quantity recalculates the total', async ({ page }) => {
  const price = await addFirstAvailableToCart(page);
  await openCart(page);

  await page.getByTestId('cart-item-qty').first().fill('3');

  await expect
    .poll(
      async () =>
        parseAmount((await page.getByTestId('cart-total').textContent()) ?? ''),
      { message: 'итог должен стать втрое больше' },
    )
    .toBe(price * 3);
});

test('line item is removed from the cart', async ({ page }) => {
  await addFirstAvailableToCart(page);
  await openCart(page);
  await expect(page.getByTestId('cart-item')).toHaveCount(1);

  await page.getByTestId('cart-item-remove').first().click();

  await expect(page.getByTestId('cart-empty')).toBeVisible();
  await expect(page.getByTestId('cart-item')).toHaveCount(0);
});

test('cart contents survive a page reload', async ({ page }) => {
  await addFirstAvailableToCart(page);
  await openCart(page);
  await expect(page.getByTestId('cart-item')).toHaveCount(1);
  const total = await page.getByTestId('cart-total').textContent();

  await page.reload({ waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('cart-item')).toHaveCount(1);
  expect(await page.getByTestId('cart-total').textContent()).toBe(total);
});

test('unavailable product cannot be added to cart', async ({ page }) => {
  await openCatalog(page);

  // Ищем недоступный товар по атрибуту, а не по тексту. По контракту на сиды такой есть.
  const index = await page
    .getByTestId('catalog-item-availability')
    .evaluateAll((nodes) =>
      nodes.findIndex(
        (node) => node.getAttribute('data-available') === 'false',
      ),
    );
  expect(
    index,
    'в каталоге должен быть хотя бы один недоступный товар',
  ).toBeGreaterThanOrEqual(0);

  await page.getByTestId('catalog-item-name').nth(index).click();
  await expect(page.getByTestId('product-name')).toBeVisible();

  // Допустимы оба варианта: кнопки нет вовсе либо она заблокирована.
  const addToCart = page.getByTestId('product-add-to-cart');
  if ((await addToCart.count()) > 0) {
    await expect(addToCart).toBeDisabled();
  }

  await page.getByTestId('nav-cart').click();
  await expect(page.getByTestId('cart-empty')).toBeVisible();
});

test('empty cart shows its state and blocks checkout', async ({ page }) => {
  await page.goto('/cart', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('cart-empty')).toBeVisible();

  const checkout = page.getByTestId('cart-checkout');
  if ((await checkout.count()) > 0) {
    await expect(checkout).toBeDisabled();
  }
});
