CREATE INDEX ix_return_items_return ON return_items(return_id);
CREATE INDEX ix_return_items_product ON return_items(product_id);
CREATE INDEX ix_bonus_created_at ON bonus_entries(created_at);
CREATE INDEX ix_salary_paid_at ON salary_payments(paid_at);
