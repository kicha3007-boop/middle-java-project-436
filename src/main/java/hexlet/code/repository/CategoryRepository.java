package hexlet.code.repository;

import hexlet.code.model.Category;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class CategoryRepository {

    private final DataSource dataSource;

    public CategoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Category> findAll() throws SQLException {
        var sql = "SELECT id, slug, name FROM categories ORDER BY id";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql);
                var rs = statement.executeQuery()) {
            var result = new ArrayList<Category>();
            while (rs.next()) {
                result.add(
                        new Category(rs.getLong("id"), rs.getString("slug"), rs.getString("name")));
            }
            return result;
        }
    }
}
