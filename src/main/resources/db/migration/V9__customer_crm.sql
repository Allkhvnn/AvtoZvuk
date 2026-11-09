CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT NOT NULL REFERENCES app_users(id),
    updated_by BIGINT NOT NULL REFERENCES app_users(id)
);

CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_name ON customers(full_name);

CREATE TABLE customer_vehicles (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    production_year INTEGER CHECK (production_year BETWEEN 1900 AND 2100),
    plate_number VARCHAR(30),
    notes VARCHAR(500)
);

CREATE INDEX idx_customer_vehicles_customer ON customer_vehicles(customer_id);
CREATE INDEX idx_customer_vehicles_plate ON customer_vehicles(plate_number);

ALTER TABLE sales ADD COLUMN customer_id BIGINT REFERENCES customers(id);
CREATE INDEX idx_sales_customer ON sales(customer_id, sold_at DESC);
