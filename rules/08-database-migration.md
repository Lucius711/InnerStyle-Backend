# Database Migration Rules - iGoGo Backend

## Overview

The project uses **Flyway** with plain SQL migrations against PostgreSQL. Migrations ensure schema changes are versioned, ordered, and reproducible across every environment. Spring Boot runs pending migrations automatically on startup (when `flyway` is on the classpath); you can also run them via the Maven plugin.

## Migration Location

```
src/main/resources/db/migration/
├── V20251027093000__create_sms_delivery_table.sql
├── V20251028120000__create_homestay_tables.sql
├── V20251029150000__add_address_ids_to_owner_profiles.sql
└── ...
```

Configured in `application.yml`:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: validate     # Flyway owns the schema; Hibernate only validates it
```

## Migration File Structure

### Naming Convention

```
V<version>__<descriptive_name>.sql
```

- Prefix `V` = a **versioned** migration (run once, in order).
- `<version>` is commonly a UTC timestamp `yyyyMMddHHmmss` (e.g. `20251027093000`) — this keeps files ordered and avoids clashes between developers.
- **Double underscore** `__` separates version from description.
- Description uses `snake_case`.

**CRITICAL RULES**

- A migration file, once merged/applied, is **immutable**. Never edit an applied migration — create a new one. Flyway validates a checksum and will fail if an applied file changes.
- Versions must be **monotonically increasing**. A timestamp from the past/present is fine; just ensure it's higher than the last merged migration.
- Generate a timestamp version with:

```bash
date -u +%Y%m%d%H%M%S      # e.g. 20251027093000
```

**Examples:**

```
V20251027093000__create_sms_delivery_table.sql
V20251028120000__create_homestay_tables.sql
V20251029150000__add_address_ids_to_owner_profiles.sql
V20251101080000__update_content_questions_category.sql
```

### Special Prefixes

- `V...__...sql` — versioned migration (the default; use for all schema changes).
- `R__...sql` — **repeatable** migration (re-applied whenever its checksum changes; runs after versioned ones). Useful for views/functions, NOT for table changes.
- Flyway Community has **no automatic down/undo** (`U` migrations are a paid feature). Roll back by writing a new forward migration that reverts the change. Keep a documented manual rollback snippet in the PR.

### Basic Migration Template

```sql
-- V20251027093000__create_users_table.sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);
```

To revert, add a later forward migration (Flyway Community has no automatic rollback):

```sql
-- V20251027100000__drop_users_table.sql  (manual "down")
DROP TABLE IF EXISTS users CASCADE;
```

## CLI / Maven Commands

```bash
# Apply all pending migrations
./mvnw flyway:migrate

# Migration status / history
./mvnw flyway:info

# Validate applied migrations against the files (checksum check)
./mvnw flyway:validate

# Repair the schema_history table after a failed/changed migration
./mvnw flyway:repair

# (dev only) Drop everything and re-create — NEVER in production
./mvnw flyway:clean
```

> In normal operation, Spring Boot applies pending migrations automatically at startup. The Maven goals are for local control and CI.

## Creating Migrations

### Step 1: Generate a version

```bash
date -u +%Y%m%d%H%M%S      # -> 20251027093000
```

### Step 2: Create the SQL file

Create `src/main/resources/db/migration/V20251027093000__create_users_table.sql`.

### Step 3: Write the schema change

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);
```

### Step 4: Run it

```bash
./mvnw flyway:migrate        # or just start the app
```

## Common Migration Patterns

### Create Table

```sql
-- V..._create_products_table.sql
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    stock       INTEGER NOT NULL DEFAULT 0,
    category_id UUID REFERENCES categories (id) ON DELETE SET NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_is_active ON products (is_active);
```

### Add Column

```sql
-- V..._add_email_verification.sql
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
```

### Drop Column

```sql
ALTER TABLE users DROP COLUMN legacy_field;
```

### Rename Column

```sql
ALTER TABLE users RENAME COLUMN full_name TO display_name;
```

### Change Column Type

```sql
ALTER TABLE products ALTER COLUMN price TYPE NUMERIC(12, 2);
```

### Add Index

```sql
-- Single column
CREATE INDEX idx_users_email ON users (email);

-- Composite
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);

-- Unique
CREATE UNIQUE INDEX idx_users_email_unique ON users (email);

-- Partial
CREATE INDEX idx_active_users ON users (created_at) WHERE is_active = TRUE;

-- GIN full-text index
CREATE INDEX idx_products_search ON products
    USING GIN (to_tsvector('english', name || ' ' || description));
```

