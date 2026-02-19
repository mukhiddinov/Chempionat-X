-- V7: Single Elimination (Playoff) Bracket Support
-- Adds bracket structure for knockout tournaments

-- Add bracket_position to track match order within bracket (1-based, left-to-right, top-to-bottom)
ALTER TABLE matches ADD COLUMN IF NOT EXISTS bracket_position INTEGER;

-- Add next_match_id for winner propagation (self-referential FK)
ALTER TABLE matches ADD COLUMN IF NOT EXISTS next_match_id BIGINT;

-- Add home_slot flag to indicate if winner goes to home_team (true) or away_team (false) slot of next match
ALTER TABLE matches ADD COLUMN IF NOT EXISTS winner_to_home BOOLEAN;

-- Add constraint for next_match reference
ALTER TABLE matches 
    ADD CONSTRAINT fk_match_next_match 
    FOREIGN KEY (next_match_id) 
    REFERENCES matches(id) 
    ON DELETE SET NULL;

-- Create index for efficient bracket queries
CREATE INDEX IF NOT EXISTS idx_matches_bracket ON matches(tournament_id, bracket_position);
CREATE INDEX IF NOT EXISTS idx_matches_next_match ON matches(next_match_id);

-- Add unique constraint for team name per tournament (case-insensitive)
-- This ensures no two teams in the same tournament have the same name
CREATE UNIQUE INDEX IF NOT EXISTS idx_teams_unique_name_per_tournament 
    ON teams (tournament_id, LOWER(name));

-- Add tournament status for proper state management
-- Using VARCHAR for easier enum handling in JPA
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS status VARCHAR(50);

-- Set default status for existing tournaments
UPDATE tournaments SET status = 'STARTED' WHERE status IS NULL AND start_date IS NOT NULL AND is_active = true;
UPDATE tournaments SET status = 'FINISHED' WHERE status IS NULL AND end_date IS NOT NULL;
UPDATE tournaments SET status = 'CREATED' WHERE status IS NULL;

-- Add comments
COMMENT ON COLUMN matches.bracket_position IS 'Position in bracket tree for ordering (1-based)';
COMMENT ON COLUMN matches.next_match_id IS 'ID of match where winner advances to';
COMMENT ON COLUMN matches.winner_to_home IS 'True if winner goes to home_team slot, false for away_team slot';
COMMENT ON COLUMN tournaments.status IS 'Tournament status: CREATED, REGISTRATION, STARTED, IN_PROGRESS, FINISHED, CANCELLED';
