package AvtoZvuk.kz.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Commands {
    private Commands() {}

    public record Named(@NotBlank @Size(max = 150) String name) {}
    public enum FinanceMethod { CASH, KASPI_PAY, BANK_TRANSFER }
    public enum StaffPaymentType { BONUS, SALARY }
    public record Expense(@NotNull UUID requestKey, @Positive long categoryId,
                          @NotNull @DecimalMin(value = "0", inclusive = false)
                          @Digits(integer = 17, fraction = 2) BigDecimal amount,
                          @NotNull FinanceMethod method, @NotBlank @Size(max = 2000) String description,
                          @PastOrPresent Instant incurredAt) {}
    public record Reversal(@NotNull UUID requestKey, @NotBlank @Size(max = 2000) String reason) {}
    public record BonusAdjustment(@NotNull UUID requestKey, @Positive long employeeId,
                                  @NotNull @DecimalMin("-99999999999999999.99")
                                  @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 2000) String reason) {}
    public record StaffPayment(@NotNull UUID requestKey, @Positive long employeeId,
                               @NotNull StaffPaymentType type,
                               @NotNull @DecimalMin(value = "0", inclusive = false)
                               @Digits(integer = 17, fraction = 2) BigDecimal amount,
                               @NotNull FinanceMethod method, @NotBlank @Size(max = 2000) String reason,
                               @PastOrPresent Instant paidAt) {}
}
