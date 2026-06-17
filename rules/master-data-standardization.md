# Master Data Standardization Rules

> **⚠️ IMPORTANT UPDATE (2025-01-28)**
>
> Quy tắc này đã được cập nhật để **SỬ DỤNG `id` TRỰC TIẾP** trong business logic thay vì `code`.
>
> **Những gì THAY ĐỔI:**
>
> - ~~Rule 7: Sử dụng code trong business logic~~ → **DEPRECATED**
> - DTOs nhận `roleId` (Integer/enum) thay vì `roleCode: String`
> - Services dùng `id` trực tiếp thay vì lookup `code → id`
>
> **Những gì VẪN GIỮ:**
>
> - Vẫn có trường `code` trong schema (để reference & docs)
> - Vẫn prefix `mtb_` cho tên bảng
> - Vẫn dùng seeder/migration với `ON CONFLICT (code)`
> - Vẫn trả về cả `id`, `code`, `name` trong response

## Mục đích (Purpose)

Quy tắc này định nghĩa chuẩn cho **tất cả bảng master data** (bảng danh mục tĩnh/lookup tables) trong hệ thống iGoGo Backend (Spring Boot + JPA + Flyway) để đảm bảo:

- **Tính nhất quán**: Đặt tên và cấu trúc thống nhất
- **Tính ổn định**: Sử dụng Java enum với `id` cho type safety
- **Khả năng bảo trì**: Dễ dàng thêm/sửa/xóa master data mà không ảnh hưởng đến code

---

## Định nghĩa Master Data

**Master data** là các bảng chứa dữ liệu tham chiếu tĩnh (reference/lookup data), ít thay đổi, dùng để phân loại/làm danh mục.

### Ví dụ Master Data Tables

- Giới tính (Gender), Vai trò (Roles), Loại phòng (Room Types), Loại ảnh/video (Media Categories), Tiện ích (Amenities), Tỉnh/Thành (Provinces), Phường/Xã (Wards)

### KHÔNG phải Master Data

- Bảng transactional: `dtb_users`, `dtb_bookings`, `dtb_payments`, `dtb_user_accounts`
- Bảng log/audit: `dtb_verification_codes`, `dtb_audit_logs`
- Bảng mapping nhiều-nhiều: `dtb_user_roles_mapping`, `dtb_room_amenities`

---

## Rule 1: Naming Convention — Tiền tố `mtb_`

Tất cả bảng master data **PHẢI** bắt đầu bằng `mtb_` (Master TaBle). Map qua `@Table(name = ...)`.

```java
// ✅ ĐÚNG
@Entity @Table(name = "mtb_genders")     public class Gender { ... }
@Entity @Table(name = "mtb_room_types")  public class RoomType { ... }
@Entity @Table(name = "mtb_roles")       public class Role { ... }

// ❌ SAI
@Entity @Table(name = "genders")  public class Gender { ... }
@Entity @Table(name = "roles")    public class Role { ... }
```

**Lý do:** phân biệt rõ master vs transactional, tránh xung đột tên, dễ filter/backup/restore.

---

## Rule 2: Trường `code` — Unique Identifier

Tất cả bảng master data **PHẢI** có trường `code` (varchar, unique) làm identifier ổn định + `id` làm khóa chính.

```java
@Entity
@Table(name = "mtb_table_name",
    indexes = {
        @Index(name = "idx_mtb_table_name_code", columnList = "code", unique = true),
        @Index(name = "idx_mtb_table_name_name", columnList = "name")
    })
@Getter @Setter
public class TableName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    // ... baseTimestamps (created_at, updated_at, deleted_at) via a @MappedSuperclass
}
```

### Chi tiết các trường

- **`id`** — `Integer` IDENTITY. Khóa chính cho relationships/FK.
- **`code`** — `varchar(50)`, `NOT NULL`, `UNIQUE`, có unique index. Identifier ổn định để reference.
- **`name`** — `varchar(255)`, tên hiển thị (tiếng Việt), có index để search/sort.

Đặt timestamps chung qua `@MappedSuperclass`:

