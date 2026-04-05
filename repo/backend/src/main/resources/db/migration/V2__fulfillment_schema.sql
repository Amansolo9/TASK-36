-- Check-ins (One-Tap)
CREATE TABLE check_ins (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users(id),
    site_id            BIGINT       NOT NULL REFERENCES organizations(id),
    timestamp          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    scheduled_time     TIMESTAMPTZ  NOT NULL,
    device_fingerprint VARCHAR(512),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('VALID','EARLY','LATE','FLAGGED')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkin_user      ON check_ins(user_id);
CREATE INDEX idx_checkin_site      ON check_ins(site_id);
CREATE INDEX idx_checkin_timestamp ON check_ins(timestamp);

-- Fraud alerts
CREATE TABLE fraud_alerts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    reason     VARCHAR(255) NOT NULL,
    details    TEXT         NOT NULL,
    resolved   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_user ON fraud_alerts(user_id);

-- Delivery zones
CREATE TABLE delivery_zones (
    id             BIGSERIAL PRIMARY KEY,
    site_id        BIGINT         NOT NULL REFERENCES organizations(id),
    zip_code       VARCHAR(10)    NOT NULL,
    distance_miles DOUBLE PRECISION NOT NULL,
    delivery_fee   NUMERIC(10,2)  NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_dz_site ON delivery_zones(site_id);
CREATE INDEX idx_dz_zip  ON delivery_zones(zip_code);

-- Orders
CREATE TABLE orders (
    id                       BIGSERIAL PRIMARY KEY,
    customer_id              BIGINT         NOT NULL REFERENCES users(id),
    site_id                  BIGINT         NOT NULL REFERENCES organizations(id),
    status                   VARCHAR(30)    NOT NULL CHECK (status IN (
        'PENDING','CONFIRMED','PREPARING','READY_FOR_PICKUP',
        'OUT_FOR_DELIVERY','DELIVERED','PICKED_UP','CANCELLED')),
    subtotal                 NUMERIC(10,2)  NOT NULL,
    delivery_fee             NUMERIC(10,2)  NOT NULL DEFAULT 0,
    total                    NUMERIC(10,2)  NOT NULL,
    delivery_zip             VARCHAR(10),
    delivery_distance_miles  DOUBLE PRECISION,
    is_pickup                BOOLEAN        NOT NULL DEFAULT FALSE,
    pickup_verification_code VARCHAR(6),
    pickup_verified          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_customer ON orders(customer_id);
CREATE INDEX idx_order_site     ON orders(site_id);
CREATE INDEX idx_order_status   ON orders(status);

-- Ratings (two-way: staff <-> customer)
CREATE TABLE ratings (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT      NOT NULL REFERENCES orders(id),
    rater_id        BIGINT      NOT NULL REFERENCES users(id),
    rated_user_id   BIGINT      NOT NULL REFERENCES users(id),
    target_type     VARCHAR(20) NOT NULL CHECK (target_type IN ('STAFF','CUSTOMER')),
    stars           INT         NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment         TEXT,
    appeal_status   VARCHAR(20) CHECK (appeal_status IN ('PENDING','IN_ARBITRATION','UPHELD','OVERTURNED','EXPIRED')),
    appeal_reason   TEXT,
    appeal_deadline TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_rating_per_order UNIQUE (order_id, rater_id)
);

CREATE INDEX idx_rating_order ON ratings(order_id);
CREATE INDEX idx_rating_rated ON ratings(rated_user_id);
