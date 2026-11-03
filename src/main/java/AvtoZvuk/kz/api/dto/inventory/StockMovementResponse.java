package AvtoZvuk.kz.api.dto.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockMovementResponse(
        long id,
        String type,
        String adjustmentType,
        int quantity,
        BigDecimal unitCost,
        long balance,
        String reason,
        OffsetDateTime createdAt,
        String username,
        Long purchaseItemId,
        Long saleId,
        Long returnItemId,
        Long adjustmentId
) {}
