package AvtoZvuk.kz.api.dto.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 250) String name,
        @NotBlank @Size(max = 100) String sku,
        @Size(min = 1, max = 100) String barcode,
        @Positive Long categoryId,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal sellingPrice,
        @Min(0) int lowStockThreshold
) {}
