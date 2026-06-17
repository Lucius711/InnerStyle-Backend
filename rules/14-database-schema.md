# Database Schema Rules - iGoGo Backend

## Overview

This document defines rules for database schema design, naming conventions, foreign key constraints, and data integrity. The schema is created and evolved through Flyway SQL migrations (see 08-database-migration.md) and mapped to JPA entities. **The `ON DELETE` behavior is enforced by the database constraint** (defined in the migration); Hibernate's `@OnDelete` is only a complementary hint.

## Foreign Key Constraints

### onDelete Rules

Foreign key `ON DELETE` behavior follows these rules based on the referenced table type.

#### Master Data Tables (ON DELETE RESTRICT)

Master data tables contain reference/lookup data that must not be deleted while referenced.

**Master Data Tables:**

- `mtb_roles` — System roles
- `mtb_sub_roles` — System sub-roles
- `mtb_provinces` — Vietnam provinces
- `mtb_wards` — Vietnam wards/communes
- Any table prefixed with `mtb_` (master table)

**Rule:** Use `ON DELETE RESTRICT` for foreign keys to master data tables.

**Reason:** Prevents accidental deletion of master data referenced by other records.

```sql
-- Good - migration: master-data reference uses RESTRICT
CREATE TABLE dtb_user_accounts (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id INTEGER NOT NULL,
    sub_role_id INTEGER,
    CONSTRAINT fk_dtb_user_accounts_role_id
        FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dtb_user_accounts_sub_role_id
        FOREIGN KEY (sub_role_id) REFERENCES mtb_sub_roles (id) ON DELETE RESTRICT
);

-- Bad - master data with CASCADE
CONSTRAINT fk_dtb_user_accounts_role_id
    FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE CASCADE   -- ❌ wrong
```

```java
// Corresponding JPA mapping (the DB constraint is the source of truth)
@Entity
@Table(name = "dtb_user_accounts")
public class UserAccount {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_dtb_user_accounts_role_id"))
    private Role role;   // RESTRICT enforced by DB; do NOT add @OnDelete(CASCADE)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_role_id")
    private SubRole subRole;
}
```

#### Regular Tables (ON DELETE CASCADE)

Regular tables contain transactional/operational data that may be deleted together with their parent.

**Rule:** Use `ON DELETE CASCADE` for foreign keys between regular (`dtb_`) tables.

**Reason:** Ensures referential integrity and automatic cleanup of dependent records.

```sql
-- Good - migration: regular-table references use CASCADE
CREATE TABLE dtb_orders (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    account_id UUID NOT NULL,
    CONSTRAINT fk_dtb_orders_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_orders_account_id
        FOREIGN KEY (account_id) REFERENCES dtb_user_accounts (id) ON DELETE CASCADE
);
```

```java
@Entity
@Table(name = "dtb_orders")
public class Order {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_dtb_orders_user_id"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
}
```

> Prefer enforcing CASCADE in the DB (migration). The Hibernate `@OnDelete(CASCADE)` annotation just makes Hibernate generate matching DDL when it's the schema owner; since Flyway owns the schema here, the SQL constraint is authoritative.

### Summary Table

| Referenced Table Type | `ON DELETE` Action | Reason |
|----------------------|--------------------|--------|
| Master data (`mtb_*`) | `RESTRICT` | Prevent deletion of reference data |
| Regular tables (`dtb_*`) | `CASCADE` | Auto-cleanup related records |
| Optional relationship | `SET NULL` | Keep child, clear the reference |

### Migration Examples

#### Master Data Table + RESTRICT reference

```sql
-- V..._create_roles_and_user_accounts.sql
CREATE TABLE mtb_roles (
    id         SERIAL PRIMARY KEY,
    code       VARCHAR(50) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE dtb_user_accounts (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id INTEGER NOT NULL,
    CONSTRAINT fk_dtb_user_accounts_role_id
        FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT
);
```

#### Regular Tables + CASCADE reference

```sql
-- V..._create_users_and_accounts.sql
CREATE TABLE dtb_users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE dtb_user_accounts (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id INTEGER NOT NULL,
    CONSTRAINT fk_dtb_user_accounts_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,    -- regular -> CASCADE
    CONSTRAINT fk_dtb_user_accounts_role_id
        FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT    -- master -> RESTRICT
);
```

