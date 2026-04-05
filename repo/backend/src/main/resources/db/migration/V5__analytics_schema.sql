-- Analytics events
CREATE TABLE analytics_events (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      REFERENCES users(id),
    site_id    BIGINT      REFERENCES organizations(id),
    event_type VARCHAR(30) NOT NULL,
    target     VARCHAR(200),
    metadata   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_type    ON analytics_events(event_type);
CREATE INDEX idx_event_site    ON analytics_events(site_id);
CREATE INDEX idx_event_created ON analytics_events(created_at);

-- Experiments (A/B and Bandit)
CREATE TABLE experiments (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    type          VARCHAR(20)  NOT NULL CHECK (type IN ('AB_TEST','BANDIT')),
    variant_count INT          NOT NULL DEFAULT 2,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    description   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Materialized views for funnel analytics
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_site_daily_metrics AS
SELECT
    site_id,
    DATE(created_at) AS event_date,
    event_type,
    COUNT(*)         AS event_count,
    COUNT(DISTINCT user_id) AS unique_users
FROM analytics_events
WHERE site_id IS NOT NULL
GROUP BY site_id, DATE(created_at), event_type;

CREATE UNIQUE INDEX idx_mv_site_daily ON mv_site_daily_metrics(site_id, event_date, event_type);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_conversion_funnel AS
SELECT
    site_id,
    DATE(created_at) AS event_date,
    COUNT(*) FILTER (WHERE event_type = 'PAGE_VIEW')           AS views,
    COUNT(*) FILTER (WHERE event_type = 'CLICK')               AS clicks,
    COUNT(*) FILTER (WHERE event_type = 'CONVERSION')          AS conversions,
    COUNT(*) FILTER (WHERE event_type = 'SATISFACTION_RATING') AS ratings
FROM analytics_events
WHERE site_id IS NOT NULL
GROUP BY site_id, DATE(created_at);

CREATE UNIQUE INDEX idx_mv_funnel ON mv_conversion_funnel(site_id, event_date);
