-- Launch promo: PRO/MAX sold below Meshy cost on purpose to win first customers; credits unchanged.
-- At Meshy's ≈350–525 VND per credit, a fully used plan now loses money:
--   PRO  99,000 VND → 300 credits (Meshy cost ≈ 105–158k VND)
--   MAX 249,000 VND → 800 credits (Meshy cost ≈ 280–420k VND)
-- Revert with a new migration back to 249,000 / 599,000 when the promo ends.
UPDATE mtb_membership_plans SET price = 99000,  updated_at = NOW() WHERE code = 'PRO';
UPDATE mtb_membership_plans SET price = 249000, updated_at = NOW() WHERE code = 'MAX';
