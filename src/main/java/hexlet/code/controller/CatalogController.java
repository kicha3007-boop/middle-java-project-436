package hexlet.code.controller;

import hexlet.code.dto.PageMetaDto;
import hexlet.code.dto.ProductPageDto;
import hexlet.code.dto.ResponseMapper;
import hexlet.code.http.ApiException;
import hexlet.code.http.Json;
import hexlet.code.repository.CategoryRepository;
import hexlet.code.repository.ProductRepository;
import hexlet.code.repository.PromoRepository;
import hexlet.code.request.ProductFilter;
import hexlet.code.util.Ids;
import io.javalin.http.Context;
import java.sql.SQLException;

public final class CatalogController {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final PromoRepository promos;

    public CatalogController(
            CategoryRepository categories, ProductRepository products, PromoRepository promos) {
        this.categories = categories;
        this.products = products;
        this.promos = promos;
    }

    public void categories(Context ctx) throws SQLException {
        Json.send(ctx, 200, categories.findAll().stream().map(ResponseMapper::toDto).toList());
    }

    public void promos(Context ctx) throws SQLException {
        Json.send(ctx, 200, promos.findAll().stream().map(ResponseMapper::toDto).toList());
    }

    public void products(Context ctx) throws SQLException {
        var filter = ProductFilter.from(ctx);
        var page = products.findPage(filter);
        var totalPages = (page.total() + filter.perPage() - 1) / filter.perPage();
        var meta = new PageMetaDto(filter.page(), filter.perPage(), page.total(), totalPages);
        var items = page.items().stream().map(ResponseMapper::toDto).toList();
        Json.send(ctx, 200, new ProductPageDto(items, meta));
    }

    public void product(Context ctx) throws SQLException {
        var id = ctx.pathParam("id");
        var numericId = Ids.parse(id).orElseThrow(() -> ApiException.notFound("Product " + id));
        var product =
                products.findById(numericId)
                        .orElseThrow(() -> ApiException.notFound("Product " + id));
        Json.send(ctx, 200, ResponseMapper.toDto(product));
    }
}
