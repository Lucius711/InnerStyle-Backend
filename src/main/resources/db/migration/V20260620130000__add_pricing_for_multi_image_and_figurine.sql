-- Charge for multi-image-to-3D and the figurine stages. The mtb_pricing CHECK only allowed the
-- original 7 task types, so widen it first, then seed the new prices (idempotent).

ALTER TABLE mtb_pricing DROP CONSTRAINT IF EXISTS chk_mtb_pricing_task_type;

ALTER TABLE mtb_pricing
    ADD CONSTRAINT chk_mtb_pricing_task_type
    CHECK (task_type IN ('IMAGE_TO_3D', 'MULTI_IMAGE_TO_3D', 'TEXT_TO_3D_PREVIEW',
                         'TEXT_TO_3D_REFINE', 'REMESH', 'RETEXTURE', 'RIG', 'ANIMATE',
                         'FIGURE_PROTOTYPE', 'FIGURE_BUILD'));

INSERT INTO mtb_pricing (task_type, unit_price) VALUES
    ('MULTI_IMAGE_TO_3D', 25000),
    ('FIGURE_PROTOTYPE',  10000),
    ('FIGURE_BUILD',      30000)
ON CONFLICT (task_type) DO NOTHING;
