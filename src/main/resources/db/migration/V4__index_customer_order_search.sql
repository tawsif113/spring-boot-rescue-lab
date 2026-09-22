CREATE INDEX idx_purchase_orders_customer_created_at_id
    ON purchase_orders (customer_id, created_at DESC, id DESC);

COMMENT ON INDEX idx_purchase_orders_customer_created_at_id IS
    'INC-004: supports ownership-scoped newest-first order pagination';
