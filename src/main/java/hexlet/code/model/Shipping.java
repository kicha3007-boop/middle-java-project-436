package hexlet.code.model;

/** Данные получения заказа: способ (delivery или pickup), получатель, телефон и адрес. */
public record Shipping(String method, String recipientName, String phone, String address) {}
