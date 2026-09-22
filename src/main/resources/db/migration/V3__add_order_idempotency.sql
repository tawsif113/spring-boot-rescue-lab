CREATE TABLE order_idempotency_records (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES purchase_orders (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_order_idempotency_customer_key UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_order_idempotency_expires_at
    ON order_idempotency_records (expires_at);
