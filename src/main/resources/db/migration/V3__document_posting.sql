-- Posting is the final step of a single backend transaction.
CREATE FUNCTION guard_payment_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status text;
BEGIN
    IF NEW.sale_id IS NOT NULL THEN
        SELECT status INTO parent_status FROM sales WHERE id = NEW.sale_id FOR SHARE;
    ELSE
        SELECT status INTO parent_status FROM sale_returns WHERE id = NEW.return_id FOR SHARE;
    END IF;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Payments must be recorded before posting';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_payment BEFORE INSERT ON payments
FOR EACH ROW EXECUTE FUNCTION guard_payment_insert();

CREATE FUNCTION guard_movement_document() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status text;
BEGIN
    IF NEW.type = 'PURCHASE' THEN
        SELECT p.status INTO parent_status FROM purchases p JOIN purchase_items i ON i.purchase_id = p.id
            WHERE i.id = NEW.purchase_item_id FOR SHARE OF p;
    ELSIF NEW.type = 'SALE' THEN
        SELECT status INTO parent_status FROM sales WHERE id = NEW.sale_id FOR SHARE;
    ELSIF NEW.type = 'RETURN' THEN
        SELECT r.status INTO parent_status FROM sale_returns r JOIN return_items i ON i.return_id = r.id
            WHERE i.id = NEW.return_item_id FOR SHARE OF r;
    ELSE
        RETURN NEW;
    END IF;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Stock movements must be recorded before posting';
    END IF;
    RETURN NEW;
END;
$$;
-- PostgreSQL runs same-event triggers alphabetically: validate before applying.
CREATE TRIGGER aa_guard_movement BEFORE INSERT ON stock_movements
FOR EACH ROW EXECUTE FUNCTION guard_movement_document();

CREATE FUNCTION validate_document_posting() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    total numeric;
    original_status text;
BEGIN
    IF NEW.status <> 'POSTED' THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME = 'purchases' THEN
        IF NOT EXISTS (SELECT 1 FROM purchase_items WHERE purchase_id = NEW.id) THEN
            RAISE EXCEPTION 'Cannot post an empty purchase';
        END IF;
        IF EXISTS (
            SELECT 1 FROM purchase_items i WHERE i.purchase_id = NEW.id
            AND i.quantity <> COALESCE((SELECT sum(m.quantity) FROM stock_movements m
                WHERE m.purchase_item_id = i.id), 0)
        ) THEN RAISE EXCEPTION 'Purchase stock receipt is incomplete'; END IF;
    ELSIF TG_TABLE_NAME = 'sales' THEN
        SELECT sum(line_total) INTO total FROM sale_items WHERE sale_id = NEW.id;
        IF total IS NULL THEN RAISE EXCEPTION 'Cannot post an empty sale'; END IF;
        IF EXISTS (
            SELECT 1 FROM sale_items i WHERE i.sale_id = NEW.id
            AND i.quantity <> COALESCE((SELECT -sum(m.quantity) FROM stock_movements m
                WHERE m.sale_item_id = i.id), 0)
        ) THEN RAISE EXCEPTION 'Sale stock issue is incomplete'; END IF;
        IF total <> COALESCE((SELECT sum(amount) FROM payments WHERE sale_id = NEW.id), 0) THEN
            RAISE EXCEPTION 'Sale payments do not match the discounted total';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM bonus_entries WHERE sale_id = NEW.id AND type = 'SALE'
            AND employee_id = NEW.seller_id AND amount = round(total * NEW.bonus_rate, 2)) THEN
            RAISE EXCEPTION 'Sale bonus does not match seller, rate and total';
        END IF;
    ELSE
        -- Serialize all returns for the same sale, including returns on different lots.
        SELECT status INTO original_status FROM sales WHERE id = NEW.sale_id FOR UPDATE;
        IF original_status IS DISTINCT FROM 'POSTED' THEN
            RAISE EXCEPTION 'Only posted sales may be returned';
        END IF;
        SELECT sum(refund_amount) INTO total FROM return_items WHERE return_id = NEW.id;
        IF total IS NULL THEN RAISE EXCEPTION 'Cannot post an empty return'; END IF;
        IF EXISTS (
            SELECT 1 FROM return_items current_item
            JOIN sale_items s ON s.id = current_item.sale_item_id
            WHERE current_item.return_id = NEW.id AND (
                current_item.quantity + COALESCE((
                    SELECT sum(i.quantity) FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                    WHERE i.sale_item_id = s.id AND r.status = 'POSTED'), 0) > s.quantity
                OR current_item.refund_amount + COALESCE((
                    SELECT sum(i.refund_amount) FROM return_items i JOIN sale_returns r ON r.id = i.return_id
                    WHERE i.sale_item_id = s.id AND r.status = 'POSTED'), 0) > s.line_total
            )
        ) THEN RAISE EXCEPTION 'Return exceeds sold quantity or original amount'; END IF;
        IF EXISTS (
            SELECT 1 FROM return_items i WHERE i.return_id = NEW.id AND i.restock
            AND i.quantity <> COALESCE((SELECT sum(m.quantity) FROM stock_movements m
                WHERE m.return_item_id = i.id), 0)
        ) THEN RAISE EXCEPTION 'Return stock receipt is incomplete'; END IF;
        IF total <> COALESCE((SELECT sum(amount) FROM payments WHERE return_id = NEW.id), 0) THEN
            RAISE EXCEPTION 'Refund payments do not match return total';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM bonus_entries b JOIN sales s ON s.id = NEW.sale_id
            WHERE b.return_id = NEW.id AND b.type = 'RETURN' AND b.employee_id = s.seller_id) THEN
            RAISE EXCEPTION 'Return bonus correction is missing';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER validate_posting BEFORE UPDATE ON purchases
