CREATE TABLE store_settings (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(40),
    address VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT REFERENCES app_users(id)
);

INSERT INTO store_settings(id, name) VALUES (1, 'AvtoZvuk');
