package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.dto.trading.PurchaseItemRequest;
import AvtoZvuk.kz.api.dto.trading.PurchaseRequest;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PurchaseService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final StockService stock;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional(timeout = 30)
    public long receive(PurchaseRequest command) {
        var actor = actors.owner(); rules.validate(command);
        DocumentSupport.unique(command.items().stream().map(PurchaseItemRequest::productId).toList());
        var fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.PURCHASE, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        db.one("SELECT id FROM suppliers WHERE id = ? AND active", command.supplierId());
        stock.lockProducts(command.items().stream().map(PurchaseItemRequest::productId).toList(), true);
        BigDecimal total = command.items().stream().map(i -> i.unitCost().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        DocumentSupport.bounded(total);
        long purchase = db.id("""
                INSERT INTO purchases(document_number, supplier_id, created_by, request_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, DocumentSupport.Kind.PURCHASE.number(command.requestKey()), command.supplierId(), actor.id(), command.requestKey(), fingerprint);
        for (var item : command.items()) {
            long line = db.id("INSERT INTO purchase_items(purchase_id, product_id, quantity, unit_cost) VALUES (?, ?, ?, ?) RETURNING id",
                    purchase, item.productId(), item.quantity(), item.unitCost());
            long lot = db.id("INSERT INTO stock_lots(product_id, purchase_item_id, unit_cost) VALUES (?, ?, ?) RETURNING id",
                    item.productId(), line, item.unitCost());
            db.update("""
                    INSERT INTO stock_movements(product_id, lot_id, type, quantity, purchase_item_id, reason, created_by)
                    VALUES (?, ?, 'PURCHASE', ?, ?, 'Поступление', ?)
                    """, item.productId(), lot, item.quantity(), line, actor.id());
        }
        documents.post(DocumentSupport.Kind.PURCHASE, purchase, actor.id());
        audit.record(actor.id(), "PURCHASE_POST", "Purchase", purchase, null);
        return purchase;
    }
}
