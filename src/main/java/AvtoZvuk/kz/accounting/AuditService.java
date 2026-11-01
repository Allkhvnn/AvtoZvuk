package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final Database db;

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