> For large tables, prefer `CREATE INDEX CONCURRENTLY`. Note: `CONCURRENTLY` cannot run inside a transaction, so set `spring.flyway.mixed=true` or put it in its own migration and disable Flyway's transaction for that script.

### Add Foreign Key

```sql
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_product_id
    FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT;
```

### Enum-like Columns

PostgreSQL `ENUM` types are awkward to evolve. With JPA we persist enums as strings (`@Enumerated(EnumType.STRING)`), so prefer a `VARCHAR` + `CHECK` constraint over a native enum:

```sql
-- Recommended: varchar + check (easy to extend in a later migration)
ALTER TABLE orders
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

-- Native enum alternative (harder to alter later)
CREATE TYPE order_status AS ENUM ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED');
ALTER TABLE orders ADD COLUMN status order_status DEFAULT 'PENDING';
```

### Data Migration

A versioned migration may include `UPDATE`/`INSERT` to backfill data alongside schema changes:

```sql
-- V..._convert_flags_to_role.sql
ALTER TABLE users ADD COLUMN role VARCHAR(50) DEFAULT 'user';

UPDATE users SET role = 'admin'     WHERE is_admin = TRUE;
UPDATE users SET role = 'moderator' WHERE is_moderator = TRUE;

ALTER TABLE users DROP COLUMN is_admin;
ALTER TABLE users DROP COLUMN is_moderator;
```

### ❌ NEVER Seed Application Data in Migrations

**CRITICAL RULE: Versioned migrations handle SCHEMA, not application/seed data.**

```sql
-- ❌ BAD - seeding user-facing content in a migration
INSERT INTO categories (name, slug) VALUES ('Electronics', 'electronics'), ('Clothing', 'clothing');
```

**Why this is wrong:**

1. **Separation of concerns** — migrations = structure, seeders = content.
2. **Environment differences** — seed data differs across dev/staging/prod.
3. **Re-runnability** — seeders can refresh independently of schema.
4. **Rollback risk** — data rollback is complex and risky.

**✅ Correct approach — seed via the application (profile-gated runner):**

```java
@Configuration
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class CategorySeeder {

    private final CategoryRepository categoryRepository;

    @Bean
    CommandLineRunner seedCategories() {
        return args -> {
            // Idempotent upsert-style seeding
            List<Category> defaults = List.of(
                new Category("electronics", "Electronics"),
                new Category("clothing", "Quần áo"),
                new Category("books", "Sách"));
            defaults.forEach(c -> categoryRepository
                .findBySlug(c.getSlug())
                .orElseGet(() -> categoryRepository.save(c)));
        };
    }
}
```

> Alternatively use a profile-specific SQL seed (e.g. `spring.sql.init.data-locations` with `spring.sql.init.mode=embedded`) so it only runs in dev/test — never wire it into Flyway versioned migrations.

**Exception — minimal master/reference data required for FK integrity:**

If reference data is required for the schema itself (e.g. a master table referenced by a NOT NULL FK), it MAY live in a migration, kept tiny, static, and idempotent:

```sql
-- ⚠️ ONLY for critical, static master data needed for schema integrity
CREATE TABLE mtb_user_roles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_key   VARCHAR(50) UNIQUE NOT NULL,
    role_name  VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO mtb_user_roles (role_key, role_name) VALUES
    ('admin', 'Administrator'),
    ('user',  'Regular User')
ON CONFLICT (role_key) DO NOTHING;

CREATE TABLE dtb_users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) UNIQUE NOT NULL,
    role_id    UUID REFERENCES mtb_user_roles (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

**Rules for master data in migrations:** only system-critical, static, FK-required, very small (< 10 rows), idempotent (`ON CONFLICT DO NOTHING`). Never for user content, large datasets, or environment-specific data.

## Best Practices

### 1. Never Edit an Applied Migration

Once a migration has run anywhere (CI, a teammate's DB, prod), it is frozen. Make changes in a new versioned file. Editing breaks Flyway's checksum validation.

### 2. Transactions Are Automatic

On PostgreSQL, Flyway runs each migration inside a transaction (Postgres supports transactional DDL), so a failed migration rolls back cleanly. Exceptions: `CREATE INDEX CONCURRENTLY` and a few other statements can't run in a transaction — isolate those.

### 3. Provide a Forward "Undo" When Needed

There's no automatic rollback in Flyway Community. For risky changes, prepare and document a forward revert migration and test it on a staging copy.

### 4. Use CASCADE Carefully

```sql
-- Good - intentional cascade on a FK
ALTER TABLE orders ADD CONSTRAINT fk_orders_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Dangerous - may delete related data unexpectedly
DROP TABLE users CASCADE;   -- ❌ double-check before doing this
```

### 5. Make Statements Idempotent Where Possible

```sql
CREATE TABLE IF NOT EXISTS users (...);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
DROP TABLE IF EXISTS users CASCADE;
```

### 6. One Logical Change Per Migration

```sql
-- Good - one focused change
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Bad - unrelated changes in one file (split into separate migrations) ❌
```

### 7. Use Descriptive Names

```
V20251027093000__create_users_table.sql
V20251027100000__add_email_verification_column.sql
V20251027110000__create_orders_table_with_foreign_keys.sql

