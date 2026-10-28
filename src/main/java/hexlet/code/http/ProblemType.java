package hexlet.code.http;

/** Закрытый словарь типов ошибок из контракта API. */
public enum ProblemType {
    INVALID_CREDENTIALS("invalid-credentials", 401, "Invalid email or password"),
    EMAIL_TAKEN("email-taken", 409, "Email already taken"),
    ADDRESS_REQUIRED("address-required", 400, "Address is required for delivery"),
    UNAVAILABLE_ITEMS("unavailable-items", 400, "Some products are unavailable"),
    VALIDATION_ERROR("validation-error", 400, "Invalid request data"),
    UNAUTHORIZED("unauthorized", 401, "Authentication required"),
    NOT_FOUND("not-found", 404, "Resource not found"),
    INTERNAL_ERROR("internal-error", 500, "Internal server error");

    private static final String BASE_URI = "https://hexlet.io/problems/";

    private final String uri;
    private final int status;
    private final String title;

    ProblemType(String slug, int status, String title) {
        this.uri = BASE_URI + slug;
        this.status = status;
        this.title = title;
    }

    public String uri() {
        return uri;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}
