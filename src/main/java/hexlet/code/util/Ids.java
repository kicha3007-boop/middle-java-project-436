package hexlet.code.util;

import java.util.Optional;

/** Идентификаторы приходят строками; внутри базы это bigserial. */
public final class Ids {

    private Ids() {}

    /** Строка, не похожая на идентификатор из базы, просто ничего не найдёт. */
    public static Optional<Long> parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,17}")) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }
}
