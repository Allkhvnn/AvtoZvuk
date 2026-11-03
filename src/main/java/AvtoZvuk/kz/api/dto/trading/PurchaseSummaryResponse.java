package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record PurchaseSummaryResponse(long id, String documentNumber, String status, OffsetDateTime purchasedAt, long supplierId, String supplierName, long itemCount, BigDecimal total) {}
