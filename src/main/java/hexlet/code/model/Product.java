package hexlet.code.model;

/** Товар каталога. Цена — целые рубли, imageUrl может отсутствовать. */
public record Product(
        long id,
        String slug,
        String name,
        String description,
        int price,
        long categoryId,
        String imageUrl,
        boolean available) {}
