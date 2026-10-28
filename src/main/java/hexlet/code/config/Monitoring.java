package hexlet.code.config;

import io.sentry.Sentry;

/**
 * Сборщик ошибок по протоколу Sentry (подойдёт и Bugsink). Адрес приходит из MONITORING_DSN; без
 * переменной мониторинг выключен, а приложение работает как обычно.
 */
public final class Monitoring {

    private static volatile boolean enabled;

    private Monitoring() {}

    public static void init(String dsn) {
        if (dsn == null || dsn.isBlank()) {
            return;
        }
        Sentry.init(
                options -> {
                    options.setDsn(dsn);
                    // Собираем ошибки, а не производительность.
                    options.setTracesSampleRate(0.0);
                    // Ни cookie, ни заголовков, ни IP пользователя в событиях не будет.
                    options.setSendDefaultPii(false);
                    options.setBeforeSend(
                            (event, hint) -> {
                                event.setRequest(null);
                                return event;
                            });
                });
        enabled = true;
    }

    public static void capture(Throwable failure, String method, String path) {
        if (!enabled) {
            return;
        }
        Sentry.withScope(
                scope -> {
                    scope.setTag("http.method", method);
                    scope.setTag("http.path", path);
                    Sentry.captureException(failure);
                });
    }
}
