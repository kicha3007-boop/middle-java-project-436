package hexlet.code.repository;

import hexlet.code.model.Order;
import hexlet.code.model.OrderItem;
import hexlet.code.model.Shipping;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

public final class OrderRepository {

    private static final String ORDER_COLUMNS =
            "id, user_id, status, shipping_method, recipient_name, phone, address, total,"
                    + " created_at";

    private final DataSource dataSource;

    public OrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Сохраняет заказ и его позиции в одной транзакции. */
    public Order create(
            long userId, String status, List<OrderItem> items, Shipping shipping, int total)
            throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var order = insertOrder(connection, userId, status, items, shipping, total);
                insertItems(connection, order.id(), items);
                connection.commit();
                return order;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Заказы пользователя, новые первыми. Позиции всех заказов — одним запросом. */
    public List<Order> findByUser(long userId) throws SQLException {
        var sql =
                "SELECT "
                        + ORDER_COLUMNS
                        + " FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            var rows = new ArrayList<OrderRow>();
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
            var items = findItems(connection, rows.stream().map(OrderRow::id).toList());
            return rows.stream()
                    .map(row -> row.toOrder(items.getOrDefault(row.id(), List.of())))
                    .toList();
        }
    }

    /** Заказ ищется по идентификатору и владельцу сразу: чужой заказ не находится вовсе. */
    public Optional<Order> findByIdAndUser(long id, long userId) throws SQLException {
        var sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ? AND user_id = ?";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, userId);
            OrderRow row;
            try (var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                row = mapRow(rs);
            }
            var items = findItems(connection, List.of(row.id()));
            return Optional.of(row.toOrder(items.getOrDefault(row.id(), List.of())));
        }
    }

    private static Order insertOrder(
            Connection connection,
            long userId,
            String status,
            List<OrderItem> items,
            Shipping shipping,
            int total)
            throws SQLException {
        var sql =
                "INSERT INTO orders (user_id, status, shipping_method, recipient_name, phone,"
                        + " address, total) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, status);
            statement.setString(3, shipping.method());
            statement.setString(4, shipping.recipientName());
            statement.setString(5, shipping.phone());
            statement.setString(6, shipping.address());
            statement.setInt(7, total);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return new Order(
                        rs.getLong("id"),
                        userId,
                        status,
                        items,
                        shipping,
                        total,
                        readInstant(rs, "created_at"));
            }
        }
    }

    private static void insertItems(Connection connection, long orderId, List<OrderItem> items)
            throws SQLException {
        var sql =
                "INSERT INTO order_items (order_id, product_id, product_name, price_at_purchase,"
                        + " quantity) VALUES (?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            for (var item : items) {
                statement.setLong(1, orderId);
                statement.setLong(2, item.productId());
                statement.setString(3, item.productName());
                statement.setInt(4, item.priceAtPurchase());
                statement.setInt(5, item.quantity());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Map<Long, List<OrderItem>> findItems(Connection connection, List<Long> orderIds)
            throws SQLException {
        var result = new HashMap<Long, List<OrderItem>>();
        if (orderIds.isEmpty()) {
            return result;
        }
        var sql =
                "SELECT order_id, product_id, product_name, price_at_purchase, quantity"
                        + " FROM order_items WHERE order_id = ANY(?) ORDER BY id";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("bigint", orderIds.toArray()));
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    var item =
                            new OrderItem(
                                    rs.getLong("product_id"),
                                    rs.getString("product_name"),
                                    rs.getInt("price_at_purchase"),
                                    rs.getInt("quantity"));
                    result.computeIfAbsent(rs.getLong("order_id"), key -> new ArrayList<>())
                            .add(item);
                }
            }
        }
        return result;
    }

    private static OrderRow mapRow(ResultSet rs) throws SQLException {
        return new OrderRow(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("status"),
                new Shipping(
                        rs.getString("shipping_method"),
                        rs.getString("recipient_name"),
                        rs.getString("phone"),
                        rs.getString("address")),
                rs.getInt("total"),
                readInstant(rs, "created_at"));
    }

    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record OrderRow(
            long id, long userId, String status, Shipping shipping, int total, Instant createdAt) {

        Order toOrder(List<OrderItem> items) {
            return new Order(id, userId, status, items, shipping, total, createdAt);
        }
    }
}
