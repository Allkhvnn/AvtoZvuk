package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.Database;
import AvtoZvuk.kz.api.dto.audit.AuditLogResponse;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final Database db;
    private final CurrentActor actors;

    public List<AuditLogResponse> history(int limit, int offset) {
        actors.owner();
        FinanceService.page(limit, offset);
        return db.query("""
                SELECT a.id, a.action, a.entity_type, a.entity_id, a.reason,
                       u.username, a.created_at
                FROM audit_log a
                JOIN app_users u ON u.id = a.actor_user_id
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """, (row, number) -> new AuditLogResponse(row.getLong("id"), row.getString("action"),
                row.getString("entity_type"), row.getLong("entity_id"), row.getString("reason"),
                row.getString("username"), row.getObject("created_at", java.time.OffsetDateTime.class)), limit, offset);
    }

    public void record(long actor, String action, String entity, long id, String reason) {
        db.update("""
                INSERT INTO audit_log(actor_user_id, action, entity_type, entity_id, reason)
                VALUES (?, ?, ?, ?, ?)
                """, actor, action, entity, id, reason);
    }

    public void product(long actor, long id, String action, boolean before) {
        // Table/column names are fixed constants; no caller-controlled identifiers.
        db.update("INSERT INTO audit_log(actor_user_id, action, entity_type, entity_id, "
                + (before ? "old_data" : "new_data") + ") SELECT ?, ?, 'Product', id, to_jsonb(p) FROM products p WHERE id = ?",
                actor, action, id);
    }
}
