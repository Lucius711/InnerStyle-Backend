-- Rebalance monthly credits now that operations cost what Meshy charges (Image→3D = 30 credits).
-- FREE: 0 credits — it gets 3 lifetime uses instead (V20261015154112). PRO/MAX keep their VND price; credits sized so a fully used plan
-- still leaves margin at Meshy's $0.0133–0.02 per credit (≈350–525 VND at ~26,300 VND/USD).
--   PRO 99,000 VND → 120 credits (4 models, Meshy cost ≈ 42–63k VND)
--   MAX 299,000 VND → 400 credits (≈13 models, Meshy cost ≈ 140–210k VND)
-- Existing balances are untouched; new allowances apply from each member's next period.
INSERT INTO mtb_membership_plans (code, name, monthly_credits, price, sort_order) VALUES
    ('FREE', 'Free', 0,   0,      0),
    ('PRO',  'Pro',  120, 99000,  1),
    ('MAX',  'Max',  400, 299000, 2)
ON CONFLICT (code) DO UPDATE
    SET monthly_credits = EXCLUDED.monthly_credits,
        updated_at      = NOW();
