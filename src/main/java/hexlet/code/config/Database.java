package hexlet.code.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Подключение к PostgreSQL, миграции схемы и заливка каталога. */
public final class Database {

    private static final String SEED_RESOURCE = "db/seed/catalog.sql";

    private Database() {}

    /** Строка подключения в форме JDBC, разобранная из postgres://user:password@host/db. */
    public record JdbcSettings(String url, String user, String password) {}

    public static JdbcSettings parse(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException("Переменная окружения DATABASE_URL не задана");
        }
        if (databaseUrl.startsWith("jdbc:")) {
            return new JdbcSettings(databaseUrl, null, null);
        }
        var uri = URI.create(databaseUrl.trim());
        var scheme = uri.getScheme();
        if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            throw new IllegalStateException("DATABASE_URL должен начинаться с postgres://");
        }
        String user = null;
        String password = null;
        var userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            var separator = userInfo.indexOf(':');
            if (separator >= 0) {
                user = decode(userInfo.substring(0, separator));
                password = decode(userInfo.substring(separator + 1));
            } else {
                user = decode(userInfo);
            }
        }
        var port = uri.getPort() == -1 ? 5432 : uri.getPort();
        var url = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath();
        if (uri.getRawQuery() != null) {
            url += "?" + uri.getRawQuery();
        }
        return new JdbcSettings(url, user, password);
    }

    public static HikariDataSource dataSource(String databaseUrl) {
        var settings = parse(databaseUrl);
        var config = new HikariConfig();
        config.setJdbcUrl(settings.url());
        if (settings.user() != null) {
            config.setUsername(settings.user());
        }
        if (settings.password() != null) {
            config.setPassword(settings.password());
        }
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    /** Применяет миграции и заливает каталог. Оба шага повторяемы. */
    public static void prepare(DataSource dataSource) throws IOException, SQLException {
        Flyway.configure().dataSource(dataSource).load().migrate();
        seed(dataSource);
    }

    private static void seed(DataSource dataSource) throws IOException, SQLException {
        var sql = readResource(SEED_RESOURCE);
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream stream = Database.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Не найден ресурс " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
