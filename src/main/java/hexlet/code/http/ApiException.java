package hexlet.code.http;

import java.util.List;

/** Ожидаемая ошибка API: превращается в ответ Problem Details в одном месте — ErrorHandlers. */
public final class ApiException extends RuntimeException {

    private final ProblemType type;
    private final transient List<String> unavailableProductIds;

    public ApiException(ProblemType type, String detail) {
        this(type, detail, null);
    }

    public ApiException(ProblemType type, String detail, List<String> unavailableProductIds) {
        super(detail == null ? type.title() : detail);
        this.type = type;
        this.unavailableProductIds = unavailableProductIds;
    }

    public static ApiException validation(String detail) {
        return new ApiException(ProblemType.VALIDATION_ERROR, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(ProblemType.NOT_FOUND, detail);
    }

    public static ApiException unauthorized() {
        return new ApiException(ProblemType.UNAUTHORIZED, null);
    }

    public ProblemType type() {
        return type;
    }

    public String detail() {
        return getMessage().equals(type.title()) ? null : getMessage();
    }

    public List<String> unavailableProductIds() {
        return unavailableProductIds;
    }
}
