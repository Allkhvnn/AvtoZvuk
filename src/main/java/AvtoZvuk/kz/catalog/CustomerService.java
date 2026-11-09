package AvtoZvuk.kz.catalog;

import AvtoZvuk.kz.accounting.AuditService;
import AvtoZvuk.kz.api.dto.crm.*;
import AvtoZvuk.kz.common.Database;
import AvtoZvuk.kz.common.Rules;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final Database db;
    private final CurrentActor actors;
    private final Rules rules;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<CustomerSummaryResponse> customers() {
        actors.get();
        return db.query("""
                SELECT c.id, c.full_name, c.phone, c.notes, c.active,
                       COALESCE(purchases.purchase_count, 0) AS purchase_count,
                       COALESCE(purchases.total_spent, 0) AS total_spent,
                       COALESCE(vehicle_list.vehicles, '') AS vehicles
                FROM customers c
                LEFT JOIN LATERAL (
                    SELECT count(DISTINCT s.id) AS purchase_count,
                           COALESCE(sum(si.quantity * si.unit_price - si.discount_amount), 0) AS total_spent
                    FROM sales s JOIN sale_items si ON si.sale_id = s.id
                    WHERE s.customer_id = c.id AND s.status = 'POSTED'
                ) purchases ON true
                LEFT JOIN LATERAL (
                    SELECT string_agg(trim(v.make || ' ' || COALESCE(v.model, '') ||
                           CASE WHEN v.plate_number IS NULL THEN '' ELSE ' · ' || v.plate_number END), ', ' ORDER BY v.id) AS vehicles
                    FROM customer_vehicles v WHERE v.customer_id = c.id
                ) vehicle_list ON true
                ORDER BY c.active DESC, c.full_name, c.id
                """, (row, n) -> new CustomerSummaryResponse(row.getLong("id"), row.getString("full_name"),
                row.getString("phone"), row.getString("notes"), row.getBoolean("active"),
                row.getLong("purchase_count"), row.getBigDecimal("total_spent"), row.getString("vehicles")));
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse customer(long id) {
        actors.get();
        var customer = db.one("SELECT id, full_name, phone, notes, active FROM customers WHERE id = ?", id);
        var vehicles = db.query("""
                SELECT id, make, model, production_year, plate_number, notes
                FROM customer_vehicles WHERE customer_id = ? ORDER BY id
                """, (row, n) -> new VehicleResponse(row.getLong("id"), row.getString("make"),
                row.getString("model"), (Integer) row.getObject("production_year"),
                row.getString("plate_number"), row.getString("notes")), id);
        var sales = db.query("""
                SELECT s.id, s.document_number, s.sold_at,
                       sum(si.quantity * si.unit_price - si.discount_amount) AS total,
                       e.full_name AS seller_name
                FROM sales s JOIN sale_items si ON si.sale_id = s.id
                JOIN employees e ON e.id = s.seller_id
                WHERE s.customer_id = ? AND s.status = 'POSTED'
                GROUP BY s.id, e.full_name ORDER BY s.sold_at DESC, s.id DESC
                """, (row, n) -> new CustomerSaleResponse(row.getLong("id"), row.getString("document_number"),
                row.getObject("sold_at", java.time.OffsetDateTime.class), row.getBigDecimal("total"),
                row.getString("seller_name")), id);
        return new CustomerDetailResponse(Database.number(customer, "id"), (String) customer.get("full_name"),
                (String) customer.get("phone"), (String) customer.get("notes"),
                Boolean.TRUE.equals(customer.get("active")), vehicles, sales);
    }

    @Transactional
    public long create(CustomerRequest command) {
        var actor = actors.get(); rules.validate(command);
        long id = db.id("""
                INSERT INTO customers(full_name, phone, notes, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, command.fullName().trim(), command.phone().trim(), blank(command.notes()), actor.id(), actor.id());
        replaceVehicles(id, command.vehicles());
        audit.record(actor.id(), "CUSTOMER_CREATE", "Customer", id, null);
        return id;
    }

    @Transactional
    public void update(long id, CustomerRequest command) {
        var actor = actors.get(); rules.validate(command);
        db.one("SELECT id FROM customers WHERE id = ? FOR UPDATE", id);
        db.update("""
                UPDATE customers SET full_name = ?, phone = ?, notes = ?, updated_at = now(), updated_by = ?
                WHERE id = ?
                """, command.fullName().trim(), command.phone().trim(), blank(command.notes()), actor.id(), id);
        replaceVehicles(id, command.vehicles());
        audit.record(actor.id(), "CUSTOMER_UPDATE", "Customer", id, null);
    }

    @Transactional
    public void status(long id, CustomerStatusRequest command) {
        var actor = actors.get(); rules.validate(command);
        db.one("SELECT id FROM customers WHERE id = ? FOR UPDATE", id);
        db.update("UPDATE customers SET active = ?, updated_at = now(), updated_by = ? WHERE id = ?",
                command.active(), actor.id(), id);
        audit.record(actor.id(), command.active() ? "CUSTOMER_ACTIVATE" : "CUSTOMER_DEACTIVATE",
                "Customer", id, command.reason());
    }

    private void replaceVehicles(long customerId, List<VehicleRequest> vehicles) {
        db.update("DELETE FROM customer_vehicles WHERE customer_id = ?", customerId);
        for (var vehicle : vehicles) db.update("""
                INSERT INTO customer_vehicles(customer_id, make, model, production_year, plate_number, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """, customerId, vehicle.make().trim(), blank(vehicle.model()), vehicle.productionYear(),
                blank(vehicle.plateNumber()), blank(vehicle.notes()));
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
