ALTER TABLE stock_adjustments
    ADD COLUMN operation_type varchar(20) NOT NULL DEFAULT 'OPENING'
    CHECK (operation_type IN ('OPENING', 'INVENTORY', 'WRITE_OFF'));

CREATE TABLE stock_adjustment_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    adjustment_id bigint NOT NULL REFERENCES stock_adjustments(id),
    product_id bigint NOT NULL REFERENCES products(id),
    system_quantity bigint NOT NULL CHECK (system_quantity >= 0),
    actual_quantity bigint NOT NULL CHECK (actual_quantity >= 0),
    difference bigint GENERATED ALWAYS AS (actual_quantity - system_quantity) STORED,
    unit_cost numeric(19,2) CHECK (unit_cost >= 0),
    UNIQUE (adjustment_id, product_id),
    CHECK ((actual_quantity > system_quantity AND unit_cost IS NOT NULL)
        OR (actual_quantity <= system_quantity AND unit_cost IS NULL))
);
CREATE INDEX ix_adjustment_items_product ON stock_adjustment_items(product_id, adjustment_id);

CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE ON stock_adjustment_items
FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();
