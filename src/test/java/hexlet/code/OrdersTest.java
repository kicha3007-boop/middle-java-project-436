package hexlet.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Оформление заказа и личный кабинет. */
class OrdersTest extends ApiTestSupport {

    private JsonNode available;
    private JsonNode another;
    private JsonNode unavailable;

    @BeforeEach
    void pickProducts() throws Exception {
        var availableItems = new ArrayList<JsonNode>();
        get("/api/products?available=true").json().get("items").forEach(availableItems::add);
        available = availableItems.get(0);
        another = availableItems.get(1);
        unavailable = get("/api/products?available=false").json().get("items").get(0);
    }

    private static ObjectNode item(JsonNode product, int quantity) {
        return MAPPER.createObjectNode()
                .put("productId", product.get("id").asText())
                .put("quantity", quantity);
    }

    private static ObjectNode shipping(String method, String address) {
        var shipping =
                MAPPER.createObjectNode()
                        .put("method", method)
                        .put("recipientName", "Иван Петров")
                        .put("phone", "+79990001122");
        if (address != null) {
            shipping.put("address", address);
        }
        return shipping;
    }

    private static String order(List<ObjectNode> items, ObjectNode shipping) {
        var body = MAPPER.createObjectNode();
        body.putArray("items").addAll(items);
        body.set("shipping", shipping);
        return body.toString();
    }

    private static int price(JsonNode product) {
        return product.get("price").get("amount").asInt();
    }

    @Test
    void guestCannotUseOrders() throws Exception {
        var body = order(List.of(item(available, 1)), shipping("pickup", null));

        assertProblem(post("/api/orders", body), 401, "unauthorized");
        assertProblem(get("/api/orders"), 401, "unauthorized");
        assertProblem(get("/api/orders/1"), 401, "unauthorized");
    }

