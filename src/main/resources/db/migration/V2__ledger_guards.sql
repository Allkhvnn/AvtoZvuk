CREATE FUNCTION reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; record a compensating operation', TG_TABLE_NAME;
END;
$$;

DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['stock_movements', 'stock_adjustments', 'bonus_entries',
        'salary_payments', 'expenses', 'payments', 'audit_log'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION reject_ledger_change()', table_name);
    END LOOP;
END;
$$;

CREATE FUNCTION guard_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Documents cannot be deleted; cancel the draft or post a return';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'A document must start as DRAFT';
        END IF;
    ELSIF OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'A finalized document cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER guard_purchase BEFORE INSERT OR UPDATE OR DELETE ON purchases
FOR EACH ROW EXECUTE FUNCTION guard_document();
CREATE TRIGGER guard_sale BEFORE INSERT OR UPDATE OR DELETE ON sales
FOR EACH ROW EXECUTE FUNCTION guard_document();
CREATE TRIGGER guard_return BEFORE INSERT OR UPDATE OR DELETE ON sale_returns
FOR EACH ROW EXECUTE FUNCTION guard_document();

-- The parent lock serializes line edits with document posting.
CREATE FUNCTION guard_document_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_id bigint;
    parent_status text;
    row_data jsonb;
BEGIN
    IF TG_OP = 'UPDATE' AND to_jsonb(OLD)->>TG_ARGV[1] IS DISTINCT FROM to_jsonb(NEW)->>TG_ARGV[1] THEN
        RAISE EXCEPTION 'A line cannot be moved to another document';
    END IF;
    IF TG_OP = 'DELETE' THEN row_data := to_jsonb(OLD); ELSE row_data := to_jsonb(NEW); END IF;
    parent_id := (row_data->>TG_ARGV[1])::bigint;
    EXECUTE format('SELECT status FROM %I WHERE id = $1 FOR SHARE', TG_ARGV[0])
        INTO parent_status USING parent_id;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Lines can only be changed on a draft document';
    END IF;
    IF TG_OP <> 'INSERT' THEN
        IF (TG_TABLE_NAME = 'purchase_items' AND EXISTS (
                SELECT 1 FROM stock_movements WHERE purchase_item_id = OLD.id))
            OR (TG_TABLE_NAME = 'sale_items' AND EXISTS (
                SELECT 1 FROM stock_movements WHERE sale_item_id = OLD.id))
            OR (TG_TABLE_NAME = 'return_items' AND EXISTS (
                SELECT 1 FROM stock_movements WHERE return_item_id = OLD.id)) THEN
            RAISE EXCEPTION 'A line with stock movements cannot be changed';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END;
$$;

CREATE TRIGGER guard_purchase_item BEFORE INSERT OR UPDATE OR DELETE ON purchase_items
FOR EACH ROW EXECUTE FUNCTION guard_document_item('purchases', 'purchase_id');
CREATE TRIGGER guard_sale_item BEFORE INSERT OR UPDATE OR DELETE ON sale_items
FOR EACH ROW EXECUTE FUNCTION guard_document_item('sales', 'sale_id');
CREATE TRIGGER guard_return_item BEFORE INSERT OR UPDATE OR DELETE ON return_items
FOR EACH ROW EXECUTE FUNCTION guard_document_item('sale_returns', 'return_id');

CREATE FUNCTION guard_stock_lot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Stock lots cannot be deleted'; END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.quantity_remaining <> 0 THEN RAISE EXCEPTION 'Receive stock through movements'; END IF;
    ELSE
        IF (to_jsonb(NEW) - 'quantity_remaining') IS DISTINCT FROM (to_jsonb(OLD) - 'quantity_remaining')
            OR pg_trigger_depth() < 2 THEN
            RAISE EXCEPTION 'Stock lots can only be changed by stock movements';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_lot BEFORE INSERT OR UPDATE OR DELETE ON stock_lots
FOR EACH ROW EXECUTE FUNCTION guard_stock_lot();

CREATE FUNCTION apply_stock_movement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    lot stock_lots%ROWTYPE;
    original stock_movements%ROWTYPE;
    purchased purchase_items%ROWTYPE;
    sold sale_items%ROWTYPE;
    returned return_items%ROWTYPE;
    already_moved bigint;
BEGIN
    -- Consistent lot locking prevents two sellers from spending the same stock.
    SELECT * INTO STRICT lot FROM stock_lots WHERE id = NEW.lot_id FOR UPDATE;
    IF lot.product_id <> NEW.product_id THEN RAISE EXCEPTION 'Lot product mismatch'; END IF;
    IF NEW.type = 'PURCHASE' THEN
        SELECT * INTO STRICT purchased FROM purchase_items WHERE id = NEW.purchase_item_id FOR UPDATE;
        IF lot.purchase_item_id IS DISTINCT FROM purchased.id OR lot.unit_cost <> purchased.unit_cost
            OR NEW.quantity <> purchased.quantity THEN
            RAISE EXCEPTION 'Receipt must match its purchase line and cost';
        END IF;
    ELSIF NEW.type = 'SALE' THEN
        SELECT * INTO STRICT sold FROM sale_items WHERE id = NEW.sale_item_id FOR UPDATE;
        SELECT COALESCE(-sum(quantity), 0) INTO already_moved FROM stock_movements
            WHERE sale_item_id = sold.id AND type = 'SALE';
        IF already_moved - NEW.quantity > sold.quantity THEN
            RAISE EXCEPTION 'Stock issued exceeds sale quantity';
        END IF;
    ELSIF NEW.type = 'RETURN' THEN
        SELECT * INTO STRICT original FROM stock_movements WHERE id = NEW.original_movement_id FOR UPDATE;
        SELECT * INTO STRICT returned FROM return_items WHERE id = NEW.return_item_id FOR UPDATE;
        IF original.type <> 'SALE' OR original.lot_id <> NEW.lot_id
            OR original.sale_item_id <> returned.sale_item_id OR NOT returned.restock THEN
            RAISE EXCEPTION 'Return must restore the original sale lot';
        END IF;
        SELECT COALESCE(sum(quantity), 0) INTO already_moved FROM stock_movements
            WHERE original_movement_id = original.id;
        IF already_moved + NEW.quantity > -original.quantity THEN
            RAISE EXCEPTION 'Returned stock exceeds the original issue';
        END IF;
        SELECT COALESCE(sum(quantity), 0) INTO already_moved FROM stock_movements
            WHERE return_item_id = returned.id;
        IF already_moved + NEW.quantity > returned.quantity THEN
            RAISE EXCEPTION 'Restocked quantity exceeds the return line';
        END IF;
    END IF;
    UPDATE stock_lots SET quantity_remaining = quantity_remaining + NEW.quantity
        WHERE id = NEW.lot_id AND quantity_remaining::bigint + NEW.quantity >= 0;
    IF NOT FOUND THEN RAISE EXCEPTION 'Insufficient stock in lot %', NEW.lot_id; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER apply_movement BEFORE INSERT ON stock_movements
FOR EACH ROW EXECUTE FUNCTION apply_stock_movement();

CREATE FUNCTION validate_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original_row jsonb;
BEGIN
    IF NEW.reversal_of_id IS NULL THEN RETURN NEW; END IF;
    EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE id = $1 FOR UPDATE', TG_TABLE_NAME)
        INTO original_row USING NEW.reversal_of_id;
    IF original_row IS NULL OR original_row->>'reversal_of_id' IS NOT NULL
        OR (original_row->>'amount')::numeric <> NEW.amount THEN
        RAISE EXCEPTION 'Reversal must match an original unreversed entry';
    END IF;
    IF TG_TABLE_NAME = 'salary_payments' THEN
        IF (original_row->>'employee_id')::bigint <> NEW.employee_id
            OR original_row->>'type' <> NEW.type THEN
            RAISE EXCEPTION 'Reversal employee and payment type must match';
        END IF;
    ELSE
        IF (original_row->>'category_id')::bigint <> NEW.category_id THEN
            RAISE EXCEPTION 'Reversal expense category must match';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER validate_salary_reversal BEFORE INSERT ON salary_payments
FOR EACH ROW EXECUTE FUNCTION validate_reversal();
CREATE TRIGGER validate_expense_reversal BEFORE INSERT ON expenses
FOR EACH ROW EXECUTE FUNCTION validate_reversal();
