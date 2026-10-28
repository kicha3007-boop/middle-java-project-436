package hexlet.code.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Хеширование паролей штатным PBKDF2WithHmacSHA256 из JDK. Строка хеша хранит алгоритм, число
 * итераций, соль и сам хеш: pbkdf2_sha256$итерации$соль$хеш.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        var salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        var hash = derive(password, salt, ITERATIONS);
        var encoder = Base64.getEncoder();
        return String.join(
                "$",
                PREFIX,
                String.valueOf(ITERATIONS),
                encoder.encodeToString(salt),
                encoder.encodeToString(hash));
    }

    public boolean matches(String password, String stored) {
        var parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        var decoder = Base64.getDecoder();
        var salt = decoder.decode(parts[2]);
        var expected = decoder.decode(parts[3]);
        var actual = derive(password, salt, Integer.parseInt(parts[1]));
        // Сравнение за постоянное время: не выдаёт, на каком байте хеши разошлись.
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