## Naming Conventions

### Master Data Tables

- **Prefix:** `mtb_` (master table)
- **Examples:** `mtb_roles`, `mtb_provinces`, `mtb_wards`, `mtb_homestay_content_questions`
- **Contains:** Reference/lookup data that rarely changes
- **Rule:** ALL master data tables MUST use the `mtb_` prefix

### Regular / Transaction Tables

- **Prefix:** `dtb_` (data table)
- **Examples:** `dtb_users`, `dtb_user_accounts`, `dtb_orders`, `dtb_homestay_registrations`
- **Contains:** Transactional/operational data
- **Rule:** ALL regular data tables MUST use the `dtb_` prefix

Map the prefix on the entity with `@Table(name = "...")`:

```java
@Entity @Table(name = "mtb_roles")    public class Role { ... }
@Entity @Table(name = "dtb_users")    public class User { ... }
```

### Why These Prefixes?

1. **Clear identification** — instantly know master vs transactional data.
2. **Foreign key rules** — easy to apply the correct `ON DELETE` behavior.
3. **Database organization** — better structure for large systems.
4. **Migration safety** — prevents accidental deletion of critical reference data.

## Best Practices

### 1. Always Specify ON DELETE

```sql
-- Good
FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT

-- Bad - relies on default (NO ACTION), intent unclear ❌
FOREIGN KEY (role_id) REFERENCES mtb_roles (id)
```

### 2. Name and Document Constraints

```sql
-- Good - descriptive, conventional name
CONSTRAINT fk_dtb_user_accounts_role_id
    FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT

-- Bad - non-descriptive
CONSTRAINT role_fk FOREIGN KEY (role_id) REFERENCES mtb_roles (id)   -- ❌
```

Use the pattern `fk_<table>_<column>`. Mirror the name on the JPA `@JoinColumn(foreignKey = @ForeignKey(name = "..."))`.

### 3. Keep Entity Mapping Consistent with the Constraint

The `@ManyToOne`/`@JoinColumn` must match the DB column and nullability. Do not annotate a RESTRICT relationship with `@OnDelete(CASCADE)`.

### 4. Decide Before Creating a Table

1. Is this master/reference data? → prefix `mtb_`, others reference it with `RESTRICT`.
2. Is it transactional data? → prefix `dtb_`, reference parents with `CASCADE`.
3. Is the relationship optional and the child should outlive the parent? → `SET NULL` (column must be nullable).

## Testing

Test foreign key behavior with `@DataJpaTest` (against a real PostgreSQL via Testcontainers — H2 won't enforce identical FK semantics).

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ForeignKeyConstraintTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired RoleRepository roleRepository;
    @Autowired UserAccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager em;

    @Test
    void preventsDeletingReferencedRole() {
        Role role = roleRepository.save(new Role("TEST", "Test"));
        accountRepository.save(new UserAccount(role));
        em.flush();

        assertThatThrownBy(() -> { roleRepository.deleteById(role.getId()); em.flush(); })
            .isInstanceOf(DataIntegrityViolationException.class);   // RESTRICT
    }

    @Test
    void cascadesAccountDeletionWhenUserDeleted() {
        User user = userRepository.save(new User("+84901234567"));
        UserAccount account = accountRepository.save(new UserAccount(user));
        em.flush();

        userRepository.deleteById(user.getId());
        em.flush();
        em.clear();

        assertThat(accountRepository.findById(account.getId())).isEmpty();  // CASCADE
    }
}
```

## Quick Reference

```sql
-- Master data reference (RESTRICT)
FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT

-- Regular table reference (CASCADE)
FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE

-- Optional relationship (SET NULL) - column must be nullable
FOREIGN KEY (category_id) REFERENCES mtb_categories (id) ON DELETE SET NULL

-- Explicit no action (rarely used)
FOREIGN KEY (x_id) REFERENCES some_table (id) ON DELETE NO ACTION
```

```java
// JPA side
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "role_id", nullable = false,
    foreignKey = @ForeignKey(name = "fk_dtb_user_accounts_role_id"))
private Role role;                       // RESTRICT (master data)

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
@OnDelete(action = OnDeleteAction.CASCADE)
private User user;                       // CASCADE (regular table)
```
