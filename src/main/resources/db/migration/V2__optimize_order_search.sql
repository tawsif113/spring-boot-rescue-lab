CREATE INDEX idx_purchase_orders_created_at_id
    ON purchase_orders (created_at DESC, id DESC);

COMMENT ON INDEX idx_purchase_orders_created_at_id IS
    'INC-001: supports deterministic newest-first order pagination';
