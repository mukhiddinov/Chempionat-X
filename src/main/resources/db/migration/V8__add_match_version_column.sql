-- Add version column for optimistic locking on matches table
-- This prevents race conditions when multiple approvals happen concurrently

ALTER TABLE matches ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Set initial version for existing matches
UPDATE matches SET version = 0 WHERE version IS NULL;

-- Make version NOT NULL after setting defaults
ALTER TABLE matches ALTER COLUMN version SET NOT NULL;
