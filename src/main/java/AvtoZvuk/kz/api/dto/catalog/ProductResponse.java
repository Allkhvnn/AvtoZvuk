package AvtoZvuk.kz.api.dto.catalog;
import java.math.BigDecimal;
public record ProductResponse(long id, Long categoryId, String name, String sku, String barcode,
                              BigDecimal sellingPrice, int lowStockThreshold, boolean active,
                              long quantity, boolean lowStock) {}
