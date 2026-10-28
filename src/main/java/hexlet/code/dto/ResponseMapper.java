package hexlet.code.dto;

import hexlet.code.model.Category;
import hexlet.code.model.Order;
import hexlet.code.model.OrderItem;
import hexlet.code.model.Product;
import hexlet.code.model.Promo;
import hexlet.code.model.Shipping;
import hexlet.code.model.User;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Преобразование записей базы в ответы API. Единственное место, где идентификаторы становятся
 * строками, а суммы — объектами Money.
 */
public final class ResponseMapper {

    private ResponseMapper() {}

    public static CategoryDto toDto(Category category) {
        return new CategoryDto(String.valueOf(category.id()), category.slug(), category.name());
    }

    public static ProductDto toDto(Product product) {
        return new ProductDto(
                String.valueOf(product.id()),
                product.slug(),
                product.name(),
                product.description(),
                MoneyDto.rub(product.price()),
                String.valueOf(product.categoryId()),
                product.imageUrl(),
                product.available());
    }

    public static PromoDto toDto(Promo promo) {
        return new PromoDto(
                String.valueOf(promo.id()), promo.title(), promo.text(), toDto(promo.product()));
    }

    public static UserDto toDto(User user) {
        return new UserDto(String.valueOf(user.id()), user.email());
    }

    public static OrderDto toDto(Order order) {
        var items = order.items().stream().map(ResponseMapper::toDto).toList();
        var createdAt =
                DateTimeFormatter.ISO_INSTANT.format(
                        order.createdAt().truncatedTo(ChronoUnit.SECONDS));
        return new OrderDto(
                String.valueOf(order.id()),
                order.status(),
                items,
                toDto(order.shipping()),
                MoneyDto.rub(order.total()),
                createdAt);
    }

    private static OrderItemDto toDto(OrderItem item) {
        return new OrderItemDto(
                String.valueOf(item.productId()),
                item.productName(),
                MoneyDto.rub(item.priceAtPurchase()),
                item.quantity());
    }

    private static ShippingDto toDto(Shipping shipping) {
        return new ShippingDto(
                shipping.method(), shipping.recipientName(), shipping.phone(), shipping.address());
    }
}
