package hexlet.code.repository;

import java.sql.SQLException;
import javax.sql.DataSource;

/** Серверные сессии. В базе хранится только хеш токена из cookie. */
public final class SessionRepository {

    private final DataSource dataSource;

    public SessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void create(String tokenHash, long userId) throws SQLException {
        var sql = "INSERT INTO sessions (token_hash, user_id) VALUES (?, ?)";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public void delete(String tokenHash) throws SQLException {
        var sql = "DELETE FROM sessions WHERE token_hash = ?";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }
}
