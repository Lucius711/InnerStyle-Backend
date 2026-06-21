-- Align currency columns with the JPA String mapping (varchar) — the original CHAR(3)
-- columns failed Hibernate schema validation (bpchar vs varchar). 'VND' fits exactly so
-- no data is affected. Forward fix (migrations are immutable; we don't edit the originals).

ALTER TABLE dtb_wallets        ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE dtb_payment_orders ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE mtb_pricing        ALTER COLUMN currency TYPE VARCHAR(3);
