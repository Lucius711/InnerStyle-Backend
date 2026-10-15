-- PRO/MAX follow Meshy's credit-per-price ratio plus margin. Meshy costs us $0.0133–0.02 per credit
-- (≈350–525 VND at ~26,300 VND/USD), so a fully used plan still keeps roughly 30–58%:
--   PRO 249,000 VND → 300 credits (10 models, Meshy cost ≈ 105–158k VND)
--   MAX 599,000 VND → 800 credits (≈26 models, Meshy cost ≈ 280–420k VND)
-- Existing balances are untouched; new allowances apply from each member's next period.
UPDATE mtb_membership_plans SET monthly_credits = 300, price = 249000, updated_at = NOW() WHERE code = 'PRO';
UPDATE mtb_membership_plans SET monthly_credits = 800, price = 599000, updated_at = NOW() WHERE code = 'MAX';
