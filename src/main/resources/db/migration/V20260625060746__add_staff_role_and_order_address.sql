-- =============================================================================
-- STAFF role + customer/shipping details on 3D-print orders.
--   1. Seed the STAFF role (operations staff who fulfil print orders).
--   2. Add recipient + Vietnam address (province / ward) + geo-coordinates to
--      dtb_print_orders so an order carries everything staff need to ship it.
--   3. Seed a default staff account so the staff dashboard is usable out of the box.
-- Conforms to rules 08 (migration), 11 (multi-role) & 14 (schema).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1. STAFF role (master data). Authority -> ROLE_STAFF.
-- ----------------------------------------------------------------------------
INSERT INTO mtb_roles (code, name, description) VALUES
    ('STAFF', 'Staff', 'Operations staff who fulfil customer 3D-print orders')
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Customer + shipping address on print orders. All nullable (legacy rows
--    predate the form); the API enforces presence for new orders.
--    province/ward are stored denormalised (code + display name) because the
--    source is an external Vietnam administrative-units API.
-- ----------------------------------------------------------------------------
ALTER TABLE dtb_print_orders
    ADD COLUMN IF NOT EXISTS recipient_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS province_code   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS province_name   VARCHAR(150),
    ADD COLUMN IF NOT EXISTS ward_code       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ward_name       VARCHAR(150),
    ADD COLUMN IF NOT EXISTS address_detail  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS latitude        NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude       NUMERIC(10, 7);

-- ----------------------------------------------------------------------------
-- 3. Default staff account.
--    email:    staff@innerstyle.local
--    password: Staff@12345   (bcrypt, cost 10 — change in production!)
--    Idempotent: only inserted when the email does not already exist.
-- ----------------------------------------------------------------------------
INSERT INTO dtb_users (email, password_hash, full_name, status, email_verified, email_verified_at)
SELECT 'staff@innerstyle.local',
       '$2b$10$DUqo/A.CnbSPSA/0ohTzaegjBuT4XeC/5OfDPdWq/lAJUYw2a1uKO',
       'InnerStyle Staff',
       'ACTIVE',
       TRUE,
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM dtb_users WHERE LOWER(email) = 'staff@innerstyle.local'
);

-- Link the staff account to the STAFF role (master FK -> RESTRICT, handled by table def).
INSERT INTO dtb_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM dtb_users u
CROSS JOIN mtb_roles r
WHERE LOWER(u.email) = 'staff@innerstyle.local'
  AND r.code = 'STAFF'
ON CONFLICT (user_id, role_id) DO NOTHING;
