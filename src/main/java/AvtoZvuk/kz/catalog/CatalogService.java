package AvtoZvuk.kz.catalog;

import AvtoZvuk.kz.accounting.AuditService;
import AvtoZvuk.kz.api.Commands;
import AvtoZvuk.kz.api.dto.catalog.*;
import AvtoZvuk.kz.api.dto.personnel.EmployeeResponse;
import AvtoZvuk.kz.common.*;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final Database db;
    private final ProductRepository products;
    private final CatalogQueryRepository queries;
    private final CurrentActor actors;
    private final Rules rules;
    private final AuditService audit;
    private final PasswordEncoder passwords;

    @Transactional(readOnly = true)
    public List<ProductResponse> products(int limit, int offset) {
        var actor = actors.get();
        if (limit < 1 || limit > 100 || offset < 0) throw BusinessException.invalid("Некорректная страница");
        return queries.products(actor.owner(), limit, offset);
    }

    @Transactional
    public Product saveProduct(Long id, Commands.Product command) {
        var actor = actors.owner();
        rules.validate(command);
        if (command.categoryId() != null)
            db.one("SELECT id FROM categories WHERE id = ? AND active", command.categoryId());
        Product product;
        if (id == null) product = new Product();
        else {
            db.one("SELECT id FROM products WHERE id = ? FOR UPDATE", id);
            product = products.findById(id).orElseThrow(() -> BusinessException.missing("Товар не найден"));
            audit.product(actor.id(), id, "PRODUCT_UPDATE_BEFORE", true);
        }
        product.setName(command.name().trim());
        product.setSku(command.sku().trim());
        product.setBarcode(command.barcode() == null ? null : command.barcode().trim());
        product.setCategoryId(command.categoryId());
        product.setSellingPrice(command.sellingPrice());
        product.setLowStockThreshold(command.lowStockThreshold());
        product.setUpdatedAt(Instant.now());
        products.saveAndFlush(product);
        audit.product(actor.id(), product.getId(), id == null ? "PRODUCT_CREATE" : "PRODUCT_UPDATE", false);
        return product;
    }

    @Transactional
    public void deactivateProduct(long id) {
        var actor = actors.owner();
        db.one("SELECT id FROM products WHERE id = ? FOR UPDATE", id);
        audit.product(actor.id(), id, "PRODUCT_DEACTIVATE_BEFORE", true);
        db.update("UPDATE products SET active = false, updated_at = now() WHERE id = ?", id);
        audit.product(actor.id(), id, "PRODUCT_DEACTIVATE", false);
    }

    @Transactional
    public long category(Commands.Named command) {
        var actor = actors.owner(); rules.validate(command);
        long id = db.id("INSERT INTO categories(name) VALUES (?) RETURNING id", command.name().trim());
        audit.record(actor.id(), "CATEGORY_CREATE", "Category", id, null);
        return id;
    }

    @Transactional
    public long supplier(Commands.Supplier command) {
        var actor = actors.owner(); rules.validate(command);
        long id = db.id("INSERT INTO suppliers(name, phone) VALUES (?, ?) RETURNING id", command.name().trim(), command.phone());
        audit.record(actor.id(), "SUPPLIER_CREATE", "Supplier", id, null);
        return id;
    }

    @Transactional
    public long employee(Commands.Employee command) {
        var actor = actors.owner(); rules.validate(command);
        long id = db.id("INSERT INTO employees(full_name, phone, bonus_rate) VALUES (?, ?, ?) RETURNING id",
                command.fullName().trim(), command.phone(), command.bonusRate());
        audit.record(actor.id(), "EMPLOYEE_CREATE", "Employee", id, null);
        return id;
    }

    @Transactional
    public long user(Commands.User command) {
        var actor = actors.owner(); rules.validate(command);
        if (command.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw BusinessException.invalid("Пароль должен занимать не более 72 байт UTF-8");
        db.one("SELECT id FROM employees WHERE id = ? AND active FOR UPDATE", command.employeeId());
        long id = db.id("INSERT INTO app_users(employee_id, username, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id",
                command.employeeId(), command.username(), passwords.encode(command.password()), command.role().name());
        audit.record(actor.id(), "USER_CREATE", "User", id, null);
        return id;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() { actors.get(); return queries.categories(); }
    @Transactional(readOnly = true)
    public List<SupplierResponse> suppliers() { actors.owner(); return queries.suppliers(); }
    @Transactional(readOnly = true)
    public List<EmployeeResponse> employees() { actors.owner(); return queries.employees(); }
}