```java
@MappedSuperclass
@Getter @Setter
public abstract class BaseMasterEntity {
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

---

## Rule 3: Code Naming Convention

### Format cho `code`

```java
// Option 1: PascalCase (giá trị đơn giản)
"Male", "Female", "Other"
"SingleRoom", "DoubleRoom", "Suite", "Dormitory"

// Option 2: UPPER_SNAKE_CASE (giá trị phức tạp)
"USER", "SELLER", "PARTNER", "EMPLOYEE"
"TEAM_LEAD_MEDIA", "INDIVIDUAL_MARKET_DEV"
"WIFI", "AIR_CONDITIONER", "PARKING"
```

**Quy tắc:** ngắn gọn (≤ 50 ký tự), mô tả chính xác, không đổi sau deploy, chỉ A-Z/0-9/underscore, tránh số vô nghĩa (`'1'`, `'2'`).

### Ví dụ ánh xạ id → code

```
mtb_roles: {1, USER, Khách hàng} {2, SELLER, Chủ nhà} {3, PARTNER, Cộng tác viên} {4, EMPLOYEE, Nhân viên}
mtb_room_types: {1, SINGLE, Phòng đơn} {2, DOUBLE, Phòng đôi} {3, SUITE, Phòng suite} {4, DORMITORY, Phòng tập thể}
```

---

## Rule 4: Migration Patterns (Flyway)

### Pattern 1: Tạo mới bảng master data

```sql
-- V..._create_mtb_table_name.sql
CREATE TABLE IF NOT EXISTS mtb_table_name (
    id         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_mtb_table_name_code ON mtb_table_name (code);   -- quan trọng nhất
CREATE INDEX idx_mtb_table_name_name ON mtb_table_name (name);
CREATE INDEX idx_mtb_table_name_deleted_at ON mtb_table_name (deleted_at);
```

> Flyway Community không có auto-rollback; nếu cần revert hãy tạo migration forward mới (xem 08-database-migration.md).

### Pattern 2: Cập nhật bảng cũ (thêm `code`, đổi tên)

```sql
-- V..._standardize_old_table.sql
ALTER TABLE old_table_name RENAME TO mtb_table_name;

ALTER TABLE mtb_table_name ADD COLUMN IF NOT EXISTS code VARCHAR(50);

UPDATE mtb_table_name
SET code = CASE id
    WHEN 1 THEN 'CODE_VALUE_1'
    WHEN 2 THEN 'CODE_VALUE_2'
    ELSE CONCAT('CODE_', id)
END
WHERE code IS NULL;

ALTER TABLE mtb_table_name ALTER COLUMN code SET NOT NULL;
ALTER TABLE mtb_table_name ADD CONSTRAINT uq_mtb_table_name_code UNIQUE (code);
CREATE UNIQUE INDEX IF NOT EXISTS idx_mtb_table_name_code ON mtb_table_name (code);
```

---

## Rule 5: Seeder Patterns

Master/reference data tối thiểu, cần cho FK integrity, có thể seed ngay trong Flyway migration với UPSERT `ON CONFLICT (code)` (xem ngoại lệ trong 08-database-migration.md). Dữ liệu lớn/đổi theo môi trường → seed qua `CommandLineRunner` gated bằng `@Profile`.

### Seeding trong migration (UPSERT theo code)

```sql
INSERT INTO mtb_table_name (code, name, created_at, updated_at)
VALUES
    ('CODE_1', 'Display Name 1', NOW(), NOW()),
    ('CODE_2', 'Display Name 2', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = NOW();
```

### Ví dụ: Seeding Roles

```sql
INSERT INTO mtb_roles (code, name, description, created_at, updated_at)
VALUES
    ('USER',     'Khách hàng',    'Platform customers who book homestays', NOW(), NOW()),
    ('SELLER',   'Chủ nhà',       'Homestay/property owners',              NOW(), NOW()),
    ('PARTNER',  'Cộng tác viên', 'Partner/affiliate collaborators',       NOW(), NOW()),
    ('EMPLOYEE', 'Nhân viên',     'Platform employees/staff',              NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();
```

### Seeder qua application (cho dữ liệu không bắt buộc FK)

```java
@Configuration
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class RoomTypeSeeder {
    private final RoomTypeRepository repository;

    @Bean
    CommandLineRunner seedRoomTypes() {
        return args -> Stream.of(
                new RoomType("SINGLE", "Phòng đơn"),
                new RoomType("DOUBLE", "Phòng đôi"))
            .forEach(rt -> repository.findByCode(rt.getCode())
                .orElseGet(() -> repository.save(rt)));   // idempotent
    }
}
```

---

## Rule 6: Schema Definitions (JPA Entity)

### Template cho Master Data Entity

```java
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Master Data: [Table Description]
 * Stores static reference data for [purpose].
 */
@Entity
@Table(name = "mtb_table_name",
    indexes = {
        @Index(name = "idx_mtb_table_name_code", columnList = "code", unique = true),
        @Index(name = "idx_mtb_table_name_name", columnList = "name")
    })
@Getter @Setter
public class MasterTableName extends BaseMasterEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;                         // business identifier (REQUIRED)

    @Column(nullable = false)
    private String name;                         // tên hiển thị (tiếng Việt, REQUIRED)

    @Column(name = "name_en")
    private String nameEn;                       // i18n (English, nullable)

    @Column(length = 500)
    private String description;                  // optional (Vietnamese)

    @Column(name = "description_en", length = 500)
    private String descriptionEn;                // optional (English)
}
```

### Rule 6.1: Internationalization (i18n) Fields — MANDATORY

Tất cả master data tables **PHẢI** có trường `_en` cho các trường text/varchar hiển thị. Trường tiếng Việt REQUIRED, trường tiếng Anh nullable. camelCase trong entity, snake_case trong DB.

| Vietnamese | English | Java field | DB column | Required |
|-----------|---------|-----------|-----------|----------|
| `name` | `nameEn` | `String nameEn` | `name_en VARCHAR(255)` | No |
| `description` | `descriptionEn` | `String descriptionEn` | `description_en VARCHAR(500)` | No |
| `title` | `titleEn` | `String titleEn` | `title_en VARCHAR(255)` | No |
| `notes` | `notesEn` | `String notesEn` | `notes_en TEXT` | No |

### Ví dụ: Gender Entity

```java
@Entity
@Table(name = "mtb_genders",
    indexes = {
        @Index(name = "idx_mtb_genders_code", columnList = "code", unique = true),
        @Index(name = "idx_mtb_genders_name", columnList = "name")
    })
@Getter @Setter
public class Gender extends BaseMasterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;
}
```

---

## ~~Rule 7: Business Logic — SỬ DỤNG `code` THAY VÌ `id`~~ (DEPRECATED)

> **⚠️ RULE NÀY ĐÃ BỊ BỎ.** Dùng `id` trực tiếp trong business logic để đơn giản hóa (không cần lookup code → id), performance tốt hơn, phù hợp multi-role. Vẫn giữ `code` để reference/docs; DTOs và API dùng `id`.

### ✅ CÁCH MỚI (Dùng id trực tiếp)

```java
// Enum ánh xạ theo id của mtb_roles
public enum UserRole {
    USER(1), SELLER(2), PARTNER(3), EMPLOYEE(4), DEVELOPMENT_PARTNER(5);
    private final int id;
    UserRole(int id) { this.id = id; }
    public int getId() { return id; }
}

// DTO nhận id
public record LoginRequest(
    @NotNull Integer roleId,
    Integer subRoleId
) {}

// Validate bằng id
if (request.roleId() == UserRole.PARTNER.getId() && request.subRoleId() == null) {
    throw new BadRequestException("auth.partnerRequiresSubRole");
}

// Query trực tiếp bằng id
Optional<UserAccount> account = userAccountRepository.findByRoleId(request.roleId());
```

### Tại sao bỏ rule code-based?

1. **Complexity** — bớt 1 bước lookup (code → id) trước mỗi query.
2. **Performance** — không query thêm để convert code → id.
3. **ID stability** — với IDENTITY + seeder UPSERT theo code, id đủ ổn định.
4. **Type safety** — enum cho type checking tốt hơn string codes.

---

## Rule 8: DTOs & API Contracts (UPDATED)

### Input DTOs: nhận `id` từ client

```java
public record CreateUserRequest(

    @NotNull
    @Schema(description = "Role ID", example = "1")
    Integer roleId,

    @Schema(description = "Sub-role ID (optional)", example = "1")
    Integer subRoleId
) {}
```

> Jackson tự convert query/body `String → Integer`; không cần `@Transform`. Có thể validate `roleId` thuộc tập hợp lệ bằng custom constraint (xem 06-validation.md).

### Response DTOs: trả về cả `id`, `code`, `name`

```java
public record RoleResponse(
    @Schema(example = "1") Integer id,
    @Schema(example = "USER") String code,
    @Schema(example = "Khách hàng") String name
) {}

public record UserResponse(
    UUID id,
    String name,
    Integer roleId,
    String roleName,
    RoleResponse role      // optional: nested master data nếu cần full info
) {}
```

---

## Rule 9: Foreign Keys — Dùng `id` trực tiếp (UPDATED)

### Schema: FK reference tới `id` master data (RESTRICT — xem 14-database-schema.md)

```java
@Entity
@Table(name = "dtb_users")
@Getter @Setter
public class User {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_dtb_users_role_id"))   // ON DELETE RESTRICT ở migration
    private Role role;
}
```

### Service: dùng `id` trực tiếp, không cần lookup

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // FK constraint sẽ tự check, nhưng kiểm tra trước cho thông báo thân thiện
        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> new BadRequestException("role.invalid"));

        User user = new User();
        user.setName(request.name());
        user.setRole(role);                       // gán entity theo id
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findWithRoleById(userId)   // JOIN FETCH role
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        return userMapper.toResponse(user);                   // trả về id, code, name của role
    }
}
```

---

## Rule 10: Constants & Enums (UPDATED)

### Pattern: enum cho master data IDs

```java
// com/igogo/common/constant/enums/UserRole.java
public enum UserRole {
    USER(1), SELLER(2), PARTNER(3), EMPLOYEE(4), DEVELOPMENT_PARTNER(5);
    private final int id;
    UserRole(int id) { this.id = id; }
    public int getId() { return id; }
}

public enum UserAccountStatus { ACTIVE, INACTIVE, SUSPEND, WAIT_APPROVE }

public enum Gender { MALE, FEMALE, OTHER }
```

### Optional: constants cho codes (reference only)

```java
public final class RoleCodes {
    public static final String USER = "USER";
    public static final String SELLER = "SELLER";
    public static final String PARTNER = "PARTNER";
    public static final String EMPLOYEE = "EMPLOYEE";
    private RoleCodes() {}
}
```

### Usage

```java
if (request.roleId() == UserRole.PARTNER.getId() && request.subRoleId() == null) {
    throw new BadRequestException("auth.partnerRequiresSubRole");
}

List<Integer> allowed = List.of(UserRole.USER.getId(), UserRole.SELLER.getId());
if (!allowed.contains(request.roleId())) {
    throw new BadRequestException("role.notAllowed");
}

if (account.getStatus() == UserAccountStatus.SUSPEND) {
    throw new UnauthorizedException("auth.accountSuspended");
}
```

---

## Checklist: Tạo Master Data Table Mới

- [ ] **Đặt tên bảng** với prefix `mtb_` (qua `@Table(name=...)`)
- [ ] **Tạo entity** với 3 trường bắt buộc: `id`, `code`, `name` (+ `nameEn`)
- [ ] **Unique index** trên `code`
- [ ] **Flyway migration** (CREATE TABLE + indexes)
- [ ] **Seed** master tối thiểu (migration UPSERT) hoặc `CommandLineRunner` (`@Profile`)
- [ ] **Định nghĩa enum** cho id values
- [ ] **DTO** nhận `id`, response trả `id`/`code`/`name`
- [ ] **Service** query bằng `id`
- [ ] **springdoc** docs với example values
- [ ] **Tests** validate id, query by id/code

## Checklist: Chuyển đổi Master Data Table Cũ

- [ ] **Đổi tên bảng** thêm prefix `mtb_` (Flyway)
- [ ] **Thêm `code`** vào entity + migration
- [ ] **Populate code** (migration tạm hoặc seeder)
- [ ] **Unique constraint + index** cho `code`
- [ ] **Seeder** sang UPSERT theo `code`
- [ ] **Chuyển logic** sang dùng `id`/enum
- [ ] **DTOs** nhận `xyzId` (Integer/enum)
- [ ] **API docs** + examples
- [ ] **Tests** + kiểm tra migration trên DB staging

---

## Ví dụ Thực Tế: 3 Bảng Master Data

### 1. mtb_genders (Giới tính)

```java
@Entity @Table(name = "mtb_genders",
    indexes = @Index(name = "idx_mtb_genders_code", columnList = "code", unique = true))
@Getter @Setter
public class Gender extends BaseMasterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false) private String name;
}
```

```sql
-- migration
CREATE TABLE mtb_genders (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL, name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW(), deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_mtb_genders_code ON mtb_genders (code);

INSERT INTO mtb_genders (code, name) VALUES ('Male','Nam'),('Female','Nữ'),('Other','Khác')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
```

### 2. mtb_roles (Vai trò người dùng)

```java
@Entity @Table(name = "mtb_roles",
    indexes = @Index(name = "idx_mtb_roles_code", columnList = "code", unique = true))
@Getter @Setter
public class Role extends BaseMasterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false) private String name;
    @Column(length = 500) private String description;
}
```

```sql
INSERT INTO mtb_roles (code, name, description) VALUES
  ('USER','Khách hàng','Platform customers who book homestays'),
  ('SELLER','Chủ nhà','Homestay/property owners'),
  ('PARTNER','Cộng tác viên','Partner/affiliate collaborators'),
  ('EMPLOYEE','Nhân viên','Platform employees/staff')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;
```

### 3. mtb_room_types (Loại phòng)

```java
@Entity @Table(name = "mtb_room_types",
    indexes = @Index(name = "idx_mtb_room_types_code", columnList = "code", unique = true))
@Getter @Setter
public class RoomType extends BaseMasterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false) private String name;
    @Column(length = 500) private String description;
    @Column(name = "max_guests") private Integer maxGuests = 2;
}
```

```sql
INSERT INTO mtb_room_types (code, name, description, max_guests) VALUES
  ('SINGLE','Phòng đơn','Single occupancy room',1),
  ('DOUBLE','Phòng đôi','Double occupancy room',2),
  ('SUITE','Phòng suite','Luxury suite room',4),
  ('DORMITORY','Phòng tập thể','Shared dormitory room',8)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, max_guests = EXCLUDED.max_guests;
```

---

## Lợi ích của Quy Tắc Này (UPDATED)

1. **Tính ổn định** — giữ `code` để reference; IDENTITY giữ `id` ổn định; seeder/migration UPSERT theo `code`.
2. **Readability** — `if (user.getRole().getId() == UserRole.SELLER.getId())` thay vì magic number `2`.
3. **Performance** — không lookup `code → id` mỗi request; query bằng primary key indexed.
4. **Type Safety** — Java enum cho compile-time checking, IDE autocomplete.
5. **API Simplicity** — client gửi `{ "roleId": 1 }`; response trả `{ "roleId": 1, "roleName": "Khách hàng", "role": { "code": "USER" } }`.

---

## Tham Khảo (References)

- **Spring Data JPA**: <https://docs.spring.io/spring-data/jpa/reference/>
- **Flyway**: <https://documentation.red-gate.com/flyway>
- **PostgreSQL Naming**: <https://www.postgresql.org/docs/current/sql-syntax-lexical.html>

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-01-26 | Team | Initial master data standardization rules |
| 2.0.0 | 2026-06-17 | Team | Migrated examples to Spring Boot (JPA + Flyway), id-based logic |

---

**Lưu ý cuối:** Quy tắc này là **MANDATORY** cho tất cả master data tables. Mọi PR vi phạm sẽ bị reject trong code review.
