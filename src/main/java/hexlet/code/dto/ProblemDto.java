package hexlet.code.dto;

import java.util.List;

/** Тело ошибки в формате Problem Details (RFC 9457). */
public record ProblemDto(
        String type, String title, int status, String detail, List<String> unavailableProductIds) {}
