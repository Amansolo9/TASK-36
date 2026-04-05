ALTER TABLE ratings ADD COLUMN arbitration_reviewer_id BIGINT REFERENCES users(id);
ALTER TABLE ratings ADD COLUMN arbitration_started_at TIMESTAMPTZ;
ALTER TABLE ratings ADD COLUMN arbitration_resolved_at TIMESTAMPTZ;
ALTER TABLE ratings ADD COLUMN arbitration_notes TEXT;
