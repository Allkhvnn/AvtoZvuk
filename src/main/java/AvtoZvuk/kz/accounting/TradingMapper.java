package AvtoZvuk.kz.accounting;
import AvtoZvuk.kz.api.dto.trading.*;
import org.springframework.stereotype.Component;
@Component
public class TradingMapper {
    PaymentResponse payment(DocumentRepository.PaymentRow r){return new PaymentResponse(r.method(),r.amount());}
    SaleDocumentResponse.Item saleItem(DocumentRepository.SaleItemRow r){return new SaleDocumentResponse.Item(r.id(),r.productId(),r.productName(),r.productSku(),r.quantity(),r.unitPrice(),r.discountAmount(),r.lineTotal(),r.returnedQuantity(),r.returnedAmount(),r.quantity()-r.returnedQuantity());}
    PurchaseDocumentResponse.Item purchaseItem(DocumentRepository.PurchaseItemRow r){return new PurchaseDocumentResponse.Item(r.id(),r.productId(),r.quantity(),r.unitCost(),r.lineTotal());}
    ReturnDocumentResponse.Item returnItem(DocumentRepository.ReturnItemRow r){return new ReturnDocumentResponse.Item(r.id(),r.saleItemId(),r.quantity(),r.refundAmount(),r.restock());}
    SaleSummaryResponse saleSummary(DocumentRepository.SaleSummaryRow r){return new SaleSummaryResponse(r.id(),r.documentNumber(),r.sellerId(),r.status(),r.soldAt(),r.total());}
    PurchaseSummaryResponse purchaseSummary(DocumentRepository.PurchaseSummaryRow r){return new PurchaseSummaryResponse(r.id(),r.documentNumber(),r.status(),r.purchasedAt(),r.supplierId(),r.supplierName(),r.itemCount(),r.total());}
}
