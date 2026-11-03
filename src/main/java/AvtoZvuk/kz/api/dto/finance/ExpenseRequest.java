package AvtoZvuk.kz.api.dto.finance;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ExpenseRequest(@NotNull UUID requestKey,@Positive long categoryId,
                             @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=17,fraction=2) BigDecimal amount,
                             @NotNull FinanceMethod method,@NotBlank @Size(max=2000) String description,
                             @PastOrPresent Instant incurredAt) {}
