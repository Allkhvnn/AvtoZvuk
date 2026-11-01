\set ON_ERROR_STOP on
BEGIN;
CREATE FUNCTION pg_temp.assert_true(condition boolean, message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF condition IS DISTINCT FROM true THEN RAISE EXCEPTION 'Assertion failed: %', message; END IF;
END;
$$;
CREATE FUNCTION pg_temp.expect_error(statement text, expected_message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        IF position(expected_message IN SQLERRM) > 0 THEN RETURN; END IF;
        RAISE EXCEPTION 'Unexpected error: % (expected %)', SQLERRM, expected_message;
    END;
    RAISE EXCEPTION 'Statement unexpectedly succeeded: %', statement;
END;
$$;

INSERT INTO employees(full_name) VALUES ('Test seller') RETURNING id AS employee_id \gset
INSERT INTO app_users(employee_id, username, password_hash, role)
VALUES (:employee_id, 'schema-test', 'test-only-not-a-login', 'OWNER') RETURNING id AS user_id \gset
INSERT INTO suppliers(name) VALUES ('Test supplier') RETURNING id AS supplier_id \gset
INSERT INTO products(name, sku, selling_price) VALUES ('Test speaker', 'SCHEMA-TEST', 200)
RETURNING id AS product_id \gset

INSERT INTO purchases(document_number, supplier_id, created_by)
VALUES ('TEST-P1', :supplier_id, :user_id) RETURNING id AS purchase_id \gset
INSERT INTO purchase_items(purchase_id, product_id, quantity, unit_cost)
VALUES (:purchase_id, :product_id, 1, 100) RETURNING id AS purchase_item_1 \gset
INSERT INTO purchase_items(purchase_id, product_id, quantity, unit_cost)
VALUES (:purchase_id, :product_id, 2, 120) RETURNING id AS purchase_item_2 \gset
INSERT INTO stock_lots(product_id, purchase_item_id, unit_cost)
VALUES (:product_id, :purchase_item_1, 100) RETURNING id AS lot_1 \gset
INSERT INTO stock_lots(product_id, purchase_item_id, unit_cost)
VALUES (:product_id, :purchase_item_2, 120) RETURNING id AS lot_2 \gset
INSERT INTO stock_movements(product_id, lot_id, type, quantity, purchase_item_id, reason, created_by)
VALUES (:product_id, :lot_1, 'PURCHASE', 1, :purchase_item_1, 'Receipt', :user_id),
       (:product_id, :lot_2, 'PURCHASE', 2, :purchase_item_2, 'Receipt', :user_id);
UPDATE purchases SET status = 'POSTED', posted_by = :user_id, posted_at = now() WHERE id = :purchase_id;
SELECT pg_temp.assert_true((SELECT quantity = 3 AND stock_cost = 340 FROM product_stock
    WHERE product_id = :product_id), 'purchase stock and cost');
SELECT pg_temp.expect_error(format('UPDATE purchase_items SET unit_cost = 1 WHERE id = %s', :purchase_item_1),
    'draft document');
SELECT pg_temp.expect_error(format('UPDATE stock_lots SET quantity_remaining = 100 WHERE id = %s', :lot_1),
    'only be changed by stock movements');

INSERT INTO sales(document_number, request_key, seller_id, created_by)
VALUES ('TEST-S1', '00000000-0000-0000-0000-000000000001', :employee_id, :user_id)
RETURNING id AS sale_id \gset
INSERT INTO sale_items(sale_id, product_id, product_name, product_sku, quantity, unit_price, discount_amount)
VALUES (:sale_id, :product_id, 'Test speaker', 'SCHEMA-TEST', 2, 200, 20) RETURNING id AS sale_item_id \gset
INSERT INTO stock_movements(product_id, lot_id, type, quantity, sale_item_id, sale_id, reason, created_by)
VALUES (:product_id, :lot_1, 'SALE', -1, :sale_item_id, :sale_id, 'Sale', :user_id)
RETURNING id AS original_movement_id \gset
INSERT INTO stock_movements(product_id, lot_id, type, quantity, sale_item_id, sale_id, reason, created_by)
VALUES (:product_id, :lot_2, 'SALE', -1, :sale_item_id, :sale_id, 'Sale', :user_id);
INSERT INTO payments(sale_id, method, amount, created_by)
VALUES (:sale_id, 'CASH', 100, :user_id), (:sale_id, 'KASPI_PAY', 280, :user_id);
INSERT INTO bonus_entries(employee_id, type, sale_id, amount, reason, created_by)
VALUES (:employee_id, 'SALE', :sale_id, 19, '5 percent of 380', :user_id);
UPDATE sales SET status = 'POSTED', posted_by = :user_id, posted_at = now() WHERE id = :sale_id;
SELECT pg_temp.assert_true((SELECT quantity = 1 AND stock_cost = 120 FROM product_stock
    WHERE product_id = :product_id), 'FIFO sale stock');
SELECT pg_temp.assert_true((SELECT sum(-m.quantity * l.unit_cost) = 220
    FROM stock_movements m JOIN stock_lots l ON l.id = m.lot_id WHERE m.sale_id = :sale_id), 'sale COGS');
SELECT pg_temp.expect_error(format('UPDATE sales SET notes = ''changed'' WHERE id = %s', :sale_id),
    'finalized document');
SELECT pg_temp.expect_error(format('DELETE FROM stock_movements WHERE id = %s', :original_movement_id),
    'append-only');
SELECT pg_temp.expect_error(format('INSERT INTO payments(sale_id, method, amount, created_by)
    VALUES (%s, ''CASH'', 1, %s)', :sale_id, :user_id), 'before posting');

INSERT INTO sale_returns(document_number, request_key, sale_id, reason, created_by)
VALUES ('TEST-R1', '00000000-0000-0000-0000-000000000002', :sale_id, 'Customer return', :user_id)
RETURNING id AS return_id \gset
INSERT INTO return_items(return_id, sale_id, sale_item_id, product_id, quantity, refund_amount)
VALUES (:return_id, :sale_id, :sale_item_id, :product_id, 1, 190) RETURNING id AS return_item_id \gset
INSERT INTO stock_movements(product_id, lot_id, type, quantity, return_item_id, original_movement_id, reason, created_by)
VALUES (:product_id, :lot_1, 'RETURN', 1, :return_item_id, :original_movement_id, 'Return', :user_id);
INSERT INTO payments(return_id, method, amount, created_by) VALUES (:return_id, 'KASPI_PAY', 190, :user_id);
INSERT INTO bonus_entries(employee_id, type, return_id, amount, reason, created_by)
VALUES (:employee_id, 'RETURN', :return_id, -9.50, 'Return correction', :user_id);
UPDATE sale_returns SET status = 'POSTED', posted_by = :user_id, posted_at = now() WHERE id = :return_id;
SELECT pg_temp.assert_true((SELECT quantity = 2 AND stock_cost = 220 FROM product_stock
    WHERE product_id = :product_id), 'return restores original cost');
INSERT INTO salary_payments(employee_id, type, amount, method, reason, created_by)
VALUES (:employee_id, 'BONUS', 5, 'CASH', 'Bonus paid', :user_id) RETURNING id AS salary_id \gset
SELECT pg_temp.assert_true((SELECT accrued = 9.50 AND paid = 5 AND balance = 4.50
    FROM employee_bonus_balances WHERE employee_id = :employee_id), 'bonus accrual, return and payout');
INSERT INTO salary_payments(employee_id, type, amount, method, reason, created_by, reversal_of_id)
VALUES (:employee_id, 'BONUS', 5, 'CASH', 'Payment correction', :user_id, :salary_id);
SELECT pg_temp.assert_true((SELECT balance = 9.50 FROM employee_bonus_balances
    WHERE employee_id = :employee_id), 'payout reversal');

INSERT INTO stock_adjustments(reason, created_by) VALUES ('Inventory check', :user_id)
RETURNING id AS adjustment_id \gset
SELECT pg_temp.expect_error(format('INSERT INTO stock_movements(product_id, lot_id, type, quantity,
    adjustment_id, reason, created_by) VALUES (%s, %s, ''ADJUSTMENT'', -100, %s, ''Count'', %s)',
    :product_id, :lot_1, :adjustment_id, :user_id), 'Insufficient stock');
SELECT pg_temp.assert_true((SELECT quantity = 2 FROM product_stock WHERE product_id = :product_id),
    'failed movement leaves stock unchanged');
INSERT INTO expense_categories(name) VALUES ('Test rent') RETURNING id AS expense_category_id \gset
INSERT INTO expenses(category_id, amount, method, description, created_by)
VALUES (:expense_category_id, 100, 'CASH', 'Rent', :user_id) RETURNING id AS expense_id \gset
INSERT INTO expenses(category_id, amount, method, description, created_by, reversal_of_id)
VALUES (:expense_category_id, 100, 'CASH', 'Rent reversal', :user_id, :expense_id);
SELECT pg_temp.assert_true((SELECT sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END) = 0
    FROM expenses WHERE category_id = :expense_category_id), 'expense reversal');
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

-- Negative posting paths and deferred commit protection run in a savepoint.
SAVEPOINT invalid_documents;
INSERT INTO sales(document_number, request_key, seller_id, created_by)
VALUES ('TEST-EMPTY', '00000000-0000-0000-0000-000000000003', :employee_id, :user_id)
RETURNING id AS invalid_sale_id \gset
SELECT pg_temp.expect_error(format('UPDATE sales SET status = ''POSTED'', posted_at = now(), posted_by = %s
    WHERE id = %s', :user_id, :invalid_sale_id), 'empty sale');
INSERT INTO payments(sale_id, method, amount, created_by) VALUES (:invalid_sale_id, 'CASH', 1, :user_id);
SELECT pg_temp.expect_error('SET CONSTRAINTS ALL IMMEDIATE', 'must be POSTED before commit');
ROLLBACK TO SAVEPOINT invalid_documents;

SAVEPOINT excessive_return;
INSERT INTO sale_returns(document_number, request_key, sale_id, reason, created_by)
VALUES ('TEST-R2', '00000000-0000-0000-0000-000000000004', :sale_id, 'Too many items', :user_id)
RETURNING id AS invalid_return_id \gset
INSERT INTO return_items(return_id, sale_id, sale_item_id, product_id, quantity, refund_amount, restock)
VALUES (:invalid_return_id, :sale_id, :sale_item_id, :product_id, 2, 380, false);
SELECT pg_temp.expect_error(format('UPDATE sale_returns SET status = ''POSTED'', posted_at = now(), posted_by = %s
    WHERE id = %s', :user_id, :invalid_return_id), 'exceeds sold quantity or original amount');
ROLLBACK TO SAVEPOINT excessive_return;
SET CONSTRAINTS ALL IMMEDIATE;
SELECT 'All accounting smoke checks passed' AS result;
ROLLBACK;
