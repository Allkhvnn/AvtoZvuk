package AvtoZvuk.kz.api.dto.inventory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record InventoryDetailResponse(
        long id,
        String operationType,
        String reason,
        OffsetDateTime createdAt,
        List<Item> items
) {
    public record Item(
            long productId,
            String name,
            String sku,
            long systemQuantity,
            long actualQuantity,
            long difference,
            BigDecimal unitCost
    ) {}
}
