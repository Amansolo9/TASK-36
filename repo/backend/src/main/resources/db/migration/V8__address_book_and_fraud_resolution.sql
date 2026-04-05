-- M5: Address book management
CREATE TABLE addresses (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    label      VARCHAR(100) NOT NULL,
    street     TEXT         NOT NULL,
    city       VARCHAR(100) NOT NULL,
    state      VARCHAR(50)  NOT NULL,
    zip_code   VARCHAR(10)  NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_address_user ON addresses(user_id);

-- M6: Supervisor note resolution for fraud alerts
ALTER TABLE fraud_alerts ADD COLUMN resolver_note TEXT;
ALTER TABLE fraud_alerts ADD COLUMN resolved_by_id BIGINT REFERENCES users(id);
ALTER TABLE fraud_alerts ADD COLUMN resolved_at TIMESTAMPTZ;
