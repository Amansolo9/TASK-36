-- Add distance per ZIP so group pricing does not depend on legacy zones
ALTER TABLE delivery_zone_zips ADD COLUMN IF NOT EXISTS distance_miles DOUBLE PRECISION NOT NULL DEFAULT 0;
