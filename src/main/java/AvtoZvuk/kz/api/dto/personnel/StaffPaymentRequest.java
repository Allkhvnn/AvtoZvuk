package AvtoZvuk.kz.api.dto.personnel;
import AvtoZvuk.kz.api.dto.finance.FinanceMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record StaffPaymentRequest(@NotNull UUID requestKey,@Positive long employeeId,@NotNull Type type,
                                  @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=17,fraction=2) BigDecimal amount,
                                  @NotNull FinanceMethod method,@NotBlank @Size(max=2000) String reason,
                                  @PastOrPresent Instant paidAt) {
    public enum Type { BONUS, SALARY }
}
