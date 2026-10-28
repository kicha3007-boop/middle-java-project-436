package hexlet.code.service;

import hexlet.code.http.ApiException;
import hexlet.code.http.ProblemType;
import hexlet.code.model.Order;
import hexlet.code.model.OrderItem;
import hexlet.code.model.Product;
import hexlet.code.repository.OrderRepository;
import hexlet.code.repository.ProductRepository;
import hexlet.code.request.CreateOrderRequest;
import hexlet.code.util.Ids;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Оформление заказа: сервер сам находит товары, берёт их цены и считает итог. */
public final class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;

    public OrderService(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    public Order create(long userId, CreateOrderRequest request) throws SQLException {
        var shipping = request.shipping();
        if ("delivery".equals(shipping.method())
                && (shipping.address() == null || shipping.address().isBlank())) {
            throw new ApiException(ProblemType.ADDRESS_REQUIRED, null);
        }

        // Один и тот же товар, присланный дважды, становится одной позицией с суммой количеств.
        var quantities = new LinkedHashMap<String, Integer>();
        for (var item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }

        var found = findProducts(quantities.keySet().stream().toList());
        var unavailable =
                quantities.keySet().stream()
                        .filter(id -> !found.containsKey(id) || !found.get(id).available())
                        .toList();
        if (!unavailable.isEmpty()) {
            throw new ApiException(
                    ProblemType.UNAVAILABLE_ITEMS,
                    "Unavailable products: " + String.join(", ", unavailable),
                    unavailable);
        }

        var items = new ArrayList<OrderItem>();
        var total = 0;
        for (var entry : quantities.entrySet()) {
            var product = found.get(entry.getKey());
            items.add(
                    new OrderItem(product.id(), product.name(), product.price(), entry.getValue()));
            total = Math.addExact(total, Math.multiplyExact(product.price(), entry.getValue()));
        }
        return orders.create(userId, Order.STATUS_PAID, items, shipping, total);
    }

    public List<Order> list(long userId) throws SQLException {
        return orders.findByUser(userId);
    }

    public Optional<Order> find(long userId, String orderId) throws SQLException {
        var id = Ids.parse(orderId);
        return id.isEmpty() ? Optional.empty() : orders.findByIdAndUser(id.get(), userId);
    }

    /** Товары по строковым идентификаторам из запроса; нечисловой идентификатор не найдётся. */
    private Map<String, Product> findProducts(List<String> ids) throws SQLException {
        var numericIds = ids.stream().map(Ids::parse).flatMap(Optional::stream).toList();
        if (numericIds.isEmpty()) {
            return Map.of();
        }
        return products.findByIds(numericIds).stream()
                .collect(
                        Collectors.toMap(
                                product -> String.valueOf(product.id()), Function.identity()));
    }
}
