-- Organization hierarchy: Enterprise -> Site -> Team
CREATE TABLE organizations (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    level           VARCHAR(20)  NOT NULL CHECK (level IN ('ENTERPRISE', 'SITE', 'TEAM')),
    parent_id       BIGINT REFERENCES organizations(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_org_parent ON organizations(parent_id);
CREATE INDEX idx_org_level  ON organizations(level);

-- Users
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(100)  NOT NULL UNIQUE,
    email                 VARCHAR(255)  NOT NULL UNIQUE,
    password_hash         VARCHAR(255)  NOT NULL,
    role                  VARCHAR(30)   NOT NULL CHECK (role IN ('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF','CUSTOMER')),
    site_id               BIGINT REFERENCES organizations(id),
    address               TEXT,
    device_id             VARCHAR(512),
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    last_authenticated_at TIMESTAMPTZ,
    last_activity_at      TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_site ON users(site_id);
CREATE INDEX idx_users_role ON users(role);
