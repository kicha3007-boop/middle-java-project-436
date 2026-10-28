package hexlet.code.model;

import java.time.Instant;
import java.util.List;

public record Order(
        long id,
        long userId,
        String status,
        List<OrderItem> items,
        Shipping shipping,
        int total,
        Instant createdAt) {

    public static final String STATUS_PAID = "paid";
}
