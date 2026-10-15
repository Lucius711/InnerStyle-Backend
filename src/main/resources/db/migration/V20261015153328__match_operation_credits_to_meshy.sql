-- Charge users exactly what Meshy charges us per operation (docs.meshy.ai/en/api/pricing, Oct 2026).
-- Assumes the defaults the Studio sends: latest/meshy-6 model, texture ON (2K/4K cost the same),
-- one animation action per ANIMATE request. Remesh / rig / animate / figure costs are flat at Meshy.
INSERT INTO mtb_operation_credits (task_type, credit_cost) VALUES
    ('IMAGE_TO_3D',        30),  -- image to 3D with texture (mesh only would be 20)
    ('MULTI_IMAGE_TO_3D',  30),  -- multi-image with texture (mesh only would be 20)
    ('TEXT_TO_3D_PREVIEW', 20),
    ('TEXT_TO_3D_REFINE',  10),  -- 2K/4K texture
    ('REMESH',              5),
    ('RETEXTURE',          10),  -- 2K/4K texture
    ('RIG',                 5),
    ('ANIMATE',             3),  -- per action
    ('FIGURE_PROTOTYPE',    6),  -- Creative Lab figure, prototype stage
    ('FIGURE_BUILD',       30)   -- Creative Lab figure, build stage
ON CONFLICT (task_type) DO UPDATE
    SET credit_cost = EXCLUDED.credit_cost,
        updated_at  = NOW();
