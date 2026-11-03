package AvtoZvuk.kz.api.dto.trading;
import jakarta.validation.constraints.*;
public record ReturnItemRequest(@Positive long saleItemId,@Min(1) @Max(1000000) int quantity,boolean restock) {}
