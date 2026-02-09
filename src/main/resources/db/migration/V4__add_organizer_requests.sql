-- Create organizer_requests table
CREATE TABLE organizer_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id BIGINT REFERENCES users(id),
    review_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

-- Add index for faster queries
CREATE INDEX idx_organizer_requests_status ON organizer_requests(status);
CREATE INDEX idx_organizer_requests_user_id ON organizer_requests(user_id);
