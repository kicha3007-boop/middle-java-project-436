import { expect, type Page, test } from '@playwright/test';

// Каталог студент наполняет сам, поэтому тесты не ждут конкретных товаров: проверяется
// поведение — что фильтр сужает выдачу, поиск оставляет подходящее, сброс возвращает всё.
// Гарантии по данным даёт контракт на сиды из чеклиста: не меньше трёх категорий,
// не меньше двенадцати товаров, минимум один недоступен, цены разного порядка,
// товаров хватает минимум на две страницы.

// На корневом адресе — главная, каталог живёт отдельной страницей. Её адрес контрактом не
// зафиксирован, поэтому попадаем в каталог ссылкой из шапки, а не прямым переходом.
const openCatalog = async (page: Page): Promise<void> => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByTestId('nav-catalog').click();
  await expect(page.getByTestId('catalog-list')).toBeVisible();
};

const itemCount = (page: Page): Promise<number> =>
  page.getByTestId('catalog-item').count();

const visibleNames = async (page: Page): Promise<string[]> => {
  const names = await page.getByTestId('catalog-item-name').allTextContents();
  return names.map((name) => name.trim());
};

test('product card shows name price and availability', async ({ page }) => {
  await openCatalog(page);
  const first = page.getByTestId('catalog-item').first();

  await expect(first.getByTestId('catalog-item-name')).not.toBeEmpty();
  await expect(first.getByTestId('catalog-item-price')).not.toBeEmpty();
  await expect(first.getByTestId('catalog-item-availability')).toBeVisible();
});

test('availability is marked with data-available', async ({ page }) => {
  await openCatalog(page);

  // Читаем состояние атрибутом, а не текстом: формулировки в интерфейсе меняются,
  // а тест от них зависеть не должен.
  const flags = await page
    .getByTestId('catalog-item-availability')
    .evaluateAll((nodes) =>
      nodes.map((node) => node.getAttribute('data-available')),
    );

  expect(flags.length).toBeGreaterThan(0);
  for (const flag of flags) {
    expect(['true', 'false']).toContain(flag);
  }
});

// Слаги категорий из списка выбора. Категории приходят отдельным запросом, поэтому их
// появление ждём отдельно: иначе evaluateAll прочитает список, в котором пока только
// «все категории».
const categoryValues = async (page: Page): Promise<string[]> => {
  const category = page.getByTestId('filter-category');
  await expect(category.locator('option').nth(1)).toBeAttached();
  const values = await category
    .locator('option')
    .evaluateAll((nodes) =>
      nodes
        .map((node) => (node as HTMLOptionElement).value)
        .filter((value) => value !== ''),
    );
  expect(values.length).toBeGreaterThan(0);
  return values;
};

test('category filter narrows the list', async ({ page }) => {
  await openCatalog(page);
  const total = await itemCount(page);

  const category = page.getByTestId('filter-category');
  const values = await categoryValues(page);
  await category.selectOption(values[0]);

  await expect
    .poll(async () => itemCount(page), { message: 'выдача должна сузиться' })
    .toBeLessThan(total);
  expect(await itemCount(page)).toBeGreaterThan(0);
});

test('search by partial name keeps matching product', async ({ page }) => {
  await openCatalog(page);
  const names = await visibleNames(page);
  expect(names.length).toBeGreaterThan(0);

  // Берём фрагмент названия существующего товара — так тест не зависит от состава каталога.
  const fragment = names[0].split(' ')[0];
  await page.getByTestId('filter-search').fill(fragment);

  // Ждём именно применения фильтра, а не появления хоть чего-нибудь: поиск отправляет запрос
  // с задержкой, поэтому сразу после ввода на экране законно остаётся прежняя выдача.
  await expect
    .poll(
      async () => {
        const shown = await visibleNames(page);
        return (
          shown.length > 0 &&
          shown.every((name) =>
            name.toLowerCase().includes(fragment.toLowerCase()),
          )
        );
      },
      { message: 'в выдаче должны остаться только подходящие товары' },
    )
    .toBe(true);
});

test('price filter changes the list', async ({ page }) => {
  await openCatalog(page);
  const total = await itemCount(page);

  // Верхняя граница ниже самой дорогой позиции обязана отсечь хотя бы один товар:
  // по контракту на сиды цены в каталоге разного порядка.
  await page.getByTestId('filter-price-max').fill('20000');

  await expect
    .poll(async () => itemCount(page), {
      message: 'дорогие товары должны уйти',
    })
    .toBeLessThan(total);
});

test('in stock only hides unavailable products', async ({ page }) => {
  await openCatalog(page);

  await page.getByTestId('filter-available').check();

  await expect
    .poll(
      async () =>
        page
          .getByTestId('catalog-item-availability')
          .evaluateAll((nodes) =>
            nodes.every(
              (node) => node.getAttribute('data-available') === 'true',
            ),
          ),
      { message: 'в выдаче должны остаться только доступные' },
    )
    .toBe(true);
});

test('resetting filters restores the full list', async ({ page }) => {
  await openCatalog(page);
  const total = await itemCount(page);

  await page.getByTestId('filter-search').fill('заведомо-несуществующий-товар');
  await expect(page.getByTestId('catalog-empty')).toBeVisible();

  await page.getByTestId('filter-reset').click();

  await expect.poll(async () => itemCount(page)).toBe(total);
});

test('no matches shows the empty state', async ({ page }) => {
  await openCatalog(page);

  await page.getByTestId('filter-search').fill('заведомо-несуществующий-товар');

  await expect(page.getByTestId('catalog-empty')).toBeVisible();
  expect(await itemCount(page)).toBe(0);
});

test('next page shows a different set of cards', async ({ page }) => {
  await openCatalog(page);
  await expect(page.getByTestId('catalog-pagination')).toBeVisible();

  const firstPage = await visibleNames(page);
  await page.getByTestId('catalog-page-next').click();

  await expect
    .poll(async () => (await visibleNames(page)).join('|'), {
      message: 'набор должен измениться',
    })
    .not.toBe(firstPage.join('|'));

  const secondPage = await visibleNames(page);
  expect(secondPage.length).toBeGreaterThan(0);
  // Страницы не должны пересекаться: иначе пагинация «съезжает» и часть товаров недостижима.
  expect(secondPage.filter((name) => firstPage.includes(name))).toEqual([]);

  await page.getByTestId('catalog-page-prev').click();
  await expect
    .poll(async () => (await visibleNames(page)).join('|'))
    .toBe(firstPage.join('|'));
});

test('catalog state survives reload and back', async ({ page }) => {
  await openCatalog(page);
  const total = await itemCount(page);

  const values = await categoryValues(page);
  await page.getByTestId('filter-category').selectOption(values[0]);
  await expect
    .poll(async () => itemCount(page), { message: 'выдача должна сузиться' })
    .toBeLessThan(total);
  const filtered = await visibleNames(page);

  // Состояние выдачи живёт в адресе, а не только в памяти страницы: перезагрузка возвращает
  // ту же выдачу и те же значения контролов, а не каталог с нуля.
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('catalog-list')).toBeVisible();
  await expect(page.getByTestId('filter-category')).toHaveValue(values[0]);
  await expect
    .poll(async () => (await visibleNames(page)).join('|'), {
      message: 'после перезагрузки выдача должна совпасть',
    })
    .toBe(filtered.join('|'));

  // «Назад» отменяет последнее изменение фильтров, а не уводит со страницы каталога.
  await page.goBack({ waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('catalog-list')).toBeVisible();
  await expect
    .poll(async () => itemCount(page), {
      message: '«назад» должен вернуть выдачу без фильтра',
    })
    .toBe(total);
});
