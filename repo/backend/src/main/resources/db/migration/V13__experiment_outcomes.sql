CREATE TABLE experiment_outcomes (
    id            BIGSERIAL PRIMARY KEY,
    experiment_id BIGINT NOT NULL REFERENCES experiments(id),
    variant       INT NOT NULL,
    reward        DOUBLE PRECISION NOT NULL,
    user_id       BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_exp_outcome ON experiment_outcomes(experiment_id, variant);
