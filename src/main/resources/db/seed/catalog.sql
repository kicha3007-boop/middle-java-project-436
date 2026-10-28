-- Заливка каталога. Выполняется при каждом запуске приложения и повторяема:
-- записи опознаются по слагу (у промо-блока — по товару), повторный запуск только
-- обновляет их, не создавая дублей. Пользователей заливка не создаёт.

INSERT INTO categories (slug, name)
SELECT v.slug, v.name
FROM (VALUES
    (1, 'videokarty', 'Видеокарты'),
    (2, 'processory', 'Процессоры'),
    (3, 'materinskie-platy', 'Материнские платы')
) AS v (position, slug, name)
ORDER BY v.position
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO products (slug, name, description, price, category_id, image_url, available)
SELECT v.slug, v.name, v.description, v.price, c.id, v.image_url, v.available
FROM (VALUES
    (1, 'nvidia-geforce-rtx-4070', 'NVIDIA GeForce RTX 4070',
        'Видеокарта 12 ГБ GDDR6X для игр в 1440p.', 62990, 'videokarty',
        '/images/nvidia-geforce-rtx-4070.svg', TRUE),
    (2, 'nvidia-geforce-rtx-4090', 'NVIDIA GeForce RTX 4090',
        'Флагманская видеокарта 24 ГБ GDDR6X для 4K и нейросетей.', 199990, 'videokarty',
        '/images/nvidia-geforce-rtx-4090.svg', FALSE),
    (3, 'amd-ryzen-5-7600x', 'AMD Ryzen 5 7600X',
        'Шестиядерный процессор AM5 с частотой до 5,3 ГГц.', 17990, 'processory',
        '/images/amd-ryzen-5-7600x.svg', TRUE),
    (4, 'asus-prime-b650m-a', 'ASUS Prime B650M-A',
        'Материнская плата micro-ATX на чипсете B650 под AM5.', 12990, 'materinskie-platy',
        '/images/asus-prime-b650m-a.svg', TRUE),
    (5, 'nvidia-geforce-rtx-4060-ti', 'NVIDIA GeForce RTX 4060 Ti',
        'Видеокарта 8 ГБ GDDR6 для игр в 1080p.', 42990, 'videokarty',
        '/images/nvidia-geforce-rtx-4060-ti.svg', TRUE),
    (6, 'amd-ryzen-7-7800x3d', 'AMD Ryzen 7 7800X3D',
        'Восьмиядерный игровой процессор с 3D V-Cache.', 38990, 'processory',
        '/images/amd-ryzen-7-7800x3d.svg', TRUE),
    (7, 'msi-mag-b760-tomahawk', 'MSI MAG B760 Tomahawk',
        'Материнская плата ATX на чипсете B760 под LGA1700.', 18990, 'materinskie-platy',
        '/images/msi-mag-b760-tomahawk.svg', TRUE),
    (8, 'amd-radeon-rx-7800-xt', 'AMD Radeon RX 7800 XT',
        'Видеокарта 16 ГБ GDDR6 для игр в 1440p.', 54990, 'videokarty',
        '/images/amd-radeon-rx-7800-xt.svg', TRUE),
    (9, 'amd-ryzen-9-7950x', 'AMD Ryzen 9 7950X',
        'Шестнадцатиядерный процессор для работы и рендеринга.', 54990, 'processory',
        '/images/amd-ryzen-9-7950x.svg', TRUE),
    (10, 'gigabyte-x670-aorus-elite', 'Gigabyte X670 Aorus Elite',
        'Материнская плата ATX на чипсете X670 с PCIe 5.0.', 27990, 'materinskie-platy',
        '/images/gigabyte-x670-aorus-elite.svg', TRUE),
    (11, 'intel-core-i7-14700k', 'Intel Core i7-14700K',
        'Двадцатиядерный процессор LGA1700 с разблокированным множителем.', 39990, 'processory',
        '/images/intel-core-i7-14700k.svg', TRUE),
    (12, 'intel-core-i5-14400f', 'Intel Core i5-14400F',
        'Десятиядерный процессор LGA1700 без встроенной графики.', 16990, 'processory',
        NULL, TRUE),
    (13, 'asrock-b650m-pro-rs', 'ASRock B650M Pro RS',
        'Недорогая материнская плата micro-ATX под AM5.', 11490, 'materinskie-platy',
        NULL, TRUE),
    (14, 'intel-arc-a770', 'Intel Arc A770',
        'Видеокарта 16 ГБ GDDR6 с аппаратным кодированием AV1.', 29990, 'videokarty',
        NULL, TRUE)
) AS v (position, slug, name, description, price, category_slug, image_url, available)
JOIN categories AS c ON c.slug = v.category_slug
ORDER BY v.position
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
    category_id = EXCLUDED.category_id,
    image_url = EXCLUDED.image_url,
    available = EXCLUDED.available;

INSERT INTO promos (title, text, product_id)
SELECT v.title, v.text, p.id
FROM (VALUES
    (1, 'Игры в 1440p без компромиссов',
        'RTX 4070 тянет современные игры на высоких настройках и не греет комнату.',
        'nvidia-geforce-rtx-4070'),
    (2, 'Лучший игровой процессор',
        'Ryzen 7 7800X3D с большим кешем — максимум кадров в секунду в любимых играх.',
        'amd-ryzen-7-7800x3d'),
    (3, 'Основа для мощной сборки',
        'Gigabyte X670 Aorus Elite: PCIe 5.0, четыре слота M.2 и надёжное питание.',
        'gigabyte-x670-aorus-elite')
) AS v (position, title, text, product_slug)
JOIN products AS p ON p.slug = v.product_slug
ORDER BY v.position
ON CONFLICT (product_id) DO UPDATE SET
    title = EXCLUDED.title,
    text = EXCLUDED.text;
