CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    available_stock INTEGER NOT NULL CHECK (available_stock >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- The fragile baseline intentionally omits indexes on purchase_orders.customer_id
-- and purchase_orders.created_at. INC-001 will diagnose and correct that choice.

