package AvtoZvuk.kz.api.dto.crm;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerSaleResponse(long id, String documentNumber, OffsetDateTime soldAt,
                                   BigDecimal total, String sellerName) {}
