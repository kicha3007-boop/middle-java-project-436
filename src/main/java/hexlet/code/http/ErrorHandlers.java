package hexlet.code.http;

import hexlet.code.config.Monitoring;
import hexlet.code.dto.ProblemDto;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Единое место, где ошибки превращаются в ответы: Problem Details для API, index.html для SPA. */
public final class ErrorHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlers.class);

    private final Path indexFile;

    public ErrorHandlers(Path staticDir) {
        this.indexFile = staticDir.resolve("index.html");
    }

    public static boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    public static void sendProblem(Context ctx, ApiException e) {
        var type = e.type();
        var body =
                new ProblemDto(
                        type.uri(),
                        type.title(),
                        type.status(),
                        e.detail(),
                        e.unavailableProductIds());
        Json.send(ctx, type.status(), body);
    }

    public void apiException(ApiException e, Context ctx) {
        sendProblem(ctx, e);
    }

    /** Исключения самого фреймворка тоже уходят в формате контракта. */
    public void frameworkException(HttpResponseException e, Context ctx) {
        var type =
                switch (e.getStatus()) {
                    case 401 -> ProblemType.UNAUTHORIZED;
                    case 404 -> ProblemType.NOT_FOUND;
                    case 500 -> ProblemType.INTERNAL_ERROR;
                    default -> ProblemType.VALIDATION_ERROR;
                };
        sendProblem(ctx, new ApiException(type, e.getMessage()));
    }

    /** Непредвиденная ошибка: 500 и отправка в сборщик ошибок. */
    public void unexpected(Exception e, Context ctx) {
        LOG.error("Unhandled error on {} {}", ctx.method(), ctx.path(), e);
        Monitoring.capture(e, ctx.method().name(), ctx.path());
        sendProblem(ctx, new ApiException(ProblemType.INTERNAL_ERROR, null));
    }

    /**
     * Срабатывает на любой ответ 404. Внутри /api/ — JSON-ошибка (если обработчик уже не записал
     * свою), снаружи — SPA-fallback: index.html со статусом 200.
     */
    public void notFound(Context ctx) throws IOException {
        if (isApiPath(ctx.path())) {
            if (ctx.resultInputStream() == null) {
                sendProblem(ctx, ApiException.notFound("No route for " + ctx.path()));
            }
            return;
        }
        if (Files.isRegularFile(indexFile)) {
            ctx.status(200)
                    .contentType("text/html; charset=utf-8")
                    .result(Files.readAllBytes(indexFile));
        }
    }
}
