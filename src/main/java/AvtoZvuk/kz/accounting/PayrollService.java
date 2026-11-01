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
public class PayrollService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final DocumentSupport documents;
    private final AuditService audit;

    @Transactional
    public long adjustBonus(Commands.BonusAdjustment command) {
        var actor = actors.owner();
        rules.validate(command);
        if (command.amount().compareTo(BigDecimal.ZERO) == 0)
            throw BusinessException.invalid("Сумма корректировки не может быть нулевой");
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.BONUS_ADJUSTMENT, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        lockEmployee(command.employeeId(), false);
        long id = db.id("""
                INSERT INTO bonus_entries(employee_id, type, amount, reason, created_by, request_key, request_fingerprint)
                VALUES (?, 'ADJUSTMENT', ?, ?, ?, ?, ?) RETURNING id
                """, command.employeeId(), command.amount(), command.reason().trim(), actor.id(),
                command.requestKey(), fingerprint);
        audit.record(actor.id(), "BONUS_ADJUST", "BonusEntry", id, command.reason());
        return id;
    }

    @Transactional
    public long pay(Commands.StaffPayment command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.STAFF_PAYMENT, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        lockEmployee(command.employeeId(), false);
        if (command.type() == Commands.StaffPaymentType.BONUS) {
            BigDecimal balance = db.amount("SELECT balance FROM employee_bonus_balances WHERE employee_id = ?",
                    command.employeeId());
            if (balance.compareTo(BigDecimal.ZERO) <= 0 || command.amount().compareTo(balance) > 0)
                throw BusinessException.conflict("Выплата превышает доступный баланс бонусов");
        }
        long id = db.id("""
                INSERT INTO salary_payments(employee_id, type, amount, method, paid_at, reason, created_by,
                    request_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, command.employeeId(), command.type().name(), command.amount(), command.method().name(),
                OffsetDateTime.ofInstant(command.paidAt() == null ? Instant.now() : command.paidAt(), ZoneOffset.UTC),
                command.reason().trim(), actor.id(),
                command.requestKey(), fingerprint);
        audit.record(actor.id(), "STAFF_PAYMENT_CREATE", "SalaryPayment", id, command.reason());
        return id;
    }

    @Transactional
    public long reversePayment(long originalId, Commands.Reversal command) {
        var actor = actors.owner();
        rules.validate(command);
        String fingerprint = Rules.fingerprint(actor.id() + ":PAYMENT_REVERSAL:" + originalId + ":" + command);
        var replay = documents.replay(DocumentSupport.Kind.STAFF_PAYMENT, command.requestKey(), fingerprint);
        if (replay.isPresent()) return replay.get();
        var original = db.one("SELECT * FROM salary_payments WHERE id = ? FOR UPDATE", originalId);
        if (original.get("reversal_of_id") != null) throw BusinessException.invalid("Нельзя отменить запись отмены");
        if (db.id("SELECT count(*) FROM salary_payments WHERE reversal_of_id = ?", originalId) != 0)
            throw BusinessException.conflict("Выплата уже отменена");
        lockEmployee(Database.number(original, "employee_id"), false);
        long id = db.id("""
                INSERT INTO salary_payments(employee_id, type, amount, method, paid_at, reason, created_by,
                    reversal_of_id, request_key, request_fingerprint)
                VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?) RETURNING id
                """, Database.number(original, "employee_id"), original.get("type"), Database.money(original, "amount"),
                original.get("method"), command.reason().trim(), actor.id(), originalId,
                command.requestKey(), fingerprint);
        audit.record(actor.id(), "STAFF_PAYMENT_REVERSE", "SalaryPayment", id, command.reason());
        return id;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> balances() {
        actors.owner();
        return db.rows("""
                SELECT e.id AS "employeeId", e.full_name AS "fullName", e.active, b.accrued, b.paid, b.balance
                FROM employees e JOIN employee_bonus_balances b ON b.employee_id = e.id ORDER BY e.full_name, e.id
                """);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bonusEntries(long employeeId, int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        return db.rows("""
                SELECT id, type, sale_id AS "saleId", return_id AS "returnId", amount, reason, created_at AS "createdAt"
                FROM bonus_entries WHERE employee_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, employeeId, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> payments(long employeeId, int limit, int offset) {
        actors.owner(); FinanceService.page(limit, offset);
        return db.rows("""
                SELECT id, type, amount, method, paid_at AS "paidAt", reason,
                    reversal_of_id AS "reversalOfId",
                    EXISTS(SELECT 1 FROM salary_payments r WHERE r.reversal_of_id = p.id) AS reversed
                FROM salary_payments p WHERE employee_id = ? ORDER BY paid_at DESC, id DESC LIMIT ? OFFSET ?
                """, employeeId, limit, offset);
    }

    private void lockEmployee(long id, boolean activeRequired) {
        var employee = db.one("SELECT id, active FROM employees WHERE id = ? FOR UPDATE", id);
        if (activeRequired && !Boolean.TRUE.equals(employee.get("active")))
            throw BusinessException.invalid("Сотрудник отключен");
    }
}
