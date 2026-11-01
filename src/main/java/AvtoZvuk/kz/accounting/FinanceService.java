package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional
    public long expenseCategory(Commands.Named command) {
        var actor = actors.owner();
        rules.validate(command);
        long id = db.id("INSERT INTO expense_categories(name) VALUES (?) RETURNING id", command.name().trim());
        audit.record(actor.id(), "EXPENSE_CATEGORY_CREATE", "ExpenseCategory", id, null);
        return id;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expenseCategories() {
        actors.owner();
        return db.rows("SELECT id, name FROM expense_categories ORDER BY name");
    }

    @Transactional
    public long expense(Commands.Expense command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.EXPENSE, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        db.one("SELECT id FROM expense_categories WHERE id = ? FOR SHARE", command.categoryId());
        long id = db.id("""
                INSERT INTO expenses(category_id, amount, method, description, incurred_at, created_by,
                    request_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, command.categoryId(), command.amount(), command.method().name(), command.description().trim(),
                OffsetDateTime.ofInstant(command.incurredAt() == null ? Instant.now() : command.incurredAt(), ZoneOffset.UTC), actor.id(),
                command.requestKey(), fingerprint);
        audit.record(actor.id(), "EXPENSE_CREATE", "Expense", id, command.description());
        return id;
    }

    @Transactional
    public long reverseExpense(long originalId, Commands.Reversal command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":EXPENSE_REVERSAL:" + originalId + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.EXPENSE, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        var original = db.one("SELECT * FROM expenses WHERE id = ? FOR UPDATE", originalId);
        if (original.get("reversal_of_id") != null) throw BusinessException.invalid("Нельзя отменить запись отмены");
        if (db.id("SELECT count(*) FROM expenses WHERE reversal_of_id = ?", originalId) != 0)
            throw BusinessException.conflict("Расход уже отменен");
        long id = db.id("""
                INSERT INTO expenses(category_id, amount, method, description, incurred_at, created_by,
                    reversal_of_id, request_key, request_fingerprint)
                VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?) RETURNING id
                """, Database.number(original, "category_id"), Database.money(original, "amount"),
                original.get("method"), command.reason().trim(), actor.id(), originalId,
                command.requestKey(), fingerprint);
        audit.record(actor.id(), "EXPENSE_REVERSE", "Expense", id, command.reason());
        return id;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expenses(int limit, int offset) {
        actors.owner();
        page(limit, offset);
        return db.rows("""
                SELECT e.id, e.category_id AS "categoryId", c.name AS "categoryName", e.amount, e.method,
                    e.description, e.incurred_at AS "incurredAt", e.reversal_of_id AS "reversalOfId",
                    EXISTS(SELECT 1 FROM expenses r WHERE r.reversal_of_id = e.id) AS reversed
                FROM expenses e JOIN expense_categories c ON c.id = e.category_id
                ORDER BY e.incurred_at DESC, e.id DESC LIMIT ? OFFSET ?
                """, limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> expenseTotals() {
        actors.owner();
        return db.one("""
                SELECT COALESCE(sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END), 0)::numeric(19,2) AS total,
                    COALESCE(sum(CASE WHEN reversal_of_id IS NULL AND method = 'CASH' THEN amount
                        WHEN reversal_of_id IS NOT NULL AND method = 'CASH' THEN -amount ELSE 0 END), 0)::numeric(19,2) AS cash,
                    COALESCE(sum(CASE WHEN reversal_of_id IS NULL AND method = 'KASPI_PAY' THEN amount
                        WHEN reversal_of_id IS NOT NULL AND method = 'KASPI_PAY' THEN -amount ELSE 0 END), 0)::numeric(19,2) AS "kaspiPay",
                    COALESCE(sum(CASE WHEN reversal_of_id IS NULL AND method = 'BANK_TRANSFER' THEN amount
                        WHEN reversal_of_id IS NOT NULL AND method = 'BANK_TRANSFER' THEN -amount ELSE 0 END), 0)::numeric(19,2) AS "bankTransfer"
                FROM expenses
                """);
    }

    static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw BusinessException.invalid("Некорректная страница");
    }
}