    @Test
    void createDeliveryOrder() throws Exception {
        register(client, "buyer@example.com");
        var body =
                order(
                        List.of(item(available, 2), item(another, 1)),
                        shipping("delivery", "ул. Ленина, 15, кв. 42"));

        var response = post("/api/orders", body);

        assertThat(response.status()).isEqualTo(201);
        var created = response.json();
        assertThat(created.get("id").isTextual()).isTrue();
        assertThat(created.get("status").asText()).isEqualTo("paid");
        assertThat(created.get("createdAt").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        assertThat(created.get("total").get("amount").asInt())
                .isEqualTo(price(available) * 2 + price(another));
        assertThat(created.get("total").get("currency").asText()).isEqualTo("RUB");
        assertThat(created.get("shipping").get("address").asText())
                .isEqualTo("ул. Ленина, 15, кв. 42");

        var first = created.get("items").get(0);
        assertThat(first.get("productId").asText()).isEqualTo(available.get("id").asText());
        assertThat(first.get("productName").asText()).isEqualTo(available.get("name").asText());
        assertThat(first.get("priceAtPurchase").get("amount").asInt()).isEqualTo(price(available));
        assertThat(first.get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void pickupOrderNeedsNoAddress() throws Exception {
        register(client, "pickup@example.com");

        var response =
                post("/api/orders", order(List.of(item(available, 1)), shipping("pickup", null)));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("shipping").get("method").asText()).isEqualTo("pickup");
        assertThat(response.json().get("shipping").has("address")).isFalse();
    }

    @Test
    void clientPricesAreIgnored() throws Exception {
        register(client, "cheater@example.com");
        var cheapItem = item(available, 1).put("price", 1);
        var body =
                (ObjectNode) MAPPER.readTree(order(List.of(cheapItem), shipping("pickup", null)));
        body.putObject("total").put("amount", 1).put("currency", "RUB");

        var response = post("/api/orders", body.toString());

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("total").get("amount").asInt()).isEqualTo(price(available));
    }

    @Test
    void duplicateProductsAreMerged() throws Exception {
        register(client, "merge@example.com");
        var body = order(List.of(item(available, 1), item(available, 2)), shipping("pickup", null));

        var created = post("/api/orders", body).json();

        assertThat(created.get("items").size()).isEqualTo(1);
        assertThat(created.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(created.get("total").get("amount").asInt()).isEqualTo(price(available) * 3);
    }

    @Test
    void deliveryWithoutAddressIsRejected() throws Exception {
        register(client, "noaddress@example.com");

        assertProblem(
                post("/api/orders", order(List.of(item(available, 1)), shipping("delivery", null))),
                400,
                "address-required");
        assertProblem(
                post(
                        "/api/orders",
                        order(List.of(item(available, 1)), shipping("delivery", "   "))),
                400,
                "address-required");
        assertThat(get("/api/orders").json()).isEmpty();
    }

    @Test
    void invalidOrderBodiesAreRejected() throws Exception {
        register(client, "invalid@example.com");

        assertProblem(
                post("/api/orders", order(List.of(), shipping("pickup", null))),
                400,
                "validation-error");
        assertProblem(
                post("/api/orders", order(List.of(item(available, 0)), shipping("pickup", null))),
                400,
                "validation-error");
        assertProblem(
                post("/api/orders", order(List.of(item(available, 1)), shipping("teleport", null))),
                400,
                "validation-error");
        assertProblem(
                post("/api/orders", "{\"items\":[{\"productId\":\"1\",\"quantity\":1.5}]}"),
                400,
                "validation-error");
        assertProblem(post("/api/orders", "[]"), 400, "validation-error");
    }

    @Test
    void orderWithUnavailableProductsIsRejectedAsAWhole() throws Exception {
        register(client, "unavailable@example.com");
        var missing = MAPPER.createObjectNode().put("productId", "999999").put("quantity", 1);
        var body =
                order(
                        List.of(item(available, 1), item(unavailable, 1), missing),
                        shipping("pickup", null));

        var response = post("/api/orders", body);

        assertProblem(response, 400, "unavailable-items");
        var ids = new ArrayList<String>();
        response.json().get("unavailableProductIds").forEach(id -> ids.add(id.asText()));
        assertThat(ids).containsExactlyInAnyOrder(unavailable.get("id").asText(), "999999");
        assertThat(get("/api/orders").json()).isEmpty();
    }

    @Test
    void ordersAreListedNewestFirst() throws Exception {
        register(client, "history@example.com");
        post("/api/orders", order(List.of(item(available, 1)), shipping("pickup", null)));
        var second =
                post("/api/orders", order(List.of(item(another, 1)), shipping("pickup", null)))
                        .json();

        var response = get("/api/orders");

        assertThat(response.status()).isEqualTo(200);
        var orders = response.json();
        assertThat(orders.size()).isEqualTo(2);
        assertThat(orders.get(0).get("id").asText()).isEqualTo(second.get("id").asText());
        assertThat(orders.get(0).get("items").get(0).get("productName").asText())
                .isEqualTo(another.get("name").asText());
    }

    @Test
    void newUserHasNoOrders() throws Exception {
        register(client, "empty@example.com");

        var response = get("/api/orders");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().isArray()).isTrue();
        assertThat(response.json()).isEmpty();
    }

    @Test
    void ownOrderIsShown() throws Exception {
        register(client, "owner@example.com");
        var created =
                post("/api/orders", order(List.of(item(available, 1)), shipping("pickup", null)))
                        .json();

        var response = get("/api/orders/" + created.get("id").asText());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).isEqualTo(created);
    }

    @Test
    void ordersAreIsolatedBetweenUsers() throws Exception {
        register(client, "owner@example.com");
        var created =
                post("/api/orders", order(List.of(item(available, 1)), shipping("pickup", null)))
                        .json();

        var stranger = newClient();
        register(stranger, "stranger@example.com");

        assertThat(get(stranger, "/api/orders").json()).isEmpty();
        assertProblem(get(stranger, "/api/orders/" + created.get("id").asText()), 404, "not-found");
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        register(client, "nobody@example.com");

        assertProblem(get("/api/orders/999999"), 404, "not-found");
        assertProblem(get("/api/orders/abc"), 404, "not-found");
    }
}
