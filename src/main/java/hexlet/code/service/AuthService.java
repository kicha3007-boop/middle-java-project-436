package hexlet.code.service;

import hexlet.code.http.ApiException;
import hexlet.code.http.ProblemType;
import hexlet.code.model.User;
import hexlet.code.repository.SessionRepository;
import hexlet.code.repository.UserRepository;
import hexlet.code.request.Credentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Регистрация, вход и серверные сессии. */
public final class AuthService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, SessionRepository sessions, PasswordHasher hasher) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
    }

    public User register(Credentials credentials) throws SQLException {
        return users.create(credentials.email(), hasher.hash(credentials.password()))
                .orElseThrow(() -> new ApiException(ProblemType.EMAIL_TAKEN, null));
    }

    /** Неизвестный email и неверный пароль дают одну и ту же ошибку. */
    public User login(Credentials credentials) throws SQLException {
        return users.findByEmail(credentials.email())
                .filter(user -> hasher.matches(credentials.password(), user.passwordHash()))
                .orElseThrow(() -> new ApiException(ProblemType.INVALID_CREDENTIALS, null));
    }

    /** Открывает сессию и возвращает токен для cookie. В базу попадает только его хеш. */
    public String openSession(User user) throws SQLException {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.create(hashToken(token), user.id());
        return token;
    }

    public void closeSession(String token) throws SQLException {
        if (token != null && !token.isBlank()) {
            sessions.delete(hashToken(token));
        }
    }

    public Optional<User> findUser(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return users.findBySessionToken(hashToken(token));
    }

    private static String hashToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
