CREATE TABLE favorites (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    post_id    BIGINT NOT NULL REFERENCES posts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_post_favorite UNIQUE (user_id, post_id)
);
CREATE INDEX idx_fav_user ON favorites(user_id);
CREATE INDEX idx_fav_post ON favorites(post_id);

CREATE TABLE user_follows (
    id           BIGSERIAL PRIMARY KEY,
    follower_id  BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_follow UNIQUE (follower_id, following_id),
    CONSTRAINT no_self_follow CHECK (follower_id != following_id)
);
CREATE INDEX idx_follow_follower ON user_follows(follower_id);
CREATE INDEX idx_follow_following ON user_follows(following_id);
