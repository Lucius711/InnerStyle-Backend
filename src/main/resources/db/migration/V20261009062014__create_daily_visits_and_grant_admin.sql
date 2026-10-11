-- One counter row per calendar day (Asia/Ho_Chi_Minh) for the admin traffic chart.
CREATE TABLE dtb_daily_visits (
    visit_date  DATE    PRIMARY KEY,
    visit_count INTEGER NOT NULL DEFAULT 0
);

-- ADMIN already exists in mtb_roles (V20260619130000); grant it to the owner account.
-- No-op on a fresh DB where that account hasn't signed in yet.
INSERT INTO dtb_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM dtb_users u
CROSS JOIN mtb_roles r
WHERE LOWER(u.email) = 'dohuy2915@gmail.com'
  AND r.code = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
