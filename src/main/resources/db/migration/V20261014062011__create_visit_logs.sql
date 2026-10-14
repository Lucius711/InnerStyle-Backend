-- One row per site visit (beacon from the SPA); user_id is null for guests.
CREATE TABLE dtb_visit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_visit_logs_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_visit_logs_created_at ON dtb_visit_logs (created_at DESC);
CREATE INDEX idx_dtb_visit_logs_user_id ON dtb_visit_logs (user_id);
