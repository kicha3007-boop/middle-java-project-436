package hexlet.code.controller;

import hexlet.code.dto.ResponseMapper;
import hexlet.code.http.ApiException;
import hexlet.code.http.Json;
import hexlet.code.http.SessionAuth;
import hexlet.code.request.CreateOrderRequest;
import hexlet.code.service.OrderService;
import io.javalin.http.Context;
import java.sql.SQLException;

/** Заказы. Авторизацию проверяет общий before-обработчик, здесь берётся готовый пользователь. */
public final class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public void create(Context ctx) throws SQLException {
        var user = SessionAuth.user(ctx);
        var request = CreateOrderRequest.from(Json.readObject(ctx));
        var order = orders.create(user.id(), request);
        Json.send(ctx, 201, ResponseMapper.toDto(order));
    }

    public void index(Context ctx) throws SQLException {
        var user = SessionAuth.user(ctx);
        var list = orders.list(user.id()).stream().map(ResponseMapper::toDto).toList();
        Json.send(ctx, 200, list);
    }

    public void show(Context ctx) throws SQLException {
        var user = SessionAuth.user(ctx);
        var id = ctx.pathParam("id");
        var order =
                orders.find(user.id(), id).orElseThrow(() -> ApiException.notFound("Order " + id));
        Json.send(ctx, 200, ResponseMapper.toDto(order));
    }
}
