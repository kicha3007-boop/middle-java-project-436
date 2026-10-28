package hexlet.code.controller;

import hexlet.code.dto.ResponseMapper;
import hexlet.code.http.ApiException;
import hexlet.code.http.Json;
import hexlet.code.http.SessionAuth;
import hexlet.code.request.Credentials;
import hexlet.code.service.AuthService;
import io.javalin.http.Context;
import java.sql.SQLException;

public final class AuthController {

    private final AuthService auth;
    private final SessionAuth session;

    public AuthController(AuthService auth, SessionAuth session) {
        this.auth = auth;
        this.session = session;
    }

    public void register(Context ctx) throws SQLException {
        var user = auth.register(Credentials.from(Json.readObject(ctx)));
        session.signIn(ctx, user);
        Json.send(ctx, 201, ResponseMapper.toDto(user));
    }

    public void login(Context ctx) throws SQLException {
        var user = auth.login(Credentials.from(Json.readObject(ctx)));
        session.signIn(ctx, user);
        Json.send(ctx, 200, ResponseMapper.toDto(user));
    }

    public void logout(Context ctx) throws SQLException {
        session.signOut(ctx);
        ctx.status(204);
    }

    public void me(Context ctx) throws SQLException {
        var user = session.currentUser(ctx).orElseThrow(ApiException::unauthorized);
        Json.send(ctx, 200, ResponseMapper.toDto(user));
    }
}
