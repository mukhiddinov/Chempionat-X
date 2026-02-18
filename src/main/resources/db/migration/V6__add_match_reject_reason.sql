-- Add reject_reason column to matches table for storing rejection reasons
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
