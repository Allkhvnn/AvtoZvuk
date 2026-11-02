package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static AvtoZvuk.kz.common.Database.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentQueries {
    private final Database db;
    private final CurrentActor actors;

    public record Document(long id, String documentNumber, String status, BigDecimal total,
                           BigDecimal bonus, List<Map<String, Object>> items, List<Map<String, Object>> payments) {}

    public Document sale(long id) {
        var actor = actors.get();
        var header = db.one("SELECT id, document_number, status, seller_id FROM sales WHERE id = ?", id);
        if (!actor.owner() && !Long.valueOf(number(header, "seller_id")).equals(actor.employeeId()))
            throw BusinessException.forbidden();
        var items = db.rows("""
                SELECT id, product_id AS "productId", product_name AS "productName", product_sku AS "productSku",
                quantity, unit_price AS "unitPrice", discount_amount AS "discountAmount", line_total AS "lineTotal"
                FROM sale_items WHERE sale_id = ? ORDER BY id
                """, id);
        return new Document(id, (String) header.get("document_number"), (String) header.get("status"),
                db.amount("SELECT COALESCE(sum(line_total), 0) FROM sale_items WHERE sale_id = ?", id),
                db.amount("SELECT COALESCE(sum(amount), 0) FROM bonus_entries WHERE sale_id = ?", id), items,
                db.rows("SELECT method, amount FROM payments WHERE sale_id = ? ORDER BY id", id));
    }

    public Document purchase(long id) {
        actors.owner();
        var header = db.one("SELECT document_number, status FROM purchases WHERE id = ?", id);
        return new Document(id, (String) header.get("document_number"), (String) header.get("status"),
                db.amount("SELECT COALESCE(sum(line_total), 0) FROM purchase_items WHERE purchase_id = ?", id), BigDecimal.ZERO,
                db.rows("SELECT id, product_id AS \"productId\", quantity, unit_cost AS \"unitCost\", line_total AS \"lineTotal\" FROM purchase_items WHERE purchase_id = ? ORDER BY id", id), List.of());
    }

    public Document returned(long id) {
        actors.owner();
        var header = db.one("SELECT document_number, status FROM sale_returns WHERE id = ?", id);
        return new Document(id, (String) header.get("document_number"), (String) header.get("status"),
                db.amount("SELECT COALESCE(sum(refund_amount), 0) FROM return_items WHERE return_id = ?", id),
                db.amount("SELECT COALESCE(sum(amount), 0) FROM bonus_entries WHERE return_id = ?", id),
                db.rows("SELECT id, sale_item_id AS \"saleItemId\", quantity, refund_amount AS \"refundAmount\", restock FROM return_items WHERE return_id = ? ORDER BY id", id),
                db.rows("SELECT method, amount FROM payments WHERE return_id = ? ORDER BY id", id));
    }

    public List<Map<String, Object>> sales(int limit, int offset) {
        var actor = actors.get();
        if (limit < 1 || limit > 100 || offset < 0) throw BusinessException.invalid("Некорректная страница");
        return db.rows("""
                SELECT s.id, s.document_number AS "documentNumber", s.seller_id AS "sellerId", s.status,
                    s.sold_at AS "soldAt", (SELECT sum(line_total) FROM sale_items WHERE sale_id = s.id) AS total
                FROM sales s WHERE (? OR s.seller_id = ?) AND s.status = 'POSTED'
                ORDER BY s.id DESC LIMIT ? OFFSET ?
                """, actor.owner(), actor.employeeId(), limit, offset);
    }

    public List<Map<String, Object>> purchases(int limit, int offset) {
        actors.owner();
        if (limit < 1 || limit > 100 || offset < 0) throw BusinessException.invalid("Некорректная страница");
        return db.rows("""
                SELECT p.id, p.document_number AS "documentNumber", p.status,
                    p.purchased_at AS "purchasedAt", p.supplier_id AS "supplierId", s.name AS "supplierName",
                    count(i.id) AS "itemCount", COALESCE(sum(i.line_total), 0)::numeric(19,2) AS total
                FROM purchases p JOIN suppliers s ON s.id = p.supplier_id
                LEFT JOIN purchase_items i ON i.purchase_id = p.id
                WHERE p.status = 'POSTED'
                GROUP BY p.id, s.name ORDER BY p.purchased_at DESC, p.id DESC LIMIT ? OFFSET ?
                """, limit, offset);
    }
}
