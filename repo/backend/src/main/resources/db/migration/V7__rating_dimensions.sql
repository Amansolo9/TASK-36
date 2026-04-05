-- H2: Add multi-dimensional rating scores
ALTER TABLE ratings ADD COLUMN timeliness_score INT NOT NULL DEFAULT 3 CHECK (timeliness_score BETWEEN 1 AND 5);
ALTER TABLE ratings ADD COLUMN communication_score INT NOT NULL DEFAULT 3 CHECK (communication_score BETWEEN 1 AND 5);
ALTER TABLE ratings ADD COLUMN accuracy_score INT NOT NULL DEFAULT 3 CHECK (accuracy_score BETWEEN 1 AND 5);
