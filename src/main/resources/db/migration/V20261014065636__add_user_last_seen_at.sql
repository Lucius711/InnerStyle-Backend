-- Presence: bumped by the SPA heartbeat while signed in, cleared on logout.
ALTER TABLE dtb_users ADD COLUMN last_seen_at TIMESTAMPTZ;

CREATE INDEX idx_dtb_users_last_seen_at ON dtb_users (last_seen_at);
