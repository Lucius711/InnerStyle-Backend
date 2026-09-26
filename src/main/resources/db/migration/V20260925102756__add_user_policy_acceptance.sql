-- Terms & Policies acceptance. The user must re-accept whenever app.auth.policy-version changes:
-- accepted_policy_version != current version => the frontend shows the policy modal again.
ALTER TABLE dtb_users
    ADD COLUMN accepted_policy_version VARCHAR(32),
    ADD COLUMN policy_accepted_at      TIMESTAMPTZ;
