-- V5: Enhanced tournament features
-- Add fields for tournament configuration and match bye rounds

-- Add new columns to tournaments table
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS max_participants INTEGER;
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS number_of_rounds INTEGER DEFAULT 1;
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS auto_start BOOLEAN DEFAULT false;

-- Add index for active tournaments
CREATE INDEX IF NOT EXISTS idx_tournaments_active ON tournaments(is_active);

-- Add bye round flag to matches
ALTER TABLE matches ADD COLUMN IF NOT EXISTS is_bye BOOLEAN DEFAULT false;

-- Add index for match rounds
CREATE INDEX IF NOT EXISTS idx_matches_round ON matches(tournament_id, round);

-- Update existing tournaments to have default values
UPDATE tournaments SET number_of_rounds = 1 WHERE number_of_rounds IS NULL;
UPDATE tournaments SET auto_start = false WHERE auto_start IS NULL;
UPDATE matches SET is_bye = false WHERE is_bye IS NULL;

-- Add column for tracking impersonation in sessions (we'll handle this in UserContext)
-- This is a comment for future reference - impersonation is handled in memory

COMMENT ON COLUMN tournaments.max_participants IS 'Maximum number of participants allowed. NULL means unlimited.';
COMMENT ON COLUMN tournaments.number_of_rounds IS 'Number of rounds for league format (1 or 2)';
COMMENT ON COLUMN tournaments.auto_start IS 'Whether to auto-start when max_participants is reached';
COMMENT ON COLUMN matches.is_bye IS 'Indicates if this is a bye round (rest/no opponent)';
