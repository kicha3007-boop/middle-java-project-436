import { expect, type Page, test } from '@playwright/test';

// Сквозной сценарий покупки. Все предусловия готовятся через интерфейс: тесты не знают ни формы
// API, ни состава каталога — только пользовательские действия и data-testid.

const uniqueEmail = (prefix: string): string =>
  `${prefix}+${Date.now()}${Math.floor(Math.random() * 1000)}@example.com`;

const PASSWORD = 'secret123';

const parseAmount = (text: string): number =>
  Number(text.replace(/[^\d]/g, ''));

const register = async (page: Page, email: string): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-signup').click();
  await page.getByTestId('auth-email').fill(email);
  await page.getByTestId('auth-password').fill(PASSWORD);
  await page.getByTestId('auth-submit').click();
  await expect(page.getByTestId('nav-account')).toBeVisible();
};

// На корневом адресе — главная, каталог живёт отдельной страницей: попадаем в него ссылкой из
// шапки, потому что адрес каталога контрактом не зафиксирован.
const openCatalog = async (page: Page): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-catalog').click();
  await expect(page.getByTestId('catalog-list')).toBeVisible();
};

const addAvailableProductToCart = async (
  page: Page,
  quantity = 1,
): Promise<number> => {
  await openCatalog(page);
  await page.getByTestId('filter-available').check();
  await expect
    .poll(async () => page.getByTestId('catalog-item').count())
    .toBeGreaterThan(0);

  await page.getByTestId('catalog-item-name').first().click();
  const price = parseAmount(
    (await page.getByTestId('product-price').textContent()) ?? '',
  );
  await page.getByTestId('product-add-to-cart').click();

  await page.getByTestId('nav-cart').click();
  await expect(page.getByTestId('cart-total')).toBeVisible();
  if (quantity !== 1) {
    await page.getByTestId('cart-item-qty').first().fill(String(quantity));
    await expect
      .poll(async () =>
        parseAmount((await page.getByTestId('cart-total').textContent()) ?? ''),
      )
      .toBe(price * quantity);
  }
  return price * quantity;
};

const fillCheckout = async (
  page: Page,
  method: 'delivery' | 'pickup',
): Promise<void> => {
  await page.getByTestId('cart-checkout').click();
  await expect(page.getByTestId('checkout-form')).toBeVisible();

  // Выбираем по значению опции (delivery/pickup), а не по подписи: тексты в интерфейсе
  // студент пишет сам, и тест от них зависеть не должен.
  await page.getByTestId('checkout-method').selectOption(method);
  await page.getByTestId('checkout-name').fill('Иван Петров');
  await page.getByTestId('checkout-phone').fill('+79990001122');
  if (method === 'delivery') {
    await page.getByTestId('checkout-address').fill('ул. Ленина, 15, кв. 42');
  }
};

test('guest cannot place an order', async ({ page }) => {
  await addAvailableProductToCart(page);

  await page.getByTestId('cart-checkout').click();

  // Приложение либо увело на вход, либо не дало отправить заказ — успеха быть не должно.
  await expect(page.getByTestId('order-success')).toBeHidden();
  await expect(page.getByTestId('nav-signin')).toBeVisible();
});

test('signed in user places an order and gets confirmation', async ({
  page,
}) => {
  await register(page, uniqueEmail('buyer'));
  const expected = await addAvailableProductToCart(page, 2);

  await fillCheckout(page, 'delivery');
  await page.getByTestId('checkout-submit').click();

  await expect(page.getByTestId('order-success')).toBeVisible();
  // Итог считает бэкенд, но пользователь должен увидеть ту же сумму, что была в корзине.
  expect(
    parseAmount((await page.getByTestId('order-total').textContent()) ?? ''),
  ).toBe(expected);
});

test('cart is emptied after checkout', async ({ page }) => {
  await register(page, uniqueEmail('clear'));
  await addAvailableProductToCart(page);

  await fillCheckout(page, 'pickup');
  await page.getByTestId('checkout-submit').click();
  await expect(page.getByTestId('order-success')).toBeVisible();

  await page.getByTestId('nav-cart').click();
  await expect(page.getByTestId('cart-empty')).toBeVisible();
});

test('address is required for delivery', async ({ page }) => {
  await register(page, uniqueEmail('noaddress'));
  await addAvailableProductToCart(page);

  await page.getByTestId('cart-checkout').click();
  await expect(page.getByTestId('checkout-form')).toBeVisible();
  await page.getByTestId('checkout-method').selectOption('delivery');
  await page.getByTestId('checkout-name').fill('Иван Петров');
  await page.getByTestId('checkout-phone').fill('+79990001122');
  // Адрес намеренно не заполняем.
  await page.getByTestId('checkout-submit').click();

  await expect(page.getByTestId('order-success')).toBeHidden();
  await expect(page.getByTestId('checkout-form')).toBeVisible();
});

