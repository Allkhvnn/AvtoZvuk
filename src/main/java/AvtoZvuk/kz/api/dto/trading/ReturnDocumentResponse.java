package AvtoZvuk.kz.api.dto.trading;
import java.math.BigDecimal;
import java.util.List;
public record ReturnDocumentResponse(long id, String documentNumber, String status, BigDecimal total, BigDecimal bonus, List<Item> items, List<PaymentResponse> payments) {
    public record Item(long id, long saleItemId, int quantity, BigDecimal refundAmount, boolean restock) {}
}
