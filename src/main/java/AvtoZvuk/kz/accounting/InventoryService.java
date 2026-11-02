package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final DocumentSupport documents;
    private final StockService stock;
    private final AuditService audit;

    public record Adjustment(long id, String operationType, String reason, Object createdAt,
                             List<Map<String, Object>> items) {}

    @Transactional(timeout = 30)
    public long inventory(Commands.Inventory command) {
        var actor = actors.owner();
        rules.validate(command);
        DocumentSupport.unique(command.items().stream().map(Commands.InventoryItem::productId).toList());
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.INVENTORY, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        stock.lockProducts(command.items().stream().map(Commands.InventoryItem::productId).toList(), false);
        long adjustment = createAdjustment("INVENTORY", command.reason(), command.requestKey(), fingerprint, actor.id());
        var ordered = command.items().stream().sorted(Comparator.comparingLong(Commands.InventoryItem::productId)).toList();
        for (var item : ordered) {
            var lots = lockLots(item.productId());
            long system = lots.stream().mapToLong(row -> Database.number(row, "quantity_remaining")).sum();
            long difference = item.actualQuantity() - system;
            if (difference > 0 && item.excessUnitCost() == null)
                throw BusinessException.invalid("Для излишка укажите себестоимость товара: " + item.productId());
            if (difference <= 0 && item.excessUnitCost() != null)
                throw BusinessException.invalid("Себестоимость указывается только для излишка: " + item.productId());
            db.update("""
                    INSERT INTO stock_adjustment_items(adjustment_id, product_id, system_quantity, actual_quantity, unit_cost)
                    VALUES (?, ?, ?, ?, ?)
                    """, adjustment, item.productId(), system, item.actualQuantity(), item.excessUnitCost());
            if (difference > 0) receiveExcess(adjustment, item.productId(), Math.toIntExact(difference),
                    item.excessUnitCost(), command.reason(), actor.id());
            else if (difference < 0) consume(lots, adjustment, item.productId(), Math.toIntExact(-difference),
                    command.reason(), actor.id());
        }
        audit.record(actor.id(), "INVENTORY_POST", "StockAdjustment", adjustment, command.reason());
        return adjustment;
    }

    @Transactional(timeout = 30)
    public long writeOff(Commands.WriteOff command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.WRITE_OFF, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        stock.lockProducts(List.of(command.productId()), false);
        var lots = lockLots(command.productId());
        long system = lots.stream().mapToLong(row -> Database.number(row, "quantity_remaining")).sum();
        if (command.quantity() > system) throw BusinessException.conflict("Недостаточно товара для списания");
        long adjustment = createAdjustment("WRITE_OFF", command.reason(), command.requestKey(), fingerprint, actor.id());
        db.update("""
                INSERT INTO stock_adjustment_items(adjustment_id, product_id, system_quantity, actual_quantity)
                VALUES (?, ?, ?, ?)
                """, adjustment, command.productId(), system, system - command.quantity());
        consume(lots, adjustment, command.productId(), command.quantity(), command.reason(), actor.id());
        audit.record(actor.id(), "STOCK_WRITE_OFF", "StockAdjustment", adjustment, command.reason());
        return adjustment;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inventories(int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        return db.rows("""
                SELECT a.id, a.operation_type AS "operationType", a.reason, a.created_at AS "createdAt",
                    count(i.id) AS "itemCount", COALESCE(sum(abs(i.difference)), 0) AS "absoluteDifference"
                FROM stock_adjustments a LEFT JOIN stock_adjustment_items i ON i.adjustment_id = a.id
                WHERE a.operation_type = 'INVENTORY' GROUP BY a.id
                ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?
                """, limit, offset);
    }

    @Transactional(readOnly = true)
    public Adjustment adjustment(long id) {
        actors.owner();
        var header = db.one("SELECT id, operation_type, reason, created_at FROM stock_adjustments WHERE id = ?", id);
        var items = db.rows("""
                SELECT i.product_id AS "productId", p.name, p.sku, i.system_quantity AS "systemQuantity",
                    i.actual_quantity AS "actualQuantity", i.difference, i.unit_cost AS "unitCost"
                FROM stock_adjustment_items i JOIN products p ON p.id = i.product_id
                WHERE i.adjustment_id = ? ORDER BY p.name, i.product_id
                """, id);
        return new Adjustment(id, (String) header.get("operation_type"), (String) header.get("reason"),
                header.get("created_at"), items);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> movements(long productId, int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        db.one("SELECT id FROM products WHERE id = ?", productId);
        return db.rows("""
                WITH history AS (
                    SELECT m.id, m.type, a.operation_type AS adjustment_type, m.quantity, l.unit_cost,
                        m.reason, m.created_at, u.username, m.purchase_item_id, m.sale_id,
                        m.return_item_id, m.adjustment_id,
                        sum(m.quantity) OVER (ORDER BY m.created_at, m.id) AS balance
                    FROM stock_movements m JOIN stock_lots l ON l.id = m.lot_id
                    JOIN app_users u ON u.id = m.created_by
                    LEFT JOIN stock_adjustments a ON a.id = m.adjustment_id
                    WHERE m.product_id = ?
                )
                SELECT id, type, adjustment_type AS "adjustmentType", quantity, unit_cost AS "unitCost",
                    balance, reason, created_at AS "createdAt", username,
                    purchase_item_id AS "purchaseItemId", sale_id AS "saleId",
                    return_item_id AS "returnItemId", adjustment_id AS "adjustmentId"
                FROM history ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, productId, limit, offset);
    }

    private long createAdjustment(String type, String reason, UUID key, String fingerprint, long actor) {
        return db.id("""
                INSERT INTO stock_adjustments(reason, created_by, request_key, request_fingerprint, operation_type)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, reason.trim(), actor, key, fingerprint, type);
    }

    private List<Map<String, Object>> lockLots(long productId) {
        return db.rows("""
                SELECT id, quantity_remaining FROM stock_lots WHERE product_id = ? AND quantity_remaining > 0
                ORDER BY received_at, id FOR UPDATE
                """, productId);
    }

    private void receiveExcess(long adjustment, long product, int quantity, BigDecimal cost, String reason, long actor) {
        long lot = db.id("""
                INSERT INTO stock_lots(product_id, opening_adjustment_id, unit_cost) VALUES (?, ?, ?) RETURNING id
                """, product, adjustment, cost);
        movement(adjustment, product, lot, quantity, reason, actor);
    }

    private void consume(List<Map<String, Object>> lots, long adjustment, long product, int quantity,
                         String reason, long actor) {
        int needed = quantity;
        for (var lot : lots) {
            int take = (int) Math.min(needed, Database.number(lot, "quantity_remaining"));
            if (take > 0) movement(adjustment, product, Database.number(lot, "id"), -take, reason, actor);
            needed -= take;
            if (needed == 0) return;
        }
        throw BusinessException.conflict("Недостаточно товара для корректировки");
    }

    private void movement(long adjustment, long product, long lot, int quantity, String reason, long actor) {
        db.update("""
                INSERT INTO stock_movements(product_id, lot_id, type, quantity, adjustment_id, reason, created_by)
                VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?)
                """, product, lot, quantity, adjustment, reason.trim(), actor);
    }
}
