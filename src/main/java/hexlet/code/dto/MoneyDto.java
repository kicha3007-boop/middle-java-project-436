package hexlet.code.dto;

public record MoneyDto(int amount, String currency) {

    public static MoneyDto rub(int amount) {
        return new MoneyDto(amount, "RUB");
    }
}
