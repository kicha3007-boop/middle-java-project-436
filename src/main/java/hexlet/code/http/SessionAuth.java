package hexlet.code.http;

import hexlet.code.model.User;
import hexlet.code.service.AuthService;
import io.javalin.http.Context;
import java.sql.SQLException;
import java.util.Optional;

/** Сессионная cookie и общая проверка авторизации для закрытых маршрутов. */
public final class SessionAuth {

    public static final String COOKIE = "session";
    private static final String USER_ATTRIBUTE = "currentUser";

    private final AuthService auth;

    public SessionAuth(AuthService auth) {
        this.auth = auth;
    }

    public void signIn(Context ctx, User user) throws SQLException {
        var token = auth.openSession(user);
        ctx.res()
                .addHeader("Set-Cookie", COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax");
    }

    /** Сессия завершается на сервере, а cookie в браузере стирается. */
    public void signOut(Context ctx) throws SQLException {
        auth.closeSession(ctx.cookie(COOKIE));
        ctx.res().addHeader("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    public Optional<User> currentUser(Context ctx) throws SQLException {
        return auth.findUser(ctx.cookie(COOKIE));
    }

    /** Обработчик before для закрытых маршрутов: без сессии — 401. */
    public void requireUser(Context ctx) throws SQLException {
        var user = currentUser(ctx).orElseThrow(ApiException::unauthorized);
        ctx.attribute(USER_ATTRIBUTE, user);
    }

    /** Пользователь, которого положил requireUser. */
    public static User user(Context ctx) {
        User user = ctx.attribute(USER_ATTRIBUTE);
        if (user == null) {
            throw ApiException.unauthorized();
        }
        return user;
    }
}
