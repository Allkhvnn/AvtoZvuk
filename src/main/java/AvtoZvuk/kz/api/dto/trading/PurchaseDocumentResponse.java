package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
import java.util.List;
public record PurchaseDocumentResponse(long id, String documentNumber, String status, BigDecimal total, BigDecimal bonus, List<Item> items, List<PaymentResponse> payments) {
    public record Item(long id, long productId, int quantity, BigDecimal unitCost, BigDecimal lineTotal) {}
}
