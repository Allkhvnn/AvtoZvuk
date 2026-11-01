-- Nullable for documents recorded before the API existed.
ALTER TABLE purchases ADD COLUMN request_key uuid UNIQUE;
ALTER TABLE purchases ADD COLUMN request_fingerprint varchar(64);
ALTER TABLE sales ADD COLUMN request_fingerprint varchar(64);
ALTER TABLE sale_returns ADD COLUMN request_fingerprint varchar(64);
ALTER TABLE stock_adjustments ADD COLUMN request_key uuid UNIQUE;
ALTER TABLE stock_adjustments ADD COLUMN request_fingerprint varchar(64);
