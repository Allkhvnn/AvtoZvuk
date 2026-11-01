package AvtoZvuk.kz.catalog;

import AvtoZvuk.kz.accounting.AuditService;
import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonnelService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final AuditService audit;

    @Transactional
    public void updateEmployee(long id, Commands.Employee command) {
        var actor = actors.owner();
        rules.validate(command);
        db.one("SELECT id FROM employees WHERE id = ? FOR UPDATE", id);
        db.update("UPDATE employees SET full_name = ?, phone = ?, bonus_rate = ? WHERE id = ?",
                command.fullName().trim(), command.phone(), command.bonusRate(), id);
        audit.record(actor.id(), "EMPLOYEE_UPDATE", "Employee", id, null);
    }

    @Transactional
    public void employeeStatus(long id, Commands.EmployeeStatus command) {
        var actor = actors.owner();
        rules.validate(command);
        var employee = db.one("SELECT id, active FROM employees WHERE id = ? FOR UPDATE", id);
        if (Boolean.TRUE.equals(employee.get("active")) == command.active()) return;
        if (!command.active() && actor.employeeId() != null && actor.employeeId() == id)
            throw BusinessException.conflict("Нельзя отключить собственного сотрудника");
        db.update("UPDATE employees SET active = ? WHERE id = ?", command.active(), id);
        if (!command.active()) db.update("UPDATE app_users SET active = false WHERE employee_id = ?", id);
        audit.record(actor.id(), command.active() ? "EMPLOYEE_ACTIVATE" : "EMPLOYEE_DEACTIVATE",
                "Employee", id, command.reason());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users() {
        actors.owner();
        return db.rows("""
                SELECT u.id, u.employee_id AS "employeeId", e.full_name AS "employeeName", u.username,
                    u.role, u.active, e.active AS "employeeActive", u.created_at AS "createdAt"
                FROM app_users u LEFT JOIN employees e ON e.id = u.employee_id ORDER BY u.id
                """);
    }

    @Transactional
    public void userStatus(long id, Commands.UserStatus command) {
        var actor = actors.owner();
        rules.validate(command);
        var user = db.one("""
                SELECT u.id, u.employee_id, u.role, u.active, COALESCE(e.active, true) AS employee_active
                FROM app_users u LEFT JOIN employees e ON e.id = u.employee_id WHERE u.id = ? FOR UPDATE OF u
                """, id);
        if (Boolean.TRUE.equals(user.get("active")) == command.active()) return;
        if (!command.active() && actor.id() == id)
            throw BusinessException.conflict("Нельзя отключить собственную учетную запись");
        if (command.active() && !Boolean.TRUE.equals(user.get("employee_active")))
            throw BusinessException.invalid("Сначала активируйте сотрудника");
        if (!command.active() && "OWNER".equals(user.get("role")) &&
                db.id("SELECT count(*) FROM app_users WHERE role = 'OWNER' AND active") <= 1)
            throw BusinessException.conflict("Нельзя отключить последнего активного владельца");
        db.update("UPDATE app_users SET active = ? WHERE id = ?", command.active(), id);
        audit.record(actor.id(), command.active() ? "USER_ACTIVATE" : "USER_DEACTIVATE",
                "User", id, command.reason());
    }
}
