package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record OpeningStockRequest(@NotNull UUID requestKey,@Positive long productId,@Min(1) @Max(1000000) int quantity,
                                  @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal unitCost,
                                  @NotBlank @Size(max=2000) String reason) {}
