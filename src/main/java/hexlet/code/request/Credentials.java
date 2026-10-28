package hexlet.code.request;

import com.fasterxml.jackson.databind.JsonNode;
import hexlet.code.http.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Тело регистрации и входа. Email нормализуется: без внешних пробелов и в нижнем регистре. */
public record Credentials(String email, String password) {

    public static final int MIN_PASSWORD_LENGTH = 6;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static Credentials from(JsonNode body) {
        var email = body.get("email");
        var password = body.get("password");
        if (email == null || !email.isTextual()) {
            throw ApiException.validation("/body/email: must be a string");
        }
        if (password == null || !password.isTextual()) {
            throw ApiException.validation("/body/password: must be a string");
        }
        var normalized = normalizeEmail(email.asText());
        if (!EMAIL.matcher(normalized).matches()) {
            throw ApiException.validation("/body/email: must be a valid email");
        }
        if (password.asText().length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.validation(
                    "/body/password: must NOT have fewer than "
                            + MIN_PASSWORD_LENGTH
                            + " characters");
        }
        return new Credentials(normalized, password.asText());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
