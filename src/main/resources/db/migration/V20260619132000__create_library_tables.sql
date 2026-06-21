-- =============================================================================
-- Library module
-- A user's finished 3D models become library items that can be kept private,
-- unlisted, or published to the public gallery (SEO-friendly slug). Supports
-- categories, free-form tags, likes and favourites.
-- File URLs stored as RELATIVE paths (rule 16); absolute URLs built in the DTO.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Master: gallery categories (e.g. Characters, Props, Avatars, Vehicles).
-- ----------------------------------------------------------------------------
CREATE TABLE mtb_categories (
    id         SERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- Master: tags (normalised, shared across models).
-- ----------------------------------------------------------------------------
CREATE TABLE mtb_tags (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL UNIQUE,
    slug       VARCHAR(80)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- Library models. source_task_id links back to the meshy task that produced it
-- (SET NULL: keep the library item even if the raw task is purged).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_models (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    source_task_id  UUID,
    category_id     INTEGER,
    name            VARCHAR(150) NOT NULL,
    slug            VARCHAR(180) NOT NULL UNIQUE,
    description     TEXT,
    visibility      VARCHAR(15)  NOT NULL DEFAULT 'PRIVATE',
    status          VARCHAR(15)  NOT NULL DEFAULT 'DRAFT',
    thumbnail_url   TEXT,
    preview_glb_url TEXT,
    polycount       INTEGER,
    like_count      INTEGER      NOT NULL DEFAULT 0,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    is_featured     BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_models_visibility
        CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC')),
    CONSTRAINT chk_dtb_models_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT fk_dtb_models_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_models_source_task_id
        FOREIGN KEY (source_task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_dtb_models_category_id
        FOREIGN KEY (category_id) REFERENCES mtb_categories (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_models_user_id ON dtb_models (user_id);
CREATE INDEX idx_dtb_models_category_id ON dtb_models (category_id);
CREATE INDEX idx_dtb_models_source_task_id ON dtb_models (source_task_id);
-- Public gallery listing: only PUBLIC + PUBLISHED, newest first.
CREATE INDEX idx_dtb_models_public_feed
    ON dtb_models (published_at DESC)
    WHERE visibility = 'PUBLIC' AND status = 'PUBLISHED';
-- Full-text search over name + description for the gallery search box.
CREATE INDEX idx_dtb_models_search
    ON dtb_models USING GIN (to_tsvector('simple', name || ' ' || COALESCE(description, '')));

-- ----------------------------------------------------------------------------
-- Concrete downloadable assets per model (one row per format/variant).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_model_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id        UUID         NOT NULL,
    asset_type      VARCHAR(20)  NOT NULL,
    format          VARCHAR(10)  NOT NULL,
    file_url        TEXT         NOT NULL,
    file_size_bytes BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_model_assets_type
        CHECK (asset_type IN ('MODEL', 'TEXTURE', 'ANIMATION', 'THUMBNAIL')),
    CONSTRAINT fk_dtb_model_assets_model_id
        FOREIGN KEY (model_id) REFERENCES dtb_models (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_model_assets_model_id ON dtb_model_assets (model_id);

-- ----------------------------------------------------------------------------
-- Model <-> Tag (many-to-many).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_model_tags (
    model_id UUID    NOT NULL,
    tag_id   INTEGER NOT NULL,

    CONSTRAINT pk_dtb_model_tags PRIMARY KEY (model_id, tag_id),
    CONSTRAINT fk_dtb_model_tags_model_id
        FOREIGN KEY (model_id) REFERENCES dtb_models (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_model_tags_tag_id
        FOREIGN KEY (tag_id) REFERENCES mtb_tags (id) ON DELETE RESTRICT
);

CREATE INDEX idx_dtb_model_tags_tag_id ON dtb_model_tags (tag_id);

-- ----------------------------------------------------------------------------
-- Likes (one per user per model).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_model_likes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id   UUID NOT NULL,
    user_id    UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dtb_model_likes_model_user UNIQUE (model_id, user_id),
    CONSTRAINT fk_dtb_model_likes_model_id
        FOREIGN KEY (model_id) REFERENCES dtb_models (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_model_likes_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_model_likes_user_id ON dtb_model_likes (user_id);

-- ----------------------------------------------------------------------------
-- Favourites / saved-to-collection (one per user per model).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_model_favorites (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id   UUID NOT NULL,
    user_id    UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dtb_model_favorites_model_user UNIQUE (model_id, user_id),
    CONSTRAINT fk_dtb_model_favorites_model_id
        FOREIGN KEY (model_id) REFERENCES dtb_models (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_model_favorites_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_model_favorites_user_id ON dtb_model_favorites (user_id);
