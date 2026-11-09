package AvtoZvuk.kz.api.dto.crm;

import java.math.BigDecimal;

public record CustomerSummaryResponse(long id, String fullName, String phone, String notes,
                                      boolean active, long purchaseCount, BigDecimal totalSpent,
                                      String vehicles) {}
