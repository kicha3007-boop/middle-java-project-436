package hexlet.code.repository;

import hexlet.code.model.Promo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class PromoRepository {

    private final DataSource dataSource;

    public PromoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Промо-блоки вместе с товарами — одним запросом с соединением таблиц. */
    public List<Promo> findAll() throws SQLException {
        var sql =
                "SELECT pr.id AS promo_id, pr.title, pr.text, "
                        + ProductRepository.COLUMNS
                        + " FROM promos pr JOIN products p ON p.id = pr.product_id"
                        + " ORDER BY pr.id";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql);
                var rs = statement.executeQuery()) {
            var result = new ArrayList<Promo>();
            while (rs.next()) {
                result.add(
                        new Promo(
                                rs.getLong("promo_id"),
                                rs.getString("title"),
                                rs.getString("text"),
                                ProductRepository.map(rs)));
            }
            return result;
        }
    }
}
