\set ON_ERROR_STOP on

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id
FROM purchase_orders
WHERE customer_id = '11111111-1111-1111-1111-111111111111'::uuid
ORDER BY created_at DESC, id DESC
LIMIT 100;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DISTINCT
    purchase_order.id,
    order_item.id,
    product.id
FROM purchase_orders AS purchase_order
LEFT JOIN order_items AS order_item
    ON order_item.order_id = purchase_order.id
LEFT JOIN products AS product
    ON product.id = order_item.product_id
WHERE purchase_order.id = ANY (
    SELECT id
    FROM purchase_orders
    WHERE customer_id = '11111111-1111-1111-1111-111111111111'::uuid
    ORDER BY created_at DESC, id DESC
    LIMIT 100
);
