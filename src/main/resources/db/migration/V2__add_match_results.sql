-- Add new columns to matches table
ALTER TABLE matches ADD COLUMN IF NOT EXISTS stage VARCHAR(50);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS home_score INTEGER;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS away_score INTEGER;

-- Add new columns to existing match_results table
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS screenshot_url VARCHAR(500);
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS reviewed_by_user_id BIGINT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS review_comment TEXT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

-- Update existing rows to set submitted_at from created_at if null
UPDATE match_results SET submitted_at = created_at WHERE submitted_at IS NULL;

-- Now make submitted_at NOT NULL
ALTER TABLE match_results ALTER COLUMN submitted_at SET NOT NULL;

-- Add foreign key constraint for reviewed_by if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_match_results_reviewed_by') THEN
        ALTER TABLE match_results ADD CONSTRAINT fk_match_results_reviewed_by 
            FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);
    END IF;
END $$;

-- Create indexes if not exist
CREATE INDEX IF NOT EXISTS idx_match_results_is_approved ON match_results(is_approved);
CREATE INDEX IF NOT EXISTS idx_match_results_submitted_by ON match_results(submitted_by_user_id);
