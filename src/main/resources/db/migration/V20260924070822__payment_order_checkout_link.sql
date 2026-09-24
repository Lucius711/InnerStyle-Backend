-- Keep the payOS checkout link on the payment order so a user who left the payment page can
-- resume the SAME link (no second payable link -> no double charge). return_url was never used.
ALTER TABLE dtb_payment_orders RENAME COLUMN return_url TO checkout_url;
ALTER TABLE dtb_payment_orders ADD COLUMN IF NOT EXISTS qr_code TEXT;
