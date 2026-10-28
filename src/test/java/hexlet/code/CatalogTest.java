package hexlet.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Категории, промо-блоки и каталог с фильтрами и пагинацией. */
class CatalogTest extends ApiTestSupport {

    private static List<JsonNode> items(JsonNode page) {
        var result = new ArrayList<JsonNode>();
        page.get("items").forEach(result::add);
        return result;
    }

    private static void assertProductShape(JsonNode product) {
        assertThat(product.get("id").isTextual()).isTrue();
        assertThat(product.get("slug").asText()).isNotBlank();
        assertThat(product.get("name").asText()).isNotBlank();
        assertThat(product.get("description").isTextual()).isTrue();
        assertThat(product.get("price").get("amount").isInt()).isTrue();
        assertThat(product.get("price").get("currency").asText()).isEqualTo("RUB");
        assertThat(product.get("categoryId").isTextual()).isTrue();
        assertThat(product.get("available").isBoolean()).isTrue();
    }

    @Test
    void categoriesAreListed() throws Exception {
        var response = get("/api/categories");

        assertThat(response.status()).isEqualTo(200);
        var categories = response.json();
        assertThat(categories.size()).isGreaterThanOrEqualTo(3);
        for (var category : categories) {
            assertThat(category.get("id").isTextual()).isTrue();
            assertThat(category.get("slug").asText()).doesNotContain(" ");
            assertThat(category.get("name").asText()).isNotBlank();
        }
    }

    @Test
    void promosContainNestedAvailableProducts() throws Exception {
        var response = get("/api/promos");

        assertThat(response.status()).isEqualTo(200);
        var promos = response.json();
        assertThat(promos.size()).isGreaterThanOrEqualTo(2);
        var productIds = new ArrayList<String>();
        for (var promo : promos) {
            assertThat(promo.get("id").isTextual()).isTrue();
            assertThat(promo.get("title").asText()).isNotBlank();
            assertThat(promo.get("text").asText()).isNotBlank();
            var product = promo.get("product");
            assertProductShape(product);
            assertThat(product.get("available").asBoolean()).isTrue();
            productIds.add(product.get("id").asText());
        }
        assertThat(productIds).doesNotHaveDuplicates();
    }

    @Test
    void productsFirstPageWithDefaults() throws Exception {
        var response = get("/api/products");

        assertThat(response.status()).isEqualTo(200);
        var page = response.json();
        var meta = page.get("meta");
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("perPage").asInt()).isEqualTo(6);
        assertThat(meta.get("total").asInt()).isGreaterThanOrEqualTo(12);
        assertThat(meta.get("totalPages").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(items(page)).hasSize(6).allSatisfy(CatalogTest::assertProductShape);
    }

    @Test
    void catalogDataContract() throws Exception {
        var all = items(get("/api/products?perPage=48").json());

        assertThat(all).anyMatch(product -> !product.get("available").asBoolean());
        assertThat(all).anyMatch(product -> !product.has("imageUrl"));
        assertThat(all).anyMatch(product -> product.get("price").get("amount").asInt() < 20000);
        assertThat(all).anyMatch(product -> product.get("price").get("amount").asInt() > 100000);
    }

    @Test
    void secondPageDoesNotOverlapFirst() throws Exception {
        var first = items(get("/api/products?page=1").json());
        var second = get("/api/products?page=2").json();

        assertThat(second.get("meta").get("page").asInt()).isEqualTo(2);
        var firstIds = first.stream().map(product -> product.get("id").asText()).toList();
        assertThat(items(second))
                .isNotEmpty()
                .noneMatch(product -> firstIds.contains(product.get("id").asText()));
    }

