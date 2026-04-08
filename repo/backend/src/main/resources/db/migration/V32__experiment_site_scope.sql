-- Add site_id to experiments for site-scoped tenancy.
-- ENTERPRISE_ADMIN can manage global experiments (site_id IS NULL).
-- SITE_MANAGER can only manage experiments scoped to their site.
ALTER TABLE experiments ADD COLUMN site_id BIGINT REFERENCES organizations(id);

CREATE INDEX idx_experiment_site ON experiments(site_id);
