package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static AvtoZvuk.kz.common.Database.*;

@Service
@RequiredArgsConstructor
public class ReturnService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final StockService stock;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional(timeout = 30)
    public long returnSale(Commands.SaleReturn command) {
        // Returns are owner-only until the shop defines seller return permissions.
        var actor = actors.owner(); rules.validate(command);
        DocumentSupport.unique(command.items().stream().map(Commands.ReturnItem::saleItemId).toList());
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.RETURN, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        var sale = db.one("SELECT * FROM sales WHERE id = ? FOR UPDATE", command.saleId());
        if (!"POSTED".equals(sale.get("status"))) throw BusinessException.invalid("Продажа не проведена");
        var lines = new HashMap<Long, Map<String, Object>>();
        for (var line : db.rows("SELECT * FROM sale_items WHERE sale_id = ? ORDER BY product_id, id FOR UPDATE", command.saleId()))
            lines.put(number(line, "id"), line);
        var productIds = new ArrayList<Long>();
        for (var item : command.items()) {
            var line = lines.get(item.saleItemId());
            if (line == null) throw BusinessException.invalid("Позиция не принадлежит исходной продаже");
            productIds.add(number(line, "product_id"));
        }
        stock.lockProducts(productIds, false);
        long returned = db.id("""
                INSERT INTO sale_returns(document_number, request_key, sale_id, reason, created_by, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, DocumentSupport.Kind.RETURN.number(command.requestKey()), command.requestKey(), command.saleId(),
                command.reason(), actor.id(), fingerprint);
        BigDecimal total = BigDecimal.ZERO;
        for (var item : command.items()) {
            var line = lines.get(item.saleItemId());
            var previous = db.one("""
                    SELECT COALESCE(sum(i.quantity), 0) AS quantity, COALESCE(sum(i.refund_amount), 0) AS amount
                    FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                    WHERE i.sale_item_id = ? AND r.status = 'POSTED'
                    """, item.saleItemId());
            long priorQuantity = number(previous, "quantity");
            long soldQuantity = number(line, "quantity");
            if (priorQuantity + item.quantity() > soldQuantity)
                throw BusinessException.conflict("Количество возврата превышает доступное количество");
            // Cumulative rounding guarantees that the final return refunds every remaining kopek.
            BigDecimal cumulative = money(line, "line_total").multiply(BigDecimal.valueOf(priorQuantity + item.quantity()))
                    .divide(BigDecimal.valueOf(soldQuantity), 2, RoundingMode.HALF_UP);
            BigDecimal refund = cumulative.subtract(money(previous, "amount"));
            long returnItem = db.id("""
                    INSERT INTO return_items(return_id, sale_id, sale_item_id, product_id, quantity, refund_amount, restock)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, returned, command.saleId(), item.saleItemId(), number(line, "product_id"), item.quantity(), refund, item.restock());
            if (item.restock()) restore(item, returnItem, number(line, "product_id"), priorQuantity, actor.id());
            total = total.add(refund);
        }
        documents.payments(null, returned, command.payments(), total, actor.id());
        BigDecimal soldTotal = db.amount("SELECT sum(line_total) FROM sale_items WHERE sale_id = ?", command.saleId());
        BigDecimal priorRefunds = db.amount("""
                SELECT COALESCE(sum(i.refund_amount), 0) FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                WHERE r.sale_id = ? AND r.status = 'POSTED'
                """, command.saleId());
        BigDecimal before = soldTotal.subtract(priorRefunds);
        BigDecimal rate = money(sale, "bonus_rate");
        BigDecimal correction = Rules.money(before.subtract(total).multiply(rate)).subtract(Rules.money(before.multiply(rate)));
        db.one("SELECT id FROM employees WHERE id = ? FOR SHARE", number(sale, "seller_id"));
        db.update("""
                INSERT INTO bonus_entries(employee_id, type, return_id, amount, reason, created_by)
                VALUES (?, 'RETURN', ?, ?, ?, ?)
                """, number(sale, "seller_id"), returned, correction, command.reason(), actor.id());
        documents.post(DocumentSupport.Kind.RETURN, returned, actor.id());
        audit.record(actor.id(), "RETURN_POST", "SaleReturn", returned, command.reason());
        return returned;
    }

    private void restore(Commands.ReturnItem item, long returnItem, long product, long priorQuantity, long actor) {
        long skip = priorQuantity;
        int needed = item.quantity();
        // Prior defective returns also consume original units, even though they had no stock movement.
        for (var issue : db.rows("""
                SELECT m.id, m.lot_id, -m.quantity AS issued FROM stock_movements m
                JOIN stock_lots l ON l.id = m.lot_id WHERE m.sale_item_id = ? AND m.type = 'SALE'
                ORDER BY l.received_at, l.id, m.id
                """, item.saleItemId())) {
            long capacity = number(issue, "issued");
            long skipped = Math.min(skip, capacity);
            skip -= skipped;
            int take = (int) Math.min(needed, capacity - skipped);
            if (take == 0) continue;
            db.update("""
                    INSERT INTO stock_movements(product_id, lot_id, type, quantity, return_item_id,
                        original_movement_id, reason, created_by)
                    VALUES (?, ?, 'RETURN', ?, ?, ?, 'Возврат покупателя', ?)
                    """, product, number(issue, "lot_id"), take, returnItem, number(issue, "id"), actor);
            needed -= take;
            if (needed == 0) return;
        }
        throw BusinessException.conflict("Не удалось определить исходные партии возврата");
    }
}