test('pickup is ordered without an address', async ({ page }) => {
  await register(page, uniqueEmail('pickup'));
  const expected = await addAvailableProductToCart(page);

  await fillCheckout(page, 'pickup');
  await page.getByTestId('checkout-submit').click();

  await expect(page.getByTestId('order-success')).toBeVisible();
  expect(
    parseAmount((await page.getByTestId('order-total').textContent()) ?? ''),
  ).toBe(expected);
});

test('account shows the placed order with details', async ({ page }) => {
  await register(page, uniqueEmail('account'));
  const expected = await addAvailableProductToCart(page, 2);

  await fillCheckout(page, 'delivery');
  await page.getByTestId('checkout-submit').click();
  await expect(page.getByTestId('order-success')).toBeVisible();

  await page.getByTestId('nav-account').click();
  await expect(page.getByTestId('account-orders')).toBeVisible();
  await expect(page.getByTestId('account-order-item')).toHaveCount(1);

  const order = page.getByTestId('account-order-item').first();
  // Статус читаем атрибутом: в проекте он всегда «оплачен», но текст подписи произволен.
  await expect(order.getByTestId('order-status')).toHaveAttribute(
    'data-status',
    'paid',
  );
  expect(
    parseAmount((await order.getByTestId('order-total').textContent()) ?? ''),
  ).toBe(expected);
});

test('account shows only own orders', async ({ page }) => {
  await register(page, uniqueEmail('owner'));
  await addAvailableProductToCart(page);
  await fillCheckout(page, 'pickup');
  await page.getByTestId('checkout-submit').click();
  await expect(page.getByTestId('order-success')).toBeVisible();

  await page.getByTestId('nav-signout').click();
  await expect(page.getByTestId('nav-signin')).toBeVisible();

  // Второй пользователь не должен увидеть покупок первого.
  await register(page, uniqueEmail('stranger'));
  await page.getByTestId('nav-account').click();

  await expect(page.getByTestId('account-orders-empty')).toBeVisible();
  await expect(page.getByTestId('account-order-item')).toHaveCount(0);
});

test('order with unavailable product is rejected', async ({ page }) => {
  await register(page, uniqueEmail('unavailable'));

  // Кладём в корзину доступный товар, а затем через localStorage добавляем недоступный:
  // интерфейс такого сделать не даёт, но сервер обязан отбить заказ независимо от клиента.
  await addAvailableProductToCart(page);

  // Ожидание выдачи внутри openCatalog обязательно: evaluateAll ничего не ждёт, и на ещё не
  // отрисованном каталоге findIndex вернул бы -1 по пустому набору.
  await openCatalog(page);
  const unavailableIndex = await page
    .getByTestId('catalog-item-availability')
    .evaluateAll((nodes) =>
      nodes.findIndex(
        (node) => node.getAttribute('data-available') === 'false',
      ),
    );
  expect(
    unavailableIndex,
    'в каталоге должен быть недоступный товар',
  ).toBeGreaterThanOrEqual(0);
  await page.getByTestId('catalog-item-name').nth(unavailableIndex).click();
  await expect(page.getByTestId('product-name')).toBeVisible();
  const unavailableId =
    new URL(page.url()).pathname.split('/').filter(Boolean).pop() ?? '';
  expect(unavailableId).not.toBe('');

  await page.evaluate((productId) => {
    // Ключ хранения не зафиксирован контрактом, поэтому дописываем товар в каждый массив,
    // похожий на корзину: так тест не привязывается к имени ключа.
    for (let i = 0; i < window.localStorage.length; i += 1) {
      const key = window.localStorage.key(i);
      if (!key) continue;
      const raw = window.localStorage.getItem(key);
      if (!raw) continue;
      try {
        const parsed = JSON.parse(raw);
        if (
          Array.isArray(parsed) &&
          parsed.length > 0 &&
          'productId' in parsed[0]
        ) {
          parsed.push({ productId, quantity: 1 });
          window.localStorage.setItem(key, JSON.stringify(parsed));
        }
      } catch {
        // не JSON — не корзина
      }
    }
  }, unavailableId);

  await page.getByTestId('nav-cart').click();
  await expect(page.getByTestId('cart-total')).toBeVisible();
  await fillCheckout(page, 'pickup');
  await page.getByTestId('checkout-submit').click();

  await expect(page.getByTestId('order-error')).toBeVisible();
  await expect(page.getByTestId('order-success')).toBeHidden();
});
