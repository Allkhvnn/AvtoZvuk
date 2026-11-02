package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class StockService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Map<String, Object>> lockProducts(Collection<Long> ids, boolean requireActive) {
        var result = new TreeMap<Long, Map<String, Object>>();
        for (long id : new TreeSet<>(ids)) {
            var product = db.one("SELECT * FROM products WHERE id = ? FOR UPDATE", id);
            if (requireActive && !Boolean.TRUE.equals(product.get("active")))
                throw BusinessException.invalid("Товар отключен: " + id);
            result.put(id, product);
        }
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(long productId, long saleId, long saleItemId, int quantity, long actor) {
        int needed = quantity;
        for (var lot : db.rows("""
                SELECT id, quantity_remaining FROM stock_lots WHERE product_id = ? AND quantity_remaining > 0
                ORDER BY received_at, id FOR UPDATE
                """, productId)) {
            int take = (int) Math.min(needed, Database.number(lot, "quantity_remaining"));
            if (take > 0) db.update("""
                    INSERT INTO stock_movements(product_id, lot_id, type, quantity, sale_item_id, sale_id, reason, created_by)
                    VALUES (?, ?, 'SALE', ?, ?, ?, 'Продажа', ?)
                    """, productId, Database.number(lot, "id"), -take, saleItemId, saleId, actor);
            needed -= take;
            if (needed == 0) return;
        }
        throw BusinessException.conflict("Недостаточно товара на складе: " + productId);
    }

    @Transactional(timeout = 30)
    public long opening(Commands.OpeningStock command) {
        var actor = actors.owner(); rules.validate(command);
        var fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.OPENING, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        lockProducts(java.util.List.of(command.productId()), true);
        long adjustment = db.id("""
                INSERT INTO stock_adjustments(reason, created_by, request_key, request_fingerprint, operation_type)
                VALUES (?, ?, ?, ?, 'OPENING') RETURNING id
                """, command.reason(), actor.id(), command.requestKey(), fingerprint);
        db.update("""
                INSERT INTO stock_adjustment_items(adjustment_id, product_id, system_quantity, actual_quantity, unit_cost)
                VALUES (?, ?, 0, ?, ?)
                """, adjustment, command.productId(), command.quantity(), command.unitCost());
        long lot = db.id("""
                INSERT INTO stock_lots(product_id, opening_adjustment_id, unit_cost) VALUES (?, ?, ?) RETURNING id
                """, command.productId(), adjustment, command.unitCost());
        db.update("""
                INSERT INTO stock_movements(product_id, lot_id, type, quantity, adjustment_id, reason, created_by)
                VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?)
                """, command.productId(), lot, command.quantity(), adjustment, command.reason(), actor.id());
        audit.record(actor.id(), "OPENING_STOCK", "StockAdjustment", adjustment, command.reason());
        return adjustment;
    }
}
