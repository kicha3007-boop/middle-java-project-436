package hexlet.code.repository;

import hexlet.code.model.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public final class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Создаёт пользователя. Пустой результат — email уже занят: уникальный индекс защищает и от
     * гонки двух одновременных регистраций.
     */
    public Optional<User> create(String email, String passwordHash) throws SQLException {
        var sql =
                "INSERT INTO users (email, password_hash) VALUES (?, ?)"
                        + " ON CONFLICT (email) DO NOTHING RETURNING id";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, passwordHash);
            try (var rs = statement.executeQuery()) {
                return rs.next()
                        ? Optional.of(new User(rs.getLong("id"), email, passwordHash))
                        : Optional.empty();
            }
        }
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        return findOne("SELECT id, email, password_hash FROM users WHERE email = ?", email);
    }

    public Optional<User> findBySessionToken(String tokenHash) throws SQLException {
        return findOne(
                "SELECT u.id, u.email, u.password_hash FROM users u"
                        + " JOIN sessions s ON s.user_id = u.id WHERE s.token_hash = ?",
                tokenHash);
    }

    private Optional<User> findOne(String sql, String param) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, param);
            try (var rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"));
    }
}
