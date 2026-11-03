package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InventoryRepository {
    private final Database db;

    public record StockLot(long id, long quantityRemaining) {}
    public record AdjustmentHeader(long id, String operationType, String reason, Object createdAt) {}

    long createAdjustment(String type, String reason, UUID key, String fingerprint, long actorId) {
        return db.id("""
                INSERT INTO stock_adjustments(reason, created_by, request_key, request_fingerprint, operation_type)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, reason.trim(), actorId, key, fingerprint, type);
    }

    void addAdjustmentItem(long adjustmentId, long productId, long systemQuantity,
                           long actualQuantity, BigDecimal unitCost) {
        db.update("""
                INSERT INTO stock_adjustment_items(adjustment_id, product_id, system_quantity, actual_quantity, unit_cost)
                VALUES (?, ?, ?, ?, ?)
                """, adjustmentId, productId, systemQuantity, actualQuantity, unitCost);
    }

    List<StockLot> lockAvailableLots(long productId) {
        return db.rows("""
                SELECT id, quantity_remaining FROM stock_lots
                WHERE product_id = ? AND quantity_remaining > 0
                ORDER BY received_at, id FOR UPDATE
                """, productId).stream()
                .map(row -> new StockLot(Database.number(row, "id"), Database.number(row, "quantity_remaining")))
                .toList();
    }

    long createExcessLot(long productId, long adjustmentId, BigDecimal unitCost) {
        return db.id("""
                INSERT INTO stock_lots(product_id, opening_adjustment_id, unit_cost)
                VALUES (?, ?, ?) RETURNING id
                """, productId, adjustmentId, unitCost);
    }

    void addMovement(long adjustmentId, long productId, long lotId, int quantity,
                     String reason, long actorId) {
        db.update("""
                INSERT INTO stock_movements(product_id, lot_id, type, quantity, adjustment_id, reason, created_by)
                VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?)
                """, productId, lotId, quantity, adjustmentId, reason.trim(), actorId);
    }

    List<Map<String, Object>> inventories(int limit, int offset) {
        return db.rows("""
                SELECT a.id, a.operation_type AS "operationType", a.reason, a.created_at AS "createdAt",
                    count(i.id) AS "itemCount", COALESCE(sum(abs(i.difference)), 0) AS "absoluteDifference"
                FROM stock_adjustments a LEFT JOIN stock_adjustment_items i ON i.adjustment_id = a.id
                WHERE a.operation_type = 'INVENTORY' GROUP BY a.id
                ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?
                """, limit, offset);
    }

    AdjustmentHeader adjustment(long id) {
        var row = db.one("SELECT id, operation_type, reason, created_at FROM stock_adjustments WHERE id = ?", id);
        return new AdjustmentHeader(Database.number(row, "id"), (String) row.get("operation_type"),
                (String) row.get("reason"), row.get("created_at"));
    }

    List<Map<String, Object>> adjustmentItems(long adjustmentId) {
        return db.rows("""
                SELECT i.product_id AS "productId", p.name, p.sku, i.system_quantity AS "systemQuantity",
                    i.actual_quantity AS "actualQuantity", i.difference, i.unit_cost AS "unitCost"
                FROM stock_adjustment_items i JOIN products p ON p.id = i.product_id
                WHERE i.adjustment_id = ? ORDER BY p.name, i.product_id
                """, adjustmentId);
    }

    void requireProduct(long productId) {
        db.one("SELECT id FROM products WHERE id = ?", productId);
    }

    List<Map<String, Object>> movements(long productId, int limit, int offset) {
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
}