FOR EACH ROW EXECUTE FUNCTION validate_document_posting();
CREATE TRIGGER validate_posting BEFORE UPDATE ON sales
FOR EACH ROW EXECUTE FUNCTION validate_document_posting();
CREATE TRIGGER validate_posting BEFORE UPDATE ON sale_returns
FOR EACH ROW EXECUTE FUNCTION validate_document_posting();

CREATE FUNCTION guard_bonus_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status text;
BEGIN
    IF NEW.type = 'SALE' THEN
        SELECT status INTO parent_status FROM sales WHERE id = NEW.sale_id FOR SHARE;
    ELSIF NEW.type = 'RETURN' THEN
        SELECT status INTO parent_status FROM sale_returns WHERE id = NEW.return_id FOR SHARE;
    ELSE
        RETURN NEW;
    END IF;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Document bonuses must be recorded before posting';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_bonus BEFORE INSERT ON bonus_entries
FOR EACH ROW EXECUTE FUNCTION guard_bonus_insert();

-- Reject commits that would leave money, bonuses or stock on a draft/cancelled
-- document. Deferred checks allow recording the journal before posting.
CREATE FUNCTION require_posted_document() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    data jsonb := to_jsonb(NEW);
    parent_status text;
BEGIN
    IF TG_TABLE_NAME = 'stock_movements' THEN
        CASE data->>'type'
        WHEN 'PURCHASE' THEN
            SELECT p.status INTO parent_status FROM purchases p JOIN purchase_items i ON i.purchase_id = p.id
                WHERE i.id = (data->>'purchase_item_id')::bigint;
        WHEN 'SALE' THEN
            SELECT status INTO parent_status FROM sales WHERE id = (data->>'sale_id')::bigint;
        WHEN 'RETURN' THEN
            SELECT r.status INTO parent_status FROM sale_returns r JOIN return_items i ON i.return_id = r.id
                WHERE i.id = (data->>'return_item_id')::bigint;
        ELSE RETURN NULL;
        END CASE;
    ELSE
        IF data->>'sale_id' IS NOT NULL THEN
            SELECT status INTO parent_status FROM sales WHERE id = (data->>'sale_id')::bigint;
        ELSIF data->>'return_id' IS NOT NULL THEN
            SELECT status INTO parent_status FROM sale_returns WHERE id = (data->>'return_id')::bigint;
        ELSE RETURN NULL;
        END IF;
    END IF;
    IF parent_status IS DISTINCT FROM 'POSTED' THEN
        RAISE EXCEPTION 'A document with ledger entries must be POSTED before commit';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER require_posted AFTER INSERT ON stock_movements
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_posted_document();
CREATE CONSTRAINT TRIGGER require_posted AFTER INSERT ON payments
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_posted_document();
CREATE CONSTRAINT TRIGGER require_posted AFTER INSERT ON bonus_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_posted_document();
