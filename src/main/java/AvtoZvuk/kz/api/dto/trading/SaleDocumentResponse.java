package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
import java.util.List;
public record SaleDocumentResponse(long id, String documentNumber, String status, BigDecimal total, BigDecimal bonus, List<Item> items, List<PaymentResponse> payments) {
    public record Item(long id, long productId, String productName, String productSku, int quantity,
                       BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal lineTotal,
                       int returnedQuantity, BigDecimal returnedAmount, int availableQuantity) {}
}
