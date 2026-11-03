package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PurchaseItemRequest(@Positive long productId,@Min(1) @Max(1000000) int quantity,
                                  @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal unitCost) {}
