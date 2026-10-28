package hexlet.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Регистрация, вход, выход и текущий пользователь. */
class AuthTest extends ApiTestSupport {

    @Test
    void registerCreatesUserAndOpensSession() throws Exception {
        var response = post("/api/users", credentials("ivan@example.com", "secret1"));

        assertThat(response.status()).isEqualTo(201);
        var user = response.json();
        assertThat(user.get("id").isTextual()).isTrue();
        assertThat(user.get("email").asText()).isEqualTo("ivan@example.com");
        assertThat(user.has("password")).isFalse();
        assertThat(user.has("passwordHash")).isFalse();

        var me = get("/api/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("id").asText()).isEqualTo(user.get("id").asText());
    }

    @Test
    void sessionCookieIsHttpOnly() throws Exception {
        var response = post("/api/users", credentials("cookie@example.com", "secret1"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.setCookie()).startsWith("session=").contains("HttpOnly");
    }

    @Test
    void registerNormalizesEmail() throws Exception {
        var response = post("/api/users", credentials("  Ivan@Example.COM ", "secret1"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("email").asText()).isEqualTo("ivan@example.com");
    }

    @Test
    void registerWithTakenEmailReturnsConflict() throws Exception {
        register(client, "ivan@example.com");

        var response =
                post(newClient(), "/api/users", credentials("IVAN@example.com  ", "secret1"));

        assertProblem(response, 409, "email-taken");
    }

    @Test
    void registerWithInvalidEmailReturnsValidationError() throws Exception {
        assertProblem(
                post("/api/users", credentials("not-an-email", "secret1")),
                400,
                "validation-error");
    }

    @Test
    void registerWithShortPasswordReturnsValidationError() throws Exception {
        assertProblem(
                post("/api/users", credentials("ivan@example.com", "12345")),
                400,
                "validation-error");
    }

    @Test
    void registerWithMissingFieldsReturnsValidationError() throws Exception {
        assertProblem(
                post("/api/users", "{\"email\":\"ivan@example.com\"}"), 400, "validation-error");
    }

    @Test
    void malformedJsonReturnsValidationError() throws Exception {
        assertProblem(post("/api/users", "{not json"), 400, "validation-error");
    }

    @Test
    void loginWithValidCredentials() throws Exception {
        register(newClient(), "ivan@example.com");

        var response = post("/api/session", credentials(" Ivan@example.com", "secret123"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("email").asText()).isEqualTo("ivan@example.com");
        assertThat(get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameAnswer() throws Exception {
        register(newClient(), "ivan@example.com");

        var wrongPassword = post("/api/session", credentials("ivan@example.com", "wrong-pass"));
        var unknownEmail = post("/api/session", credentials("nobody@example.com", "secret123"));

        assertProblem(wrongPassword, 401, "invalid-credentials");
        assertProblem(unknownEmail, 401, "invalid-credentials");
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        assertThat(get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void meWithoutSessionReturnsUnauthorized() throws Exception {
        assertProblem(get("/api/me"), 401, "unauthorized");
    }

    @Test
    void logoutEndsSessionOnServer() throws Exception {
        register(client, "ivan@example.com");

        var response = delete("/api/session");

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertProblem(get("/api/me"), 401, "unauthorized");
    }
}
