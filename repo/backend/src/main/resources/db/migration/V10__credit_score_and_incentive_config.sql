-- Credit score tracking
CREATE TABLE credit_scores (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) UNIQUE,
    score           INT NOT NULL DEFAULT 500 CHECK (score BETWEEN 0 AND 1000),
    rating_impact   INT NOT NULL DEFAULT 0,
    community_impact INT NOT NULL DEFAULT 0,
    order_impact    INT NOT NULL DEFAULT 0,
    dispute_impact  INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_credit_user ON credit_scores(user_id);

-- Configurable incentive rules
CREATE TABLE incentive_rules (
    id          BIGSERIAL PRIMARY KEY,
    action_key  VARCHAR(50) NOT NULL UNIQUE,
    points      INT NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default incentive rules
INSERT INTO incentive_rules (action_key, points, description) VALUES
    ('POST_CREATED', 5, 'Points for creating a new post'),
    ('UPVOTE_RECEIVED', 1, 'Points for receiving an upvote'),
    ('DOWNVOTE_RECEIVED', -1, 'Points for receiving a downvote'),
    ('POST_REMOVED', -10, 'Penalty for post removal'),
    ('COMMENT_CREATED', 2, 'Points for adding a comment'),
    ('QUARANTINED', -20, 'Penalty for like-ring detection');
