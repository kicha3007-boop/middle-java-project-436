package hexlet.code.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import java.io.UncheckedIOException;

/** Чтение тел запросов и запись JSON-ответов. */
public final class Json {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private Json() {}

    public static void send(Context ctx, int status, Object body) {
        try {
            ctx.status(status)
                    .contentType("application/json; charset=utf-8")
                    .result(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Тело запроса как JSON-объект; всё, что объектом не является, — ошибка валидации. */
    public static JsonNode readObject(Context ctx) {
        JsonNode node;
        try {
            node = MAPPER.readTree(ctx.body());
        } catch (JsonProcessingException e) {
            throw ApiException.validation("Request body is not valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw ApiException.validation("Request body must be a JSON object");
        }
        return node;
    }
}
