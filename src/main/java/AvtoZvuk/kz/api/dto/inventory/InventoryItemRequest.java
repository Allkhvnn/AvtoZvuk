package AvtoZvuk.kz.api.dto.inventory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record InventoryItemRequest(@Positive long productId,@Min(0) @Max(1000000000) long actualQuantity,
                                   @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal excessUnitCost) {}
