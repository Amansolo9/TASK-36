CREATE TABLE pickup_redemption_log (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id),
    verifier_id  BIGINT NOT NULL REFERENCES users(id),
    outcome      VARCHAR(50) NOT NULL,
    reason       TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_redemption_order ON pickup_redemption_log(order_id);
