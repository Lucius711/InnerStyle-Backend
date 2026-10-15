-- FREE no longer runs on credits: every FREE account gets exactly 3 generations in total (any
-- service, never renewed). Track how many it has used; paid plans keep using credits.
ALTER TABLE dtb_user_memberships ADD COLUMN free_uses INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dtb_user_memberships
    ADD CONSTRAINT chk_dtb_user_memberships_free_uses CHECK (free_uses >= 0);

-- FREE members' leftover credits are meaningless now (they spend uses, not credits).
UPDATE dtb_user_memberships
SET credits_remaining = 0
WHERE plan_id = (SELECT id FROM mtb_membership_plans WHERE code = 'FREE');
