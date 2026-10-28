package hexlet.code.request;

import com.fasterxml.jackson.databind.JsonNode;
import hexlet.code.http.ApiException;
import hexlet.code.model.Shipping;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Тело оформления заказа. Проверяется только форма; цены и итог в запросе не принимаются. */
public record CreateOrderRequest(List<CartItem> items, Shipping shipping) {

    private static final Set<String> METHODS = Set.of("delivery", "pickup");

    public static CreateOrderRequest from(JsonNode body) {
        var itemsNode = body.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw ApiException.validation("/body/items: must be a non-empty array");
        }
        var items = new ArrayList<CartItem>();
        for (var i = 0; i < itemsNode.size(); i++) {
            items.add(parseItem(itemsNode.get(i), "/body/items/" + i));
        }
        var shippingNode = body.get("shipping");
        if (shippingNode == null || !shippingNode.isObject()) {
            throw ApiException.validation("/body/shipping: must be an object");
        }
        return new CreateOrderRequest(List.copyOf(items), parseShipping(shippingNode));
    }

    private static CartItem parseItem(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw ApiException.validation(path + ": must be an object");
        }
        var productId = node.get("productId");
        if (productId == null || !productId.isTextual() || productId.asText().isBlank()) {
            throw ApiException.validation(path + "/productId: must be a non-empty string");
        }
        var quantity = node.get("quantity");
        if (quantity == null
                || !quantity.isIntegralNumber()
                || !quantity.canConvertToInt()
                || quantity.asInt() < 1) {
            throw ApiException.validation(path + "/quantity: must be an integer >= 1");
        }
        return new CartItem(productId.asText(), quantity.asInt());
    }

    private static Shipping parseShipping(JsonNode node) {
        var method = requiredText(node, "method");
        if (!METHODS.contains(method)) {
            throw ApiException.validation("/body/shipping/method: must be delivery or pickup");
        }
        var recipientName = requiredText(node, "recipientName");
        var phone = requiredText(node, "phone");
        var addressNode = node.get("address");
        String address = null;
        if (addressNode != null && !addressNode.isNull()) {
            if (!addressNode.isTextual()) {
                throw ApiException.validation("/body/shipping/address: must be a string");
            }
            address = addressNode.asText().trim();
        }
        return new Shipping(method, recipientName, phone, address);
    }

    private static String requiredText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw ApiException.validation(
                    "/body/shipping/" + field + ": must be a non-empty string");
        }
        return value.asText().trim();
    }
}
