CREATE TABLE idempotency_keys (
    key_hash VARCHAR(64) PRIMARY KEY,
    response_status INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_idem_created ON idempotency_keys(created_at);
