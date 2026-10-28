package hexlet.code.dto;

import java.util.List;

public record OrderDto(
        String id,
        String status,
        List<OrderItemDto> items,
        ShippingDto shipping,
        MoneyDto total,
        String createdAt) {}
