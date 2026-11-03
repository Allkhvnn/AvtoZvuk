package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.dto.inventory.InventoryDetailResponse;
import AvtoZvuk.kz.api.dto.inventory.InventorySummaryResponse;
import AvtoZvuk.kz.api.dto.inventory.StockMovementResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {
    InventorySummaryResponse toSummary(InventoryRepository.InventoryRow row) {
        return new InventorySummaryResponse(row.id(), row.operationType(), row.reason(), row.createdAt(),
                row.itemCount(), row.absoluteDifference());
    }

    InventoryDetailResponse.Item toDetailItem(InventoryRepository.InventoryItemRow row) {
        return new InventoryDetailResponse.Item(row.productId(), row.name(), row.sku(), row.systemQuantity(),
                row.actualQuantity(), row.difference(), row.unitCost());
    }

    StockMovementResponse toMovement(InventoryRepository.StockMovementRow row) {
        return new StockMovementResponse(row.id(), row.type(), row.adjustmentType(), row.quantity(), row.unitCost(),
                row.balance(), row.reason(), row.createdAt(), row.username(), row.purchaseItemId(), row.saleId(),
                row.returnItemId(), row.adjustmentId());
    }
}
