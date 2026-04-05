-- Posts
CREATE TABLE posts (
    id         BIGSERIAL PRIMARY KEY,
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    topic      VARCHAR(100),
    upvotes    INT          NOT NULL DEFAULT 0,
    downvotes  INT          NOT NULL DEFAULT 0,
    removed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_author ON posts(author_id);
CREATE INDEX idx_post_topic  ON posts(topic);

-- Comments
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts(id),
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    body       TEXT        NOT NULL,
    removed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_post   ON comments(post_id);
CREATE INDEX idx_comment_author ON comments(author_id);

-- Votes (one per user per post)
CREATE TABLE votes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    post_id    BIGINT      NOT NULL REFERENCES posts(id),
    type       VARCHAR(10) NOT NULL CHECK (type IN ('UPVOTE','DOWNVOTE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_post_vote UNIQUE (user_id, post_id)
);

CREATE INDEX idx_vote_post ON votes(post_id);

-- Topic follows
CREATE TABLE topic_follows (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    topic      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_topic UNIQUE (user_id, topic)
);

-- Points ledger
CREATE TABLE point_ledger (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    action      VARCHAR(30) NOT NULL,
    points      INT         NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_points_user ON point_ledger(user_id);

-- Quarantined votes (fraud detection)
CREATE TABLE quarantined_votes (
    id             BIGSERIAL PRIMARY KEY,
    voter_id       BIGINT      NOT NULL REFERENCES users(id),
    post_author_id BIGINT      NOT NULL REFERENCES users(id),
    vote_count     INT         NOT NULL,
    reason         VARCHAR(255) NOT NULL,
    reviewed       BOOLEAN     NOT NULL DEFAULT FALSE,
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quarantine_voter  ON quarantined_votes(voter_id);
CREATE INDEX idx_quarantine_author ON quarantined_votes(post_author_id);
