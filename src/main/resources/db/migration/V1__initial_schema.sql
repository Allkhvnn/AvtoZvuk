-- Money is KZT. Quantities are whole pieces. All event times are absolute instants.
CREATE TABLE employees (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name varchar(200) NOT NULL CHECK (btrim(full_name) <> ''),
    phone varchar(40),
    bonus_rate numeric(5,4) NOT NULL DEFAULT 0.0500 CHECK (bonus_rate BETWEEN 0 AND 1),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id bigint UNIQUE REFERENCES employees(id),
    username varchar(100) NOT NULL CHECK (btrim(username) <> ''),
    password_hash text NOT NULL CHECK (btrim(password_hash) <> ''),
    role varchar(20) NOT NULL CHECK (role IN ('OWNER', 'SELLER')),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_username ON app_users (lower(username));

CREATE TABLE categories (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(150) NOT NULL CHECK (btrim(name) <> ''),
    active boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX uq_categories_name ON categories (lower(name));

CREATE TABLE suppliers (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    phone varchar(40),
    notes text,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE products (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id bigint REFERENCES categories(id),
    name varchar(250) NOT NULL CHECK (btrim(name) <> ''),
    sku varchar(100) NOT NULL CHECK (btrim(sku) <> ''),
    barcode varchar(100) UNIQUE CHECK (btrim(barcode) <> ''),
    selling_price numeric(19,2) NOT NULL CHECK (selling_price >= 0),
    low_stock_threshold integer NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_products_sku ON products (lower(sku));
CREATE INDEX ix_products_category ON products(category_id);

CREATE TABLE purchases (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_number varchar(100) NOT NULL UNIQUE,
    supplier_id bigint NOT NULL REFERENCES suppliers(id),
    supplier_document_number varchar(100),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    purchased_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES app_users(id),
    posted_by bigint REFERENCES app_users(id),
    posted_at timestamptz,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'POSTED') = (posted_at IS NOT NULL AND posted_by IS NOT NULL))
);

CREATE TABLE purchase_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id bigint NOT NULL REFERENCES purchases(id),
    product_id bigint NOT NULL REFERENCES products(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_cost numeric(19,2) NOT NULL CHECK (unit_cost >= 0),
    line_total numeric(19,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
    UNIQUE (id, product_id)
);
CREATE INDEX ix_purchase_items_purchase ON purchase_items(purchase_id);

CREATE TABLE sales (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_number varchar(100) NOT NULL UNIQUE,
    request_key uuid NOT NULL UNIQUE,
    seller_id bigint NOT NULL REFERENCES employees(id),
    bonus_rate numeric(5,4) NOT NULL DEFAULT 0.0500 CHECK (bonus_rate BETWEEN 0 AND 1),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    sold_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES app_users(id),
    posted_by bigint REFERENCES app_users(id),
    posted_at timestamptz,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'POSTED') = (posted_at IS NOT NULL AND posted_by IS NOT NULL))
);
CREATE INDEX ix_sales_sold_at ON sales(sold_at) WHERE status = 'POSTED';
CREATE INDEX ix_sales_seller_date ON sales(seller_id, sold_at);

CREATE TABLE sale_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id bigint NOT NULL REFERENCES sales(id),
    product_id bigint NOT NULL REFERENCES products(id),
    product_name varchar(250) NOT NULL,
    product_sku varchar(100) NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(19,2) NOT NULL CHECK (unit_price >= 0),
    discount_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    line_total numeric(19,2) GENERATED ALWAYS AS (quantity * unit_price - discount_amount) STORED,
    CHECK (discount_amount <= quantity * unit_price),
    UNIQUE (id, sale_id, product_id)
);
CREATE INDEX ix_sale_items_sale ON sale_items(sale_id);
CREATE INDEX ix_sale_items_product ON sale_items(product_id);

CREATE TABLE sale_returns (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_number varchar(100) NOT NULL UNIQUE,
    request_key uuid NOT NULL UNIQUE,
    sale_id bigint NOT NULL REFERENCES sales(id),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    returned_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES app_users(id),
    posted_by bigint REFERENCES app_users(id),
    posted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'POSTED') = (posted_at IS NOT NULL AND posted_by IS NOT NULL)),
    UNIQUE (id, sale_id)
);
CREATE INDEX ix_returns_sale ON sale_returns(sale_id);
CREATE INDEX ix_returns_date ON sale_returns(returned_at) WHERE status = 'POSTED';

