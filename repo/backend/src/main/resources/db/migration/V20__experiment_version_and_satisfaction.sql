-- Add experiment versioning for backtracking
ALTER TABLE experiments ADD COLUMN version INT NOT NULL DEFAULT 1;

-- Add satisfaction diversity metric to materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_satisfaction_metrics AS
SELECT
    site_id,
    DATE(created_at) AS event_date,
    COUNT(*) FILTER (WHERE event_type = 'SATISFACTION_RATING') AS total_ratings,
    COUNT(DISTINCT user_id) FILTER (WHERE event_type = 'SATISFACTION_RATING') AS unique_raters,
    COUNT(DISTINCT user_id) AS total_active_users,
    ROUND(COUNT(DISTINCT user_id) FILTER (WHERE event_type = 'SATISFACTION_RATING')::numeric /
          NULLIF(COUNT(DISTINCT user_id), 0) * 100, 1) AS satisfaction_coverage_pct
FROM analytics_events
WHERE site_id IS NOT NULL
GROUP BY site_id, DATE(created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_satisfaction ON mv_satisfaction_metrics(site_id, event_date);
