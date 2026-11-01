ALTER TABLE expenses ADD COLUMN request_key uuid UNIQUE;
ALTER TABLE expenses ADD COLUMN request_fingerprint varchar(64);

ALTER TABLE salary_payments ADD COLUMN request_key uuid UNIQUE;
ALTER TABLE salary_payments ADD COLUMN request_fingerprint varchar(64);

ALTER TABLE bonus_entries ADD COLUMN request_key uuid UNIQUE;
ALTER TABLE bonus_entries ADD COLUMN request_fingerprint varchar(64);

CREATE INDEX ix_users_employee_active ON app_users(employee_id, active);
