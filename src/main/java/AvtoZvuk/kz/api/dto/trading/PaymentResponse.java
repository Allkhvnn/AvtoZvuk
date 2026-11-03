package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
public record PaymentResponse(String method, BigDecimal amount) {}
