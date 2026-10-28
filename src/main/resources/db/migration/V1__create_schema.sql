-- Каталог: категории, товары и промо-блоки главной. Цены — целые рубли.
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    price INTEGER NOT NULL CHECK (price >= 0),
    category_id BIGINT NOT NULL REFERENCES categories (id),
    image_url VARCHAR(255),
    available BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX products_category_id_idx ON products (category_id);
CREATE INDEX products_price_idx ON products (price);

-- Один товар не участвует в двух блоках: уникальность по товару.
CREATE TABLE promos (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    product_id BIGINT NOT NULL UNIQUE REFERENCES products (id)
);

-- Email хранится нормализованным (без внешних пробелов, в нижнем регистре).
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Серверные сессии: в cookie лежит случайный токен, в базе — только его хеш.
CREATE TABLE sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    shipping_method VARCHAR(32) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone VARCHAR(64) NOT NULL,
    address TEXT,
    total INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX orders_user_id_idx ON orders (user_id);

-- Позиция хранит снимок товара: внешнего ключа на products нет намеренно,
-- чтобы удаление товара из каталога не ломало историю заказов.
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price_at_purchase INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX order_items_order_id_idx ON order_items (order_id);
