package hexlet.code.dto;

public record ProductDto(
        String id,
        String slug,
        String name,
        String description,
        MoneyDto price,
        String categoryId,
        String imageUrl,
        boolean available) {}
