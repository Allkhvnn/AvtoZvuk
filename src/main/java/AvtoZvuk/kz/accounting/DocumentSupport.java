package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DocumentSupport {
    private final Database db;

    public enum Kind {
        PURCHASE("purchases", "P"), SALE("sales", "S"), RETURN("sale_returns", "R"),
        OPENING("stock_adjustments", "A");
        final String table;
        final String prefix;
        Kind(String table, String prefix) { this.table = table; this.prefix = prefix; }
        public String number(UUID key) { return prefix + "-" + key; }
    }

    public Optional<Long> replay(Kind kind, UUID key, String fingerprint) {
        db.update("SET LOCAL lock_timeout = '5s'");
        db.rows("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", kind.name() + ":" + key);
        return db.optional("SELECT id, request_fingerprint FROM " + kind.table + " WHERE request_key = ?", key)
                .map(row -> {
                    if (!fingerprint.equals(row.get("request_fingerprint")))
                        throw BusinessException.conflict("Этот ключ запроса уже использован с другими данными");
                    return Database.number(row, "id");
                });
    }

    public void post(Kind kind, long id, long actor) {
        db.update("UPDATE " + kind.table + " SET status = 'POSTED', posted_at = now(), posted_by = ? WHERE id = ?", actor, id);
    }

    public void payments(Long saleId, Long returnId, List<Commands.Payment> payments, BigDecimal total, long actor) {
        unique(payments.stream().map(Commands.Payment::method).toList());
        var paid = payments.stream().map(Commands.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Rules.equalMoney(total, paid, "Сумма оплат должна совпадать с итогом документа");
        for (var payment : payments)
            db.update("INSERT INTO payments(sale_id, return_id, method, amount, created_by) VALUES (?, ?, ?, ?, ?)",
                    saleId, returnId, payment.method().name(), payment.amount(), actor);
    }

    public static void unique(Collection<?> values) {
        if (new HashSet<>(values).size() != values.size())
            throw BusinessException.invalid("Повторяющиеся позиции или способы оплаты");
    }

    public static void bounded(BigDecimal total) {
        if (total.compareTo(new BigDecimal("99999999999999999.99")) > 0)
            throw BusinessException.invalid("Сумма документа слишком велика");
    }
}
