package hexlet.code.model;

/** Позиция заказа — снимок названия и цены товара на момент покупки. */
public record OrderItem(long productId, String productName, int priceAtPurchase, int quantity) {}
