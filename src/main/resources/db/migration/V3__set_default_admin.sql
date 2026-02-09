-- Set default admin user
-- This migration ensures telegram_id 1059249931 is always an admin

-- Update user to admin if exists
UPDATE users 
SET role = 'ADMIN' 
WHERE telegram_id = 1059249931;

-- If user doesn't exist yet, this migration will run after they first use /start
-- So we don't need to insert here
