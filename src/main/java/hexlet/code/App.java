package hexlet.code;

import hexlet.code.config.Database;
import hexlet.code.config.Monitoring;
import hexlet.code.controller.AuthController;
import hexlet.code.controller.CatalogController;
import hexlet.code.controller.OrderController;
import hexlet.code.dto.HealthDto;
import hexlet.code.http.ApiException;
import hexlet.code.http.ErrorHandlers;
import hexlet.code.http.Json;
import hexlet.code.http.SessionAuth;
import hexlet.code.repository.CategoryRepository;
import hexlet.code.repository.OrderRepository;
import hexlet.code.repository.ProductRepository;
import hexlet.code.repository.PromoRepository;
import hexlet.code.repository.SessionRepository;
import hexlet.code.repository.UserRepository;
import hexlet.code.service.AuthService;
import hexlet.code.service.OrderService;
import hexlet.code.service.PasswordHasher;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;

public final class App {

    private App() {}

    /** Собирает приложение: маршруты API, раздачу фронтенда и обработку ошибок. */
    public static Javalin create(DataSource dataSource, Path staticDir) {
        var authService =
                new AuthService(
                        new UserRepository(dataSource),
                        new SessionRepository(dataSource),
                        new PasswordHasher());
        var sessionAuth = new SessionAuth(authService);
        var productRepository = new ProductRepository(dataSource);
        var orderService = new OrderService(productRepository, new OrderRepository(dataSource));

        var auth = new AuthController(authService, sessionAuth);
        var catalog =
                new CatalogController(
                        new CategoryRepository(dataSource),
                        productRepository,
                        new PromoRepository(dataSource));
        var orders = new OrderController(orderService);
        var errors = new ErrorHandlers(staticDir);

        return Javalin.create(
                config -> {
                    if (Files.isDirectory(staticDir)) {
                        config.staticFiles.add(
                                staticDir.toAbsolutePath().toString(), Location.EXTERNAL);
                    }

                    config.routes.get(
                            "/api/health", ctx -> Json.send(ctx, 200, new HealthDto("ok")));

                    config.routes.post("/api/users", auth::register);
                    config.routes.post("/api/session", auth::login);
                    config.routes.delete("/api/session", auth::logout);
                    config.routes.get("/api/me", auth::me);

                    config.routes.get("/api/categories", catalog::categories);
                    config.routes.get("/api/promos", catalog::promos);
                    config.routes.get("/api/products", catalog::products);
                    config.routes.get("/api/products/{id}", catalog::product);

                    // Проверка авторизации для всех маршрутов заказов — в одном месте.
                    config.routes.before("/api/orders", sessionAuth::requireUser);
                    config.routes.before("/api/orders/*", sessionAuth::requireUser);
                    config.routes.post("/api/orders", orders::create);
                    config.routes.get("/api/orders", orders::index);
                    config.routes.get("/api/orders/{id}", orders::show);

                    config.routes.exception(ApiException.class, errors::apiException);
                    config.routes.exception(
                            HttpResponseException.class, errors::frameworkException);
                    config.routes.exception(Exception.class, errors::unexpected);
                    config.routes.error(HttpStatus.NOT_FOUND, errors::notFound);
                });
    }

    public static void main(String[] args) throws Exception {
        Monitoring.init(System.getenv("MONITORING_DSN"));
        var dataSource = Database.dataSource(System.getenv("DATABASE_URL"));
        // Миграции и заливка каталога — до старта сервера, при каждом запуске.
        Database.prepare(dataSource);
        var staticDir = Path.of(System.getenv().getOrDefault("STATIC_DIR", "public"));
        create(dataSource, staticDir).start("0.0.0.0", port());
    }

    private static int port() {
        return Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    }
}