CREATE TABLE return_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_id bigint NOT NULL,
    sale_id bigint NOT NULL,
    sale_item_id bigint NOT NULL,
    product_id bigint NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    refund_amount numeric(19,2) NOT NULL CHECK (refund_amount >= 0),
    restock boolean NOT NULL DEFAULT true,
    FOREIGN KEY (return_id, sale_id) REFERENCES sale_returns(id, sale_id),
    FOREIGN KEY (sale_item_id, sale_id, product_id) REFERENCES sale_items(id, sale_id, product_id),
    UNIQUE (return_id, sale_item_id),
    UNIQUE (id, product_id)
);
CREATE INDEX ix_return_items_sale_item ON return_items(sale_item_id);

CREATE TABLE payments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id bigint REFERENCES sales(id),
    return_id bigint REFERENCES sale_returns(id),
    method varchar(20) NOT NULL CHECK (method IN ('CASH', 'KASPI_PAY')),
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    paid_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES app_users(id),
    reference varchar(200),
    CHECK (num_nonnulls(sale_id, return_id) = 1)
);
CREATE INDEX ix_payments_sale ON payments(sale_id);
CREATE INDEX ix_payments_return ON payments(return_id);
CREATE INDEX ix_payments_date ON payments(paid_at);

CREATE TABLE stock_adjustments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_by bigint NOT NULL REFERENCES app_users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Opening stock also has a lot and a known unit cost; never treat it as a free purchase.
CREATE TABLE stock_lots (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL REFERENCES products(id),
    purchase_item_id bigint UNIQUE,
    opening_adjustment_id bigint REFERENCES stock_adjustments(id),
    unit_cost numeric(19,2) NOT NULL CHECK (unit_cost >= 0),
    received_at timestamptz NOT NULL DEFAULT now(),
    quantity_remaining integer NOT NULL DEFAULT 0 CHECK (quantity_remaining >= 0),
    FOREIGN KEY (purchase_item_id, product_id) REFERENCES purchase_items(id, product_id),
    CHECK (num_nonnulls(purchase_item_id, opening_adjustment_id) = 1),
    UNIQUE (id, product_id)
);
CREATE INDEX ix_lots_fifo ON stock_lots(product_id, received_at, id) WHERE quantity_remaining > 0;

CREATE TABLE stock_movements (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL REFERENCES products(id),
    lot_id bigint NOT NULL,
    type varchar(30) NOT NULL CHECK (type IN ('PURCHASE', 'SALE', 'RETURN', 'ADJUSTMENT')),
    quantity integer NOT NULL CHECK (quantity <> 0),
    purchase_item_id bigint,
    sale_item_id bigint,
    sale_id bigint,
    return_item_id bigint,
    adjustment_id bigint REFERENCES stock_adjustments(id),
    original_movement_id bigint REFERENCES stock_movements(id),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_by bigint NOT NULL REFERENCES app_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (lot_id, product_id) REFERENCES stock_lots(id, product_id),
    FOREIGN KEY (purchase_item_id, product_id) REFERENCES purchase_items(id, product_id),
    FOREIGN KEY (sale_item_id, sale_id, product_id) REFERENCES sale_items(id, sale_id, product_id),
    FOREIGN KEY (return_item_id, product_id) REFERENCES return_items(id, product_id),
    CHECK ((sale_item_id IS NULL) = (sale_id IS NULL)),
    CHECK (
        (type = 'PURCHASE' AND quantity > 0 AND purchase_item_id IS NOT NULL
            AND num_nonnulls(sale_item_id, return_item_id, adjustment_id, original_movement_id) = 0)
        OR (type = 'SALE' AND quantity < 0 AND sale_item_id IS NOT NULL
            AND num_nonnulls(purchase_item_id, return_item_id, adjustment_id, original_movement_id) = 0)
        OR (type = 'RETURN' AND quantity > 0 AND return_item_id IS NOT NULL AND original_movement_id IS NOT NULL
            AND num_nonnulls(purchase_item_id, sale_item_id, adjustment_id) = 0)
        OR (type = 'ADJUSTMENT' AND adjustment_id IS NOT NULL
            AND num_nonnulls(purchase_item_id, sale_item_id, return_item_id, original_movement_id) = 0)
    )
);
CREATE INDEX ix_movements_product_date ON stock_movements(product_id, created_at);
CREATE INDEX ix_movements_lot ON stock_movements(lot_id);
CREATE INDEX ix_movements_sale_item ON stock_movements(sale_item_id);
CREATE INDEX ix_movements_return_item ON stock_movements(return_item_id);
CREATE INDEX ix_movements_original ON stock_movements(original_movement_id);
CREATE UNIQUE INDEX uq_purchase_receipt ON stock_movements(purchase_item_id) WHERE type = 'PURCHASE';

