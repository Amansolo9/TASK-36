-- Drop the integer-range CHECK constraint before changing column type
ALTER TABLE credit_scores DROP CONSTRAINT IF EXISTS credit_scores_score_check;
ALTER TABLE credit_scores ALTER COLUMN score DROP NOT NULL;
-- Widen score column for encrypted storage
ALTER TABLE credit_scores ALTER COLUMN score TYPE TEXT USING score::TEXT;
ALTER TABLE credit_scores ALTER COLUMN score SET DEFAULT '500';
