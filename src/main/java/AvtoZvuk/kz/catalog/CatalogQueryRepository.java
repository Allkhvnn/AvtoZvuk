package AvtoZvuk.kz.catalog;

import AvtoZvuk.kz.api.dto.catalog.*;
import AvtoZvuk.kz.api.dto.personnel.*;
import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CatalogQueryRepository {
    private final Database db;

    List<ProductResponse> products(boolean includeInactive, int limit, int offset) {
        return db.query("""
                SELECT p.id, p.category_id, p.name, p.sku, p.barcode, p.selling_price,
                    p.low_stock_threshold, p.active, s.quantity, s.low_stock
                FROM products p JOIN product_stock s ON s.product_id = p.id
                WHERE (? OR p.active) ORDER BY p.id LIMIT ? OFFSET ?
                """, (rs, n) -> new ProductResponse(rs.getLong("id"), rs.getObject("category_id", Long.class),
                rs.getString("name"), rs.getString("sku"), rs.getString("barcode"),
                rs.getBigDecimal("selling_price"), rs.getInt("low_stock_threshold"), rs.getBoolean("active"),
                rs.getLong("quantity"), rs.getBoolean("low_stock")), includeInactive, limit, offset);
    }

    List<CategoryResponse> categories() {
        return db.query("SELECT id, name FROM categories WHERE active ORDER BY name",
                (rs, n) -> new CategoryResponse(rs.getLong("id"), rs.getString("name")));
    }

    List<SupplierResponse> suppliers() {
        return db.query("SELECT id, name, phone FROM suppliers WHERE active ORDER BY name LIMIT 500",
                (rs, n) -> new SupplierResponse(rs.getLong("id"), rs.getString("name"), rs.getString("phone")));
    }

    List<EmployeeResponse> employees() {
        return db.query("SELECT id, full_name, phone, bonus_rate, active FROM employees ORDER BY id LIMIT 500",
                (rs, n) -> new EmployeeResponse(rs.getLong("id"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getBigDecimal("bonus_rate"), rs.getBoolean("active")));
    }

    List<UserResponse> users() {
        return db.query("""
                SELECT u.id, u.employee_id, e.full_name AS employee_name, u.username,
                    u.role, u.active, e.active AS employee_active, u.created_at
                FROM app_users u LEFT JOIN employees e ON e.id = u.employee_id ORDER BY u.id
                """, (rs, n) -> new UserResponse(rs.getLong("id"), rs.getObject("employee_id", Long.class),
                rs.getString("employee_name"), rs.getString("username"), rs.getString("role"),
                rs.getBoolean("active"), rs.getObject("employee_active", Boolean.class),
                rs.getObject("created_at", OffsetDateTime.class)));
    }
}
