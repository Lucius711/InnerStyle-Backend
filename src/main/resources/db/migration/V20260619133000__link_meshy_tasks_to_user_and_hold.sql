-- =============================================================================
-- Link the existing dtb_meshy_tasks to its owner and the authorization hold
-- that backs it. Both columns are NULL-able so pre-existing rows remain valid
-- (Flyway migrations are immutable; this is the forward change).
--   user_id : owner of the job        -> CASCADE (job dies with the user)
--   hold_id : funds reserved for it    -> SET NULL (keep the task if hold purged)
-- =============================================================================

ALTER TABLE dtb_meshy_tasks ADD COLUMN user_id UUID;
ALTER TABLE dtb_meshy_tasks ADD COLUMN hold_id UUID;

ALTER TABLE dtb_meshy_tasks
    ADD CONSTRAINT fk_dtb_meshy_tasks_user_id
    FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE;

ALTER TABLE dtb_meshy_tasks
    ADD CONSTRAINT fk_dtb_meshy_tasks_hold_id
    FOREIGN KEY (hold_id) REFERENCES dtb_holds (id) ON DELETE SET NULL;

CREATE INDEX idx_dtb_meshy_tasks_user_id ON dtb_meshy_tasks (user_id);
CREATE INDEX idx_dtb_meshy_tasks_hold_id ON dtb_meshy_tasks (hold_id);
-- A user's task history, newest first (dashboard / library import).
CREATE INDEX idx_dtb_meshy_tasks_user_created
    ON dtb_meshy_tasks (user_id, created_at DESC);
