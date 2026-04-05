-- Server-side shift schedule for check-in validation
CREATE TABLE shift_assignments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    site_id     BIGINT NOT NULL REFERENCES organizations(id),
    shift_date  DATE NOT NULL,
    shift_start TIME NOT NULL,
    shift_end   TIME NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shift_user_date ON shift_assignments(user_id, shift_date);
CREATE INDEX idx_shift_site_date ON shift_assignments(site_id, shift_date);
CREATE UNIQUE INDEX idx_shift_unique ON shift_assignments(user_id, site_id, shift_date);

-- Add team binding to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES organizations(id);
CREATE INDEX IF NOT EXISTS idx_users_team ON users(team_id);

-- Add device binding table for multi-device tracking
CREATE TABLE device_bindings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    device_hash     TEXT NOT NULL,
    device_label    VARCHAR(100),
    bound_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT unique_user_device UNIQUE (user_id, device_hash)
);
CREATE INDEX idx_device_user ON device_bindings(user_id);
