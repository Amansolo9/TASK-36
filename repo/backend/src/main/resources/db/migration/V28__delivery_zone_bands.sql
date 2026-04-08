-- Delivery zone groups with ZIP lists and distance bands
CREATE TABLE delivery_zone_groups (
    id          BIGSERIAL PRIMARY KEY,
    site_id     BIGINT NOT NULL REFERENCES organizations(id),
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dzg_site ON delivery_zone_groups(site_id);

-- ZIP codes belonging to a zone group
CREATE TABLE delivery_zone_zips (
    id       BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES delivery_zone_groups(id) ON DELETE CASCADE,
    zip_code VARCHAR(10) NOT NULL,
    CONSTRAINT unique_zip_per_group UNIQUE (group_id, zip_code)
);
CREATE INDEX idx_dzz_zip ON delivery_zone_zips(zip_code);
CREATE INDEX idx_dzz_group ON delivery_zone_zips(group_id);

-- Distance bands within a zone group
CREATE TABLE delivery_distance_bands (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL REFERENCES delivery_zone_groups(id) ON DELETE CASCADE,
    min_miles  DOUBLE PRECISION NOT NULL,
    max_miles  DOUBLE PRECISION NOT NULL,
    fee        NUMERIC(10,2) NOT NULL,
    CONSTRAINT valid_band CHECK (min_miles >= 0 AND max_miles > min_miles AND fee >= 0)
);
CREATE INDEX idx_ddb_group ON delivery_distance_bands(group_id);
