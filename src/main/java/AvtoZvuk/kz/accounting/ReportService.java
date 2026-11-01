package AvtoZvuk.kz.accounting;

import AvtoZvuk.kz.common.BusinessException;
import AvtoZvuk.kz.common.Database;
import AvtoZvuk.kz.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {
    public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Qyzylorda");

    private final Database db;
    private final CurrentActor actors;

    public record Summary(LocalDate from, LocalDate to, BigDecimal salesRevenue, BigDecimal returns,
                          BigDecimal netRevenue, BigDecimal discounts, BigDecimal saleCost,
                          BigDecimal restoredCost, BigDecimal netCost, BigDecimal grossProfit,
                          BigDecimal expenses, BigDecimal grossProfitAfterExpenses,
                          BigDecimal bonusAccrued, BigDecimal bonusPaid, BigDecimal salaryPaid) {}

    public Summary summary(LocalDate from, LocalDate to) {
        actors.owner();
        var period = period(from, to);
        var row = db.one("""
                SELECT
                    (SELECT COALESCE(sum(i.line_total), 0) FROM sale_items i JOIN sales s ON s.id = i.sale_id
                        WHERE s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ?)::numeric(19,2) AS "salesRevenue",
                    (SELECT COALESCE(sum(i.refund_amount), 0) FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                        WHERE r.status = 'POSTED' AND r.returned_at >= ? AND r.returned_at < ?)::numeric(19,2) AS returns,
                    (SELECT COALESCE(sum(i.discount_amount), 0) FROM sale_items i JOIN sales s ON s.id = i.sale_id
                        WHERE s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ?)::numeric(19,2) AS discounts,
                    (SELECT COALESCE(sum(-m.quantity * l.unit_cost), 0) FROM stock_movements m
                        JOIN stock_lots l ON l.id = m.lot_id JOIN sale_items i ON i.id = m.sale_item_id
                        JOIN sales s ON s.id = i.sale_id WHERE m.type = 'SALE' AND s.status = 'POSTED'
                        AND s.sold_at >= ? AND s.sold_at < ?)::numeric(19,2) AS "saleCost",
                    (SELECT COALESCE(sum(m.quantity * l.unit_cost), 0) FROM stock_movements m
                        JOIN stock_lots l ON l.id = m.lot_id JOIN return_items i ON i.id = m.return_item_id
                        JOIN sale_returns r ON r.id = i.return_id WHERE m.type = 'RETURN' AND r.status = 'POSTED'
                        AND r.returned_at >= ? AND r.returned_at < ?)::numeric(19,2) AS "restoredCost",
                    (SELECT COALESCE(sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END), 0)
                        FROM expenses WHERE incurred_at >= ? AND incurred_at < ?)::numeric(19,2) AS expenses,
                    (SELECT COALESCE(sum(amount), 0) FROM bonus_entries
                        WHERE created_at >= ? AND created_at < ?)::numeric(19,2) AS "bonusAccrued",
                    (SELECT COALESCE(sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END), 0)
                        FROM salary_payments WHERE type = 'BONUS' AND paid_at >= ? AND paid_at < ?)::numeric(19,2) AS "bonusPaid",
                    (SELECT COALESCE(sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END), 0)
                        FROM salary_payments WHERE type = 'SALARY' AND paid_at >= ? AND paid_at < ?)::numeric(19,2) AS "salaryPaid"
                """, period.args(9));
        var salesRevenue = money(row, "salesRevenue");
        var returns = money(row, "returns");
        var netRevenue = salesRevenue.subtract(returns);
        var saleCost = money(row, "saleCost");
        var restoredCost = money(row, "restoredCost");
        var netCost = saleCost.subtract(restoredCost);
        var grossProfit = netRevenue.subtract(netCost);
        var expenses = money(row, "expenses");
        return new Summary(from, to, salesRevenue, returns, netRevenue, money(row, "discounts"),
                saleCost, restoredCost, netCost, grossProfit, expenses, grossProfit.subtract(expenses),
                money(row, "bonusAccrued"), money(row, "bonusPaid"), money(row, "salaryPaid"));
    }

    public List<Map<String, Object>> paymentMethods(LocalDate from, LocalDate to) {
        actors.owner();
        var p = period(from, to);
        return db.rows("""
                WITH methods(method) AS (VALUES ('CASH'), ('KASPI_PAY')),
                incoming AS (
                    SELECT pay.method, sum(pay.amount) amount FROM payments pay JOIN sales s ON s.id = pay.sale_id
                    WHERE s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ? GROUP BY pay.method
                ), outgoing AS (
                    SELECT pay.method, sum(pay.amount) amount FROM payments pay JOIN sale_returns r ON r.id = pay.return_id
                    WHERE r.status = 'POSTED' AND r.returned_at >= ? AND r.returned_at < ? GROUP BY pay.method
                )
                SELECT m.method, COALESCE(i.amount, 0)::numeric(19,2) AS incoming,
                    COALESCE(o.amount, 0)::numeric(19,2) AS refunds,
                    (COALESCE(i.amount, 0) - COALESCE(o.amount, 0))::numeric(19,2) AS net
                FROM methods m LEFT JOIN incoming i ON i.method = m.method
                LEFT JOIN outgoing o ON o.method = m.method ORDER BY m.method
                """, p.args(2));
    }

    public List<Map<String, Object>> employees(LocalDate from, LocalDate to) {
        actors.owner();
        var p = period(from, to);
        return db.rows("""
                WITH sale_data AS (
                    SELECT s.seller_id employee_id, count(DISTINCT s.id) sales_count,
                        sum(i.line_total) sales_revenue
                    FROM sales s JOIN sale_items i ON i.sale_id = s.id
                    WHERE s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ? GROUP BY s.seller_id
                ), return_data AS (
                    SELECT s.seller_id employee_id, count(DISTINCT r.id) returns_count,
                        sum(i.refund_amount) returns_amount
                    FROM sale_returns r JOIN sales s ON s.id = r.sale_id JOIN return_items i ON i.return_id = r.id
                    WHERE r.status = 'POSTED' AND r.returned_at >= ? AND r.returned_at < ? GROUP BY s.seller_id
                ), bonus_data AS (
                    SELECT employee_id, sum(amount) bonus_accrued FROM bonus_entries
                    WHERE created_at >= ? AND created_at < ? GROUP BY employee_id
                ), paid_data AS (
                    SELECT employee_id,
                        sum(CASE WHEN type = 'BONUS' AND reversal_of_id IS NULL THEN amount
                            WHEN type = 'BONUS' THEN -amount ELSE 0 END) bonus_paid,
                        sum(CASE WHEN type = 'SALARY' AND reversal_of_id IS NULL THEN amount
                            WHEN type = 'SALARY' THEN -amount ELSE 0 END) salary_paid
                    FROM salary_payments WHERE paid_at >= ? AND paid_at < ? GROUP BY employee_id
                )
                SELECT e.id AS "employeeId", e.full_name AS "fullName", e.active,
                    COALESCE(s.sales_count, 0) AS "salesCount",
                    COALESCE(s.sales_revenue, 0)::numeric(19,2) AS "salesRevenue",
                    COALESCE(r.returns_count, 0) AS "returnsCount",
                    COALESCE(r.returns_amount, 0)::numeric(19,2) AS returns,
                    (COALESCE(s.sales_revenue, 0) - COALESCE(r.returns_amount, 0))::numeric(19,2) AS "netRevenue",
                    COALESCE(b.bonus_accrued, 0)::numeric(19,2) AS "bonusAccrued",
                    COALESCE(p.bonus_paid, 0)::numeric(19,2) AS "bonusPaid",
                    COALESCE(p.salary_paid, 0)::numeric(19,2) AS "salaryPaid"
                FROM employees e LEFT JOIN sale_data s ON s.employee_id = e.id
                LEFT JOIN return_data r ON r.employee_id = e.id LEFT JOIN bonus_data b ON b.employee_id = e.id
                LEFT JOIN paid_data p ON p.employee_id = e.id
                WHERE s.employee_id IS NOT NULL OR r.employee_id IS NOT NULL OR b.employee_id IS NOT NULL OR p.employee_id IS NOT NULL
                ORDER BY "netRevenue" DESC, e.id
                """, p.args(4));
    }

    public List<Map<String, Object>> products(LocalDate from, LocalDate to) {
        actors.owner();
        var p = period(from, to);
        return db.rows("""
                WITH sold AS (
                    SELECT i.product_id, max(i.product_name) product_name, max(i.product_sku) product_sku,
                        sum(i.quantity) sold_quantity, sum(i.line_total) sales_revenue,
                        sum(i.discount_amount) discounts
                    FROM sale_items i JOIN sales s ON s.id = i.sale_id
                    WHERE s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ? GROUP BY i.product_id
                ), returned AS (
                    SELECT i.product_id, sum(i.quantity) returned_quantity, sum(i.refund_amount) returns_amount
                    FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                    WHERE r.status = 'POSTED' AND r.returned_at >= ? AND r.returned_at < ? GROUP BY i.product_id
                ), sold_cost AS (
                    SELECT m.product_id, sum(-m.quantity * l.unit_cost) sale_cost
                    FROM stock_movements m JOIN stock_lots l ON l.id = m.lot_id
                    JOIN sale_items i ON i.id = m.sale_item_id JOIN sales s ON s.id = i.sale_id
                    WHERE m.type = 'SALE' AND s.status = 'POSTED' AND s.sold_at >= ? AND s.sold_at < ? GROUP BY m.product_id
                ), returned_cost AS (
                    SELECT m.product_id, sum(m.quantity * l.unit_cost) restored_cost
                    FROM stock_movements m JOIN stock_lots l ON l.id = m.lot_id
                    JOIN return_items i ON i.id = m.return_item_id JOIN sale_returns r ON r.id = i.return_id
                    WHERE m.type = 'RETURN' AND r.status = 'POSTED' AND r.returned_at >= ? AND r.returned_at < ? GROUP BY m.product_id
                ), keys AS (
                    SELECT product_id FROM sold UNION SELECT product_id FROM returned
                )
                SELECT k.product_id AS "productId", COALESCE(s.product_name, p.name) AS name,
                    COALESCE(s.product_sku, p.sku) AS sku,
                    COALESCE(s.sold_quantity, 0) AS "soldQuantity",
                    COALESCE(r.returned_quantity, 0) AS "returnedQuantity",
                    (COALESCE(s.sold_quantity, 0) - COALESCE(r.returned_quantity, 0)) AS "netQuantity",
                    COALESCE(s.sales_revenue, 0)::numeric(19,2) AS "salesRevenue",
                    COALESCE(r.returns_amount, 0)::numeric(19,2) AS returns,
                    (COALESCE(s.sales_revenue, 0) - COALESCE(r.returns_amount, 0))::numeric(19,2) AS "netRevenue",
                    COALESCE(s.discounts, 0)::numeric(19,2) AS discounts,
                    COALESCE(sc.sale_cost, 0)::numeric(19,2) AS "saleCost",
                    COALESCE(rc.restored_cost, 0)::numeric(19,2) AS "restoredCost",
                    (COALESCE(sc.sale_cost, 0) - COALESCE(rc.restored_cost, 0))::numeric(19,2) AS "netCost",
                    (COALESCE(s.sales_revenue, 0) - COALESCE(r.returns_amount, 0)
                        - COALESCE(sc.sale_cost, 0) + COALESCE(rc.restored_cost, 0))::numeric(19,2) AS "grossProfit"
                FROM keys k JOIN products p ON p.id = k.product_id LEFT JOIN sold s ON s.product_id = k.product_id
                LEFT JOIN returned r ON r.product_id = k.product_id LEFT JOIN sold_cost sc ON sc.product_id = k.product_id
                LEFT JOIN returned_cost rc ON rc.product_id = k.product_id
                ORDER BY "netRevenue" DESC, k.product_id
                """, p.args(4));
    }

    public List<Map<String, Object>> lowStock() {
        actors.owner();
        return db.rows("""
                SELECT p.id AS "productId", p.name, p.sku, p.active, s.quantity,
                    p.low_stock_threshold AS "lowStockThreshold", s.stock_cost AS "stockCost"
                FROM products p JOIN product_stock s ON s.product_id = p.id
                WHERE p.active AND s.low_stock ORDER BY s.quantity, p.name, p.id
                """);
    }

    private Period period(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw BusinessException.invalid("Укажите даты from и to");
        if (to.isBefore(from)) throw BusinessException.invalid("Дата to не может быть раньше from");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw BusinessException.invalid("Период не может превышать 367 дней");
        return new Period(from.atStartOfDay(STORE_ZONE).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(STORE_ZONE).toOffsetDateTime());
    }

    private static BigDecimal money(Map<String, Object> row, String key) {
        return (BigDecimal) row.get(key);
    }

    private record Period(OffsetDateTime start, OffsetDateTime end) {
        Object[] args(int repetitions) {
            var result = new Object[repetitions * 2];
            for (int i = 0; i < repetitions; i++) { result[i * 2] = start; result[i * 2 + 1] = end; }
            return result;
        }
    }
}
