package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PaymentRequest(@NotNull Method method,
                             @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=17,fraction=2) BigDecimal amount) {
    public enum Method { CASH, KASPI_PAY }
}
