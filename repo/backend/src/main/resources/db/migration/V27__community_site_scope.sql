-- Add site linkage to community posts for site-scoped isolation
ALTER TABLE posts ADD COLUMN IF NOT EXISTS site_id BIGINT REFERENCES organizations(id);
CREATE INDEX IF NOT EXISTS idx_post_site ON posts(site_id);

-- Backfill: derive site from author's site assignment
UPDATE posts SET site_id = (SELECT site_id FROM users WHERE users.id = posts.author_id)
WHERE site_id IS NULL;
