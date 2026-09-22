\set ON_ERROR_STOP on

BEGIN;

TRUNCATE TABLE order_items, purchase_orders, products;

INSERT INTO products (id, sku, name, unit_price, available_stock, created_at, updated_at)
SELECT
    md5('product-' || product_number)::uuid,
    'LOAD-' || lpad(product_number::text, 4, '0'),
    'Load-test product ' || product_number,
    19.99,
    100000,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM generate_series(1, 1000) AS product_number;

INSERT INTO purchase_orders (id, customer_id, status, total_amount, created_at)
SELECT
    md5('order-' || order_number)::uuid,
    md5('customer-' || (order_number % 250))::uuid,
    'CREATED',
    59.97,
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + order_number * INTERVAL '1 second'
FROM generate_series(1, 10000) AS order_number;

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price)
SELECT
    md5('item-' || order_number || '-' || line_number)::uuid,
    md5('order-' || order_number)::uuid,
    md5('product-' || (((order_number + line_number) % 1000) + 1))::uuid,
    1,
    19.99
FROM generate_series(1, 10000) AS order_number
CROSS JOIN generate_series(1, 3) AS line_number;

COMMIT;

ANALYZE products;
ANALYZE purchase_orders;
ANALYZE order_items;
