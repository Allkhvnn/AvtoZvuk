package AvtoZvuk.kz.security;

import AvtoZvuk.kz.common.BusinessException;
import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentActor {
    private final Database db;

    public Actor get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated())
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Необходим вход в систему");
        var row = db.optional("""
                SELECT u.id, u.employee_id, u.role, u.username FROM app_users u
                LEFT JOIN employees e ON e.id = u.employee_id
                WHERE lower(u.username) = lower(?) AND u.active AND (u.employee_id IS NULL OR e.active)
                """, authentication.getName()).orElseThrow(BusinessException::forbidden);
        return new Actor(Database.number(row, "id"), (Long) row.get("employee_id"),
                (String) row.get("role"), (String) row.get("username"));
    }

    public Actor owner() {
        var actor = get();
        if (!actor.owner()) throw BusinessException.forbidden();
        return actor;
    }

    public record Actor(long id, Long employeeId, String role, String username) {
        public boolean owner() { return "OWNER".equals(role); }
    }
}
