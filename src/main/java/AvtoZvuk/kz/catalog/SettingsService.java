package AvtoZvuk.kz.catalog;

import AvtoZvuk.kz.accounting.AuditService;
import AvtoZvuk.kz.api.dto.settings.PasswordChangeRequest;
import AvtoZvuk.kz.api.dto.settings.StoreSettingsRequest;
import AvtoZvuk.kz.api.dto.settings.StoreSettingsResponse;
import AvtoZvuk.kz.common.BusinessException;
import AvtoZvuk.kz.common.Database;
import AvtoZvuk.kz.common.Rules;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public StoreSettingsResponse store() {
        actors.get();
        var row = db.one("SELECT name, phone, address, updated_at FROM store_settings WHERE id = 1");
        return new StoreSettingsResponse((String) row.get("name"), (String) row.get("phone"),
                (String) row.get("address"), timestamp(row.get("updated_at")));
    }

    @Transactional
    public StoreSettingsResponse updateStore(StoreSettingsRequest command) {
        var actor = actors.owner();
        rules.validate(command);
        db.update("""
                UPDATE store_settings
                SET name = ?, phone = ?, address = ?, updated_at = now(), updated_by = ?
                WHERE id = 1
                """, command.name().trim(), blankToNull(command.phone()), blankToNull(command.address()), actor.id());
        audit.record(actor.id(), "STORE_SETTINGS_UPDATE", "StoreSettings", 1, null);
        return store();
    }

    @Transactional
    public void changePassword(PasswordChangeRequest command) {
        var actor = actors.get();
        rules.validate(command);
        var row = db.one("SELECT password_hash FROM app_users WHERE id = ? FOR UPDATE", actor.id());
        if (!passwordEncoder.matches(command.currentPassword(), (String) row.get("password_hash")))
            throw BusinessException.invalid("Текущий пароль указан неверно");
        if (command.currentPassword().equals(command.newPassword()))
            throw BusinessException.invalid("Новый пароль должен отличаться от текущего");
        db.update("UPDATE app_users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(command.newPassword()), actor.id());
        audit.record(actor.id(), "PASSWORD_CHANGE", "User", actor.id(), null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.time.OffsetDateTime timestamp(Object value) {
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp)
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported timestamp value: " + value);
    }
}
