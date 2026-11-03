package AvtoZvuk.kz.api.dto.inventory;

import java.time.OffsetDateTime;

public record InventorySummaryResponse(
        long id,
        String operationType,
        String reason,
        OffsetDateTime createdAt,
        long itemCount,
        long absoluteDifference
) {}
