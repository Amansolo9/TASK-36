ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_staff_id BIGINT REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_order_assigned_staff ON orders(assigned_staff_id);
