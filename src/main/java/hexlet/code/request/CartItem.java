package hexlet.code.request;

/** Позиция корзины в запросе на оформление: только товар и количество, без цены. */
public record CartItem(String productId, int quantity) {}
