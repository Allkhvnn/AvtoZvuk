package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.Database;
import AvtoZvuk.kz.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InventoryRepository {
    private final Database db;

    public record StockLot(long id, long quantityRemaining) {}
    public record AdjustmentHeader(long id, String operationType, String reason, OffsetDateTime createdAt) {}
    public record InventoryRow(long id, String operationType, String reason, OffsetDateTime createdAt,
                               long itemCount, long absoluteDifference) {}
    public record InventoryItemRow(long productId, String name, String sku, long systemQuantity,
                                   long actualQuantity, long difference, BigDecimal unitCost) {}
    public record StockMovementRow(long id, String type, String adjustmentType, int quantity,
                                   BigDecimal unitCost, long balance, String reason, OffsetDateTime createdAt,
                                   String username, Long purchaseItemId, Long saleId, Long returnItemId,
                                   Long adjustmentId) {}

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

    List<InventoryRow> inventories(int limit, int offset) {
        return db.query("""
                SELECT a.id, a.operation_type AS "operationType", a.reason, a.created_at AS "createdAt",
                    count(i.id) AS "itemCount", COALESCE(sum(abs(i.difference)), 0) AS "absoluteDifference"
                FROM stock_adjustments a LEFT JOIN stock_adjustment_items i ON i.adjustment_id = a.id
                WHERE a.operation_type = 'INVENTORY' GROUP BY a.id
                ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new InventoryRow(rs.getLong("id"), rs.getString("operationType"),
                rs.getString("reason"), rs.getObject("createdAt", OffsetDateTime.class), rs.getLong("itemCount"),
                rs.getLong("absoluteDifference")), limit, offset);
    }

    AdjustmentHeader adjustment(long id) {
        return db.query("SELECT id, operation_type, reason, created_at FROM stock_adjustments WHERE id = ?",
                        (rs, rowNum) -> new AdjustmentHeader(rs.getLong("id"), rs.getString("operation_type"),
                                rs.getString("reason"), rs.getObject("created_at", OffsetDateTime.class)), id)
                .stream().findFirst().orElseThrow(() -> BusinessException.missing("Запись не найдена"));
    }

    List<InventoryItemRow> adjustmentItems(long adjustmentId) {
        return db.query("""
                SELECT i.product_id AS "productId", p.name, p.sku, i.system_quantity AS "systemQuantity",
                    i.actual_quantity AS "actualQuantity", i.difference, i.unit_cost AS "unitCost"
                FROM stock_adjustment_items i JOIN products p ON p.id = i.product_id
                WHERE i.adjustment_id = ? ORDER BY p.name, i.product_id
                """, (rs, rowNum) -> new InventoryItemRow(rs.getLong("productId"), rs.getString("name"),
                rs.getString("sku"), rs.getLong("systemQuantity"), rs.getLong("actualQuantity"),
                rs.getLong("difference"), rs.getBigDecimal("unitCost")), adjustmentId);
    }

    void requireProduct(long productId) {
        db.one("SELECT id FROM products WHERE id = ?", productId);
    }

    List<StockMovementRow> movements(long productId, int limit, int offset) {
        return db.query("""
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
                """, (rs, rowNum) -> new StockMovementRow(rs.getLong("id"), rs.getString("type"),
                rs.getString("adjustmentType"), rs.getInt("quantity"), rs.getBigDecimal("unitCost"),
                rs.getLong("balance"), rs.getString("reason"), rs.getObject("createdAt", OffsetDateTime.class),
                rs.getString("username"), rs.getObject("purchaseItemId", Long.class),
                rs.getObject("saleId", Long.class), rs.getObject("returnItemId", Long.class),
                rs.getObject("adjustmentId", Long.class)), productId, limit, offset);
    }
}
