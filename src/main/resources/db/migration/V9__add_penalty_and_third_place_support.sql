-- Add penalty shootout support and third-place match tracking for knockout tournaments

-- Add penalty score columns to matches table
ALTER TABLE matches ADD COLUMN IF NOT EXISTS home_penalty_score INTEGER;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS away_penalty_score INTEGER;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS decided_by_penalties BOOLEAN DEFAULT FALSE;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS is_third_place_match BOOLEAN DEFAULT FALSE;

-- Add penalty score columns to match_results table
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS home_penalty_score INTEGER;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS away_penalty_score INTEGER;

-- Set defaults for existing rows
UPDATE matches SET decided_by_penalties = FALSE WHERE decided_by_penalties IS NULL;
UPDATE matches SET is_third_place_match = FALSE WHERE is_third_place_match IS NULL;