# Bad
V20251027093000__migration1.sql   # ❌ vague
V20251027100000__fix.sql           # ❌ unclear
```

### 8. Document Complex Migrations

```sql
-- V..._migrate_to_tiered_pricing.sql
-- Moves single product price into a pricing_tiers table (tier 1 = existing price).

CREATE TABLE pricing_tiers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID REFERENCES products (id) ON DELETE CASCADE,
    min_quantity INTEGER NOT NULL,
    price        NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO pricing_tiers (product_id, min_quantity, price)
SELECT id, 1, price FROM products;

ALTER TABLE products DROP COLUMN price;
```

## Column Data Types (PostgreSQL)

```sql
-- UUID
id UUID PRIMARY KEY DEFAULT gen_random_uuid()

-- Strings
name        VARCHAR(255) NOT NULL
email       VARCHAR(255) NOT NULL UNIQUE
description TEXT
code        CHAR(10)

-- Numbers
age      INTEGER
price    NUMERIC(10, 2)
quantity BIGINT
rating   NUMERIC(3, 2)

-- Boolean
is_active BOOLEAN DEFAULT TRUE

-- Dates / Times (prefer TIMESTAMPTZ; maps to java.time.Instant/OffsetDateTime)
created_at TIMESTAMPTZ DEFAULT NOW()
birth_date DATE

-- JSON (maps to a JPA @JdbcTypeCode(SqlTypes.JSON) field)
metadata JSONB

-- Arrays
tags TEXT[]
```

## Constraints

```sql
-- Primary key
id UUID PRIMARY KEY DEFAULT gen_random_uuid()

-- Foreign key
user_id    UUID REFERENCES users (id) ON DELETE CASCADE
product_id UUID REFERENCES products (id) ON DELETE SET NULL

-- Unique
email VARCHAR(255) UNIQUE
CONSTRAINT unique_email UNIQUE (email)

-- Not null
full_name VARCHAR(255) NOT NULL

-- Check
age   INTEGER CHECK (age >= 0 AND age <= 150)
price NUMERIC(10, 2) CHECK (price > 0)

-- Default
is_active  BOOLEAN DEFAULT TRUE
created_at TIMESTAMPTZ DEFAULT NOW()
```

## Quick Reference

| Task | Command / Syntax | Notes |
|------|------------------|-------|
| Generate version | `date -u +%Y%m%d%H%M%S` | timestamp version |
| Apply migrations | `./mvnw flyway:migrate` | also runs at app startup |
| Migration status | `./mvnw flyway:info` | history table |
| Validate checksums | `./mvnw flyway:validate` | detects edited files |
| Repair history | `./mvnw flyway:repair` | after a failure |
| Create table | `CREATE TABLE` | versioned file |
| Add column | `ALTER TABLE ... ADD COLUMN` | versioned file |
| Drop column | `ALTER TABLE ... DROP COLUMN` | versioned file |
| Rename column | `ALTER TABLE ... RENAME COLUMN` | versioned file |
| Add index | `CREATE INDEX` | `CONCURRENTLY` for big tables |
| Add FK | `ALTER TABLE ... ADD CONSTRAINT` | versioned file |
| Rollback | new forward migration | no auto-undo in Community |
| File prefix | `V` versioned, `R` repeatable | `V<ver>__<name>.sql` |
