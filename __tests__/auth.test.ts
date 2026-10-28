import { expect, type Page, test } from '@playwright/test';

// Способ авторизации студенту не навязан: cookie-сессия или токен — тесты этого не различают
// и не должны. Проверяется только наблюдаемое поведение.

// Уникальный email на каждый прогон: база между прогонами может не пересоздаваться,
// а повторная регистрация того же адреса законно упадёт с ошибкой «email занят».
const uniqueEmail = (prefix: string): string =>
  `${prefix}+${Date.now()}${Math.floor(Math.random() * 1000)}@example.com`;

const PASSWORD = 'secret123';

const register = async (page: Page, email: string): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-signup').click();
  await page.getByTestId('auth-email').fill(email);
  await page.getByTestId('auth-password').fill(PASSWORD);
  await page.getByTestId('auth-submit').click();
};

const signIn = async (
  page: Page,
  email: string,
  password = PASSWORD,
): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-signin').click();
  await page.getByTestId('auth-email').fill(email);
  await page.getByTestId('auth-password').fill(password);
  await page.getByTestId('auth-submit').click();
};

test('new user signs up and ends up authenticated', async ({ page }) => {
  await register(page, uniqueEmail('signup'));

  // Признак авторизации — появление личного раздела и выхода в навигации.
  await expect(page.getByTestId('nav-account')).toBeVisible();
  await expect(page.getByTestId('nav-signout')).toBeVisible();
});

test('registered user signs in with email and password', async ({ page }) => {
  const email = uniqueEmail('signin');
  await register(page, email);
  await expect(page.getByTestId('nav-signout')).toBeVisible();

  await page.getByTestId('nav-signout').click();
  await expect(page.getByTestId('nav-signin')).toBeVisible();

  await signIn(page, email);
  await expect(page.getByTestId('nav-account')).toBeVisible();
});

test('after sign out the account area is unavailable', async ({ page }) => {
  await register(page, uniqueEmail('signout'));
  await expect(page.getByTestId('nav-signout')).toBeVisible();

  await page.getByTestId('nav-signout').click();

  await expect(page.getByTestId('nav-account')).toBeHidden();
  await expect(page.getByTestId('nav-signin')).toBeVisible();
});

test('sign up with a taken email is rejected', async ({ page }) => {
  const email = uniqueEmail('duplicate');
  await register(page, email);
  await expect(page.getByTestId('nav-signout')).toBeVisible();
  await page.getByTestId('nav-signout').click();
  await expect(page.getByTestId('nav-signin')).toBeVisible();

  await register(page, email);

  await expect(page.getByTestId('auth-error')).toBeVisible();
  await expect(page.getByTestId('nav-account')).toBeHidden();
});

test('sign in with a wrong password is rejected', async ({ page }) => {
  const email = uniqueEmail('wrongpass');
  await register(page, email);
  await page.getByTestId('nav-signout').click();
  await expect(page.getByTestId('nav-signin')).toBeVisible();

  await signIn(page, email, 'definitely-wrong');

  await expect(page.getByTestId('auth-error')).toBeVisible();
  await expect(page.getByTestId('nav-account')).toBeHidden();
});

test('auth state survives a page reload', async ({ page }) => {
  await register(page, uniqueEmail('reload'));
  await expect(page.getByTestId('nav-account')).toBeVisible();

  await page.reload({ waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('nav-account')).toBeVisible();
  await expect(page.getByTestId('nav-signout')).toBeVisible();
});

test('guest cannot open the account area directly', async ({ page }) => {
  await page.goto('/account', { waitUntil: 'domcontentloaded' });

  // Приложение либо увело на вход, либо просто не показало список заказов —
  // важно, что чужие данные не отрисовались.
  await expect(page.getByTestId('account-orders')).toBeHidden();
  await expect(page.getByTestId('nav-signin')).toBeVisible();
});
