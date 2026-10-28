package hexlet.code.request;

import hexlet.code.http.ApiException;
import io.javalin.http.Context;

/**
 * Параметры каталога. Пустое значение параметра — не фильтр. Параметр не того типа — ошибка
 * валидации.
 */
public record ProductFilter(
        String category,
        String query,
        Integer priceMin,
        Integer priceMax,
        Boolean available,
        int page,
        int perPage) {

    public static final int DEFAULT_PER_PAGE = 6;
    public static final int MAX_PER_PAGE = 48;

    public static ProductFilter from(Context ctx) {
        var page = intParam(ctx, "page");
        var perPage = intParam(ctx, "perPage");
        if (page != null && page < 1) {
            throw ApiException.validation("/query/page: must be >= 1");
        }
        if (perPage != null && (perPage < 1 || perPage > MAX_PER_PAGE)) {
            throw ApiException.validation("/query/perPage: must be between 1 and " + MAX_PER_PAGE);
        }
        return new ProductFilter(
                textParam(ctx, "category"),
                textParam(ctx, "q"),
                intParam(ctx, "priceMin"),
                intParam(ctx, "priceMax"),
                boolParam(ctx, "available"),
                page == null ? 1 : page,
                perPage == null ? DEFAULT_PER_PAGE : perPage);
    }

    public int offset() {
        return (page - 1) * perPage;
    }

    private static String textParam(Context ctx, String name) {
        var value = ctx.queryParam(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer intParam(Context ctx, String name) {
        var value = textParam(ctx, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw ApiException.validation("/query/" + name + ": must be integer");
        }
    }

    private static Boolean boolParam(Context ctx, String name) {
        var value = textParam(ctx, name);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw ApiException.validation("/query/" + name + ": must be boolean");
        };
    }
}
