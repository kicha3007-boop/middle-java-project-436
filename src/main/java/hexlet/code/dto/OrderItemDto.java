package hexlet.code.dto;

public record OrderItemDto(
        String productId, String productName, MoneyDto priceAtPurchase, int quantity) {}
