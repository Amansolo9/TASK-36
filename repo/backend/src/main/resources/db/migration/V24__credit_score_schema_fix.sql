-- Ensure credit_scores.score is TEXT for encrypted storage (idempotent)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'credit_scores' AND column_name = 'score'
               AND data_type != 'text') THEN
        ALTER TABLE credit_scores ALTER COLUMN score TYPE TEXT;
    END IF;
END $$;

-- Drop the NOT NULL constraint since encrypted empty/default may be null during transition
ALTER TABLE credit_scores ALTER COLUMN score DROP NOT NULL;

-- Remove the check constraint if it exists (it references integer range which won't work for encrypted text)
ALTER TABLE credit_scores DROP CONSTRAINT IF EXISTS credit_scores_score_check;
