-- V31: Idempotency state machine - only persist successful outcomes
-- Failed/transient responses (401, 403, 5xx) must NOT poison the key.
ALTER TABLE idempotency_keys ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED';
ALTER TABLE idempotency_keys ADD COLUMN response_body TEXT;

-- Migrate existing rows: they were all stored as successes under old logic
-- (best-effort; old filter stored everything, but we treat existing rows as SUCCEEDED)
UPDATE idempotency_keys SET state = 'SUCCEEDED' WHERE state = 'SUCCEEDED';

-- Index for cleanup of stale IN_PROGRESS rows (crash recovery)
CREATE INDEX idx_idem_state ON idempotency_keys(state);
