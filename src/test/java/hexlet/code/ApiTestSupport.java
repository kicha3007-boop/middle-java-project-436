package hexlet.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import hexlet.code.config.Database;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Общая обвязка тестов API: приложение поднимается целиком на свободном порту и на отдельной базе
 * (TEST_DATABASE_URL, иначе DATABASE_URL), тесты бьют по нему настоящим HTTP-клиентом.
 */
abstract class ApiTestSupport {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final String PROBLEM_BASE = "https://hexlet.io/problems/";

    private static HikariDataSource dataSource;
    private static Javalin app;
    private static String baseUrl;
    protected static Path staticDir;

    protected HttpClient client;

    /** Ответ: код, заголовки Content-Type и Set-Cookie, тело. */
    protected record Response(int status, String contentType, String setCookie, String body) {

        JsonNode json() throws IOException {
            return MAPPER.readTree(body);
        }
    }

    /** Приложение одно на весь прогон и останавливается вместе с JVM. */
    @BeforeAll
    static void startApp() throws Exception {
        if (app != null) {
            return;
        }
        var url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank()) {
            url = System.getenv("DATABASE_URL");
        }
        dataSource = Database.dataSource(url);
        Database.prepare(dataSource);

        staticDir = Files.createTempDirectory("store-static");
        Files.writeString(staticDir.resolve("index.html"), "<!doctype html><div id=\"root\">");
        Files.createDirectories(staticDir.resolve("assets"));
        Files.writeString(staticDir.resolve("assets/app.js"), "console.log('app');");

        app = App.create(dataSource, staticDir).start("127.0.0.1", 0);
        baseUrl = "http://127.0.0.1:" + app.port();
    }

    /** Перед каждым тестом база приводится в известное состояние: каталог есть, людей нет. */
    @BeforeEach
    void resetState() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "TRUNCATE order_items, orders, sessions, users RESTART IDENTITY CASCADE");
        }
        Database.prepare(dataSource);
        client = newClient();
    }

    protected static HttpClient newClient() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    protected Response get(String path) throws Exception {
        return get(client, path);
    }

    protected static Response get(HttpClient httpClient, String path) throws Exception {
        return send(httpClient, HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    protected Response post(String path, String json) throws Exception {
        return post(client, path, json);
    }

    protected static Response post(HttpClient httpClient, String path, String json)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        return send(httpClient, request);
    }

    protected Response delete(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build();
        return send(client, request);
    }

    private static Response send(HttpClient httpClient, HttpRequest request) throws Exception {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var contentType = response.headers().firstValue("Content-Type").orElse("");
        var setCookie = response.headers().firstValue("Set-Cookie").orElse("");
        return new Response(response.statusCode(), contentType, setCookie, response.body());
    }

    protected static String credentials(String email, String password) {
        return MAPPER.createObjectNode().put("email", email).put("password", password).toString();
    }

    /** Регистрирует пользователя этим клиентом: дальше клиент авторизован. */
    protected static JsonNode register(HttpClient httpClient, String email) throws Exception {
        var response = post(httpClient, "/api/users", credentials(email, "secret123"));
        assertThat(response.status()).isEqualTo(201);
        return response.json();
    }

    protected static void assertProblem(Response response, int status, String type)
            throws IOException {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/json");
        var body = response.json();
        assertThat(body.get("type").asText()).isEqualTo(PROBLEM_BASE + type);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isNotBlank();
    }
}
