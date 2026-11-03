package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record SaleSummaryResponse(long id, String documentNumber, long sellerId, String status, OffsetDateTime soldAt, BigDecimal total) {}
