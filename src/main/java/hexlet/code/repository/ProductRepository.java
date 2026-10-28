package hexlet.code.repository;

import hexlet.code.model.Product;
import hexlet.code.request.ProductFilter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class ProductRepository {

    /** Колонки товара; используются и в каталоге, и в промо-блоках. */
    static final String COLUMNS =
            "p.id, p.slug, p.name, p.description, p.price, p.category_id, p.image_url,"
                    + " p.available";

    private final DataSource dataSource;

    public ProductRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Страница каталога и общее число подходящих под фильтр товаров. */
    public record Page(List<Product> items, int total) {}

    /** Каталог с фильтрами: условия собираются динамически, значения — только параметрами. */
    public Page findPage(ProductFilter filter) throws SQLException {
        var conditions = new ArrayList<String>();
        var params = new ArrayList<Object>();
        if (filter.category() != null) {
            conditions.add("c.slug = ?");
            params.add(filter.category());
        }
        if (filter.query() != null) {
            conditions.add("p.name ILIKE ?");
            params.add("%" + escapeLike(filter.query()) + "%");
        }
        if (filter.priceMin() != null) {
            conditions.add("p.price >= ?");
            params.add(filter.priceMin());
        }
        if (filter.priceMax() != null) {
            conditions.add("p.price <= ?");
            params.add(filter.priceMax());
        }
        if (filter.available() != null) {
            conditions.add("p.available = ?");
            params.add(filter.available());
        }
        var where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        var from = " FROM products p JOIN categories c ON c.id = p.category_id" + where;

        try (var connection = dataSource.getConnection()) {
            int total;
            try (var statement = connection.prepareStatement("SELECT COUNT(*)" + from)) {
                bind(statement, params);
                try (var rs = statement.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            // Сортировка по устойчивому полю: соседние страницы не пересекаются.
            var sql = "SELECT " + COLUMNS + from + " ORDER BY p.id LIMIT ? OFFSET ?";
            try (var statement = connection.prepareStatement(sql)) {
                var pageParams = new ArrayList<>(params);
                pageParams.add(filter.perPage());
                pageParams.add(filter.offset());
                bind(statement, pageParams);
                try (var rs = statement.executeQuery()) {
                    var items = new ArrayList<Product>();
                    while (rs.next()) {
                        items.add(map(rs));
                    }
                    return new Page(items, total);
                }
            }
        }
    }

    public Optional<Product> findById(long id) throws SQLException {
        var sql = "SELECT " + COLUMNS + " FROM products p WHERE p.id = ?";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Товары по списку идентификаторов — одним запросом, а не по одному в цикле. */
    public List<Product> findByIds(Collection<Long> ids) throws SQLException {
        var sql = "SELECT " + COLUMNS + " FROM products p WHERE p.id = ANY(?)";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("bigint", ids.toArray()));
            try (var rs = statement.executeQuery()) {
                var result = new ArrayList<Product>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        }
    }

    static Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("price"),
                rs.getLong("category_id"),
                rs.getString("image_url"),
                rs.getBoolean("available"));
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (var i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    /** Символы % и _ из поискового запроса ищутся буквально. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
