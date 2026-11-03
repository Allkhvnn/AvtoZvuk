package AvtoZvuk.kz.api.dto.personnel;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record BonusAdjustmentRequest(@NotNull UUID requestKey,@Positive long employeeId,
                                     @NotNull @DecimalMin("-99999999999999999.99") @DecimalMax("99999999999999999.99")
                                     @Digits(integer=17,fraction=2) BigDecimal amount,
                                     @NotBlank @Size(max=2000) String reason) {}