CREATE TABLE expense_categories (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(150) NOT NULL UNIQUE
);

CREATE TABLE expenses (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id bigint NOT NULL REFERENCES expense_categories(id),
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    method varchar(20) NOT NULL CHECK (method IN ('CASH', 'KASPI_PAY', 'BANK_TRANSFER')),
    description text NOT NULL CHECK (btrim(description) <> ''),
    incurred_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL REFERENCES app_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    reversal_of_id bigint UNIQUE REFERENCES expenses(id)
);
CREATE INDEX ix_expenses_date ON expenses(incurred_at);

-- Signed ledger: accrual positive, return correction negative, manual adjustment either sign.
CREATE TABLE bonus_entries (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id bigint NOT NULL REFERENCES employees(id),
    type varchar(30) NOT NULL CHECK (type IN ('SALE', 'RETURN', 'ADJUSTMENT')),
    sale_id bigint REFERENCES sales(id),
    return_id bigint REFERENCES sale_returns(id),
    amount numeric(19,2) NOT NULL,
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_by bigint NOT NULL REFERENCES app_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (type = 'SALE' AND sale_id IS NOT NULL AND return_id IS NULL AND amount >= 0)
        OR (type = 'RETURN' AND return_id IS NOT NULL AND sale_id IS NULL AND amount <= 0)
        OR (type = 'ADJUSTMENT' AND sale_id IS NULL AND return_id IS NULL AND amount <> 0)
    )
);
CREATE UNIQUE INDEX uq_bonus_sale ON bonus_entries(sale_id) WHERE type = 'SALE';
CREATE UNIQUE INDEX uq_bonus_return ON bonus_entries(return_id) WHERE type = 'RETURN';
CREATE INDEX ix_bonus_employee_date ON bonus_entries(employee_id, created_at);

CREATE TABLE salary_payments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id bigint NOT NULL REFERENCES employees(id),
    type varchar(20) NOT NULL CHECK (type IN ('BONUS', 'SALARY')),
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    method varchar(20) NOT NULL CHECK (method IN ('CASH', 'KASPI_PAY', 'BANK_TRANSFER')),
    paid_at timestamptz NOT NULL DEFAULT now(),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_by bigint NOT NULL REFERENCES app_users(id),
    reversal_of_id bigint UNIQUE REFERENCES salary_payments(id)
);
CREATE INDEX ix_salary_employee_date ON salary_payments(employee_id, paid_at);

CREATE TABLE audit_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id bigint NOT NULL REFERENCES app_users(id),
    action varchar(100) NOT NULL,
    entity_type varchar(100) NOT NULL,
    entity_id bigint NOT NULL,
    reason text,
    old_data jsonb,
    new_data jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log(entity_type, entity_id, created_at);
CREATE INDEX ix_audit_actor_date ON audit_log(actor_user_id, created_at);

CREATE VIEW product_stock AS
SELECT p.id AS product_id, COALESCE(sum(l.quantity_remaining), 0)::bigint AS quantity,
       COALESCE(sum(l.quantity_remaining * l.unit_cost), 0)::numeric(19,2) AS stock_cost,
       COALESCE(sum(l.quantity_remaining), 0) <= p.low_stock_threshold AS low_stock
FROM products p LEFT JOIN stock_lots l ON l.product_id = p.id
GROUP BY p.id;

CREATE VIEW employee_bonus_balances AS
SELECT e.id AS employee_id,
       COALESCE(b.accrued, 0) AS accrued,
       COALESCE(p.paid, 0) AS paid,
       COALESCE(b.accrued, 0) - COALESCE(p.paid, 0) AS balance
FROM employees e
LEFT JOIN (SELECT employee_id, sum(amount) AS accrued FROM bonus_entries GROUP BY employee_id) b
    ON b.employee_id = e.id
LEFT JOIN (
    SELECT employee_id, sum(CASE WHEN reversal_of_id IS NULL THEN amount ELSE -amount END) AS paid
    FROM salary_payments WHERE type = 'BONUS' GROUP BY employee_id
) p ON p.employee_id = e.id;