    @Test
    void pageBeyondLastIsEmptyWithRealTotal() throws Exception {
        var page = get("/api/products?page=100").json();

        assertThat(items(page)).isEmpty();
        assertThat(page.get("meta").get("total").asInt()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void filterByCategory() throws Exception {
        var category = get("/api/categories").json().get(0);
        var page = get("/api/products?perPage=48&category=" + category.get("slug").asText()).json();

        var found = items(page);
        assertThat(found).hasSizeGreaterThanOrEqualTo(2);
        assertThat(found)
                .allMatch(
                        product ->
                                product.get("categoryId")
                                        .asText()
                                        .equals(category.get("id").asText()));
        assertThat(page.get("meta").get("total").asInt()).isEqualTo(found.size());
    }

    @Test
    void emptyCategoryParamMeansAllCategories() throws Exception {
        var all = get("/api/products").json().get("meta").get("total").asInt();
        var withEmpty = get("/api/products?category=").json().get("meta").get("total").asInt();

        assertThat(withEmpty).isEqualTo(all);
    }

    @Test
    void searchIsCaseInsensitiveSubstring() throws Exception {
        var found = items(get("/api/products?perPage=48&q=rYzEn").json());

        assertThat(found)
                .isNotEmpty()
                .allMatch(product -> product.get("name").asText().toLowerCase().contains("ryzen"));
    }

    @Test
    void filterByPriceRangeIsInclusive() throws Exception {
        var found = items(get("/api/products?perPage=48&priceMin=17990&priceMax=39990").json());

        assertThat(found)
                .isNotEmpty()
                .allMatch(
                        product -> {
                            var amount = product.get("price").get("amount").asInt();
                            return amount >= 17990 && amount <= 39990;
                        })
                .anyMatch(product -> product.get("price").get("amount").asInt() == 17990)
                .anyMatch(product -> product.get("price").get("amount").asInt() == 39990);
    }

    @Test
    void filterOnlyAvailable() throws Exception {
        var all = get("/api/products").json().get("meta").get("total").asInt();
        var page = get("/api/products?perPage=48&available=true").json();

        assertThat(page.get("meta").get("total").asInt()).isLessThan(all);
        assertThat(items(page)).allMatch(product -> product.get("available").asBoolean());
    }

    @Test
    void filterOnlyUnavailable() throws Exception {
        var found = items(get("/api/products?available=false").json());

        assertThat(found).isNotEmpty().allMatch(product -> !product.get("available").asBoolean());
    }

    @Test
    void filtersCombine() throws Exception {
        var page =
                get("/api/products?perPage=48&category=videokarty&priceMax=100000&available=true")
                        .json();

        var categoryId = get("/api/categories").json().get(0).get("id").asText();
        assertThat(items(page))
                .isNotEmpty()
                .allMatch(product -> product.get("categoryId").asText().equals(categoryId))
                .allMatch(product -> product.get("price").get("amount").asInt() <= 100000)
                .allMatch(product -> product.get("available").asBoolean());
    }

    @Test
    void noMatchesGiveEmptyPage() throws Exception {
        var response = get("/api/products?q=no-such-product-at-all");

        assertThat(response.status()).isEqualTo(200);
        var page = response.json();
        assertThat(items(page)).isEmpty();
        assertThat(page.get("meta").get("total").asInt()).isZero();
        assertThat(page.get("meta").get("totalPages").asInt()).isZero();
    }

    @Test
    void wrongParameterTypesAreRejected() throws Exception {
        assertProblem(get("/api/products?priceMin=abc"), 400, "validation-error");
        assertProblem(get("/api/products?priceMax=1.5"), 400, "validation-error");
        assertProblem(get("/api/products?available=yes"), 400, "validation-error");
        assertProblem(get("/api/products?page=0"), 400, "validation-error");
        assertProblem(get("/api/products?perPage=49"), 400, "validation-error");
    }

    @Test
    void productById() throws Exception {
        var listed = items(get("/api/products").json()).getFirst();

        var response = get("/api/products/" + listed.get("id").asText());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).isEqualTo(listed);
    }

    @Test
    void productWithoutImageHasNoImageUrl() throws Exception {
        var withoutImage =
                items(get("/api/products?perPage=48").json()).stream()
                        .filter(product -> !product.has("imageUrl"))
                        .findFirst()
                        .orElseThrow();

        var product = get("/api/products/" + withoutImage.get("id").asText()).json();

        assertThat(product.has("imageUrl")).isFalse();
    }

    @Test
    void unknownProductReturnsNotFound() throws Exception {
        assertProblem(get("/api/products/999999"), 404, "not-found");
        assertProblem(get("/api/products/not-an-id"), 404, "not-found");
    }
}
