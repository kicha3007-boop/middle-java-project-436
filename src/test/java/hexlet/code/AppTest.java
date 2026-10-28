package hexlet.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Контракт запуска: health, JSON-404 внутри /api/, раздача статики и SPA-fallback. */
class AppTest extends ApiTestSupport {

    @Test
    void healthReturnsOk() throws Exception {
        var response = get("/api/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("status").asText()).isEqualTo("ok");
    }

    @Test
    void unknownApiPathReturnsJsonNotFound() throws Exception {
        assertProblem(get("/api/nothing-here"), 404, "not-found");
    }

    @Test
    void pageAddressesReturnIndexHtml() throws Exception {
        for (var path : new String[] {"/", "/cart", "/account", "/products/1"}) {
            var response = get(path);

            assertThat(response.status()).as(path).isEqualTo(200);
            assertThat(response.contentType()).as(path).startsWith("text/html");
            assertThat(response.body()).as(path).contains("id=\"root\"");
        }
    }

    @Test
    void staticFileIsServedWithItsType() throws Exception {
        var response = get("/assets/app.js");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).contains("javascript");
        assertThat(response.body()).contains("console.log");
    }

    @Test
    void pathTraversalDoesNotLeakFiles() throws Exception {
        var response = get("/..%2f..%2fetc%2fpasswd");

        assertThat(response.body()).doesNotContain("root:x:");
    }
}
