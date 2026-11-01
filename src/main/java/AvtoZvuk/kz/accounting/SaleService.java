package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import static AvtoZvuk.kz.common.Database.money;

@Service
@RequiredArgsConstructor
public class SaleService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final StockService stock;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional(timeout = 30)
    public long sell(Commands.Sale command) {
        var actor = actors.get(); rules.validate(command);
        Long sellerId = command.sellerId() == null ? actor.employeeId() : command.sellerId();
        if (sellerId == null) throw BusinessException.invalid("Укажите сотрудника-продавца");
        if (!actor.owner() && !sellerId.equals(actor.employeeId())) throw BusinessException.forbidden();
        DocumentSupport.unique(command.items().stream().map(Commands.SaleItem::productId).toList());
        var fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.SALE, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        var seller = db.one("SELECT id, bonus_rate FROM employees WHERE id = ? AND active FOR SHARE", sellerId);
        var products = stock.lockProducts(command.items().stream().map(Commands.SaleItem::productId).toList(), true);
        BigDecimal total = BigDecimal.ZERO;
        for (var item : command.items()) {
            var gross = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            if (item.discountAmount().compareTo(gross) > 0)
                throw BusinessException.invalid("Скидка превышает стоимость позиции");
            total = total.add(gross.subtract(item.discountAmount()));
        }
        DocumentSupport.bounded(total);
        long sale = db.id("""
                INSERT INTO sales(document_number, request_key, seller_id, bonus_rate, created_by, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, DocumentSupport.Kind.SALE.number(command.requestKey()), command.requestKey(), sellerId,
                money(seller, "bonus_rate"), actor.id(), fingerprint);
        for (var item : command.items()) {
            var product = products.get(item.productId());
            long line = db.id("""
                    INSERT INTO sale_items(sale_id, product_id, product_name, product_sku, quantity, unit_price, discount_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, sale, item.productId(), product.get("name"), product.get("sku"), item.quantity(), item.unitPrice(), item.discountAmount());
            stock.issue(item.productId(), sale, line, item.quantity(), actor.id());
        }
        documents.payments(sale, null, command.payments(), total, actor.id());
        db.update("""
                INSERT INTO bonus_entries(employee_id, type, sale_id, amount, reason, created_by)
                VALUES (?, 'SALE', ?, ?, 'Бонус за продажу', ?)
                """, sellerId, sale, Rules.money(total.multiply(money(seller, "bonus_rate"))), actor.id());
        documents.post(DocumentSupport.Kind.SALE, sale, actor.id());
        audit.record(actor.id(), "SALE_POST", "Sale", sale, null);
        return sale;
    }
}
