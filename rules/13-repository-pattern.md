# Repository Pattern Rules - iGoGo Backend

## Overview

Repositories provide a clean abstraction layer for database operations, separating data access from business logic. We use **Spring Data JPA**: a repository is an **interface** extending `JpaRepository` (and optionally `JpaSpecificationExecutor`), and Spring generates the implementation at runtime. Queries are type-safe through derived method names, JPQL `@Query`, or the Criteria/Specification API — and all parameters are bound, so there is no SQL injection risk.

## File Structure

```
src/main/java/com/igogo/<feature>/repository/
├── UserRepository.java
├── UserAccountRepository.java
└── projection/UserSummary.java   # interface/record projections
```

## Naming Conventions

### File / Class Names

```java
// Good
UserRepository.java          -> public interface UserRepository extends JpaRepository<User, UUID> {}
AuthRepository.java
SmsDeliveryRepository.java

// Bad
User.java                    // ❌ missing Repository suffix
UserRepo.java                // ❌ abbreviated suffix
userRepository.java          // ❌ wrong case
```

### Method Names

Spring Data derives queries from method names, so names must follow the convention (`findBy`, `existsBy`, `countBy`, `deleteBy`).

```java
// Good - derived, self-documenting
Optional<User> findByPhoneNumber(String phoneNumber);
Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);
boolean existsByEmail(String email);
boolean existsByEmailAndIdNot(String email, UUID id);
long countByStatus(UserAccountStatus status);

// Bad - won't derive / unclear
Optional<User> get(String phone);            // ❌ not a valid prefix
List<User> users();                          // ❌ no query intent
User findUser(Object criteria);              // ❌ untyped, ambiguous
```

## Basic Structure

### Template

```java
package com.igogo.user.repository;

import com.igogo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * User Repository - data access for user management.
 * Spring Data JPA generates the implementation at runtime.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    // Derived query: active users only
    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    // JPQL with a JOIN FETCH to load the account + role eagerly for login
    @Query("""
        SELECT u FROM User u
        JOIN FETCH u.account a
        JOIN FETCH a.role r
        LEFT JOIN FETCH a.subRole sr
        WHERE u.phoneNumber = :phoneNumber AND u.deletedAt IS NULL
        ORDER BY a.createdAt DESC
        """)
    Optional<User> findUserWithRoleByPhone(@Param("phoneNumber") String phoneNumber);

    // Pageable search
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    // Bulk update (must run in a transaction; see Service rules)
    @Modifying
    @Query("UPDATE UserAccount a SET a.status = :status, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :accountId")
    int updateAccountStatus(@Param("accountId") UUID accountId, @Param("status") UserAccountStatus status);
}
```

> Spring Data automatically creates the bean — you don't annotate with `@Repository` strictly (it's optional on Spring Data interfaces) but it documents intent and translates persistence exceptions.

## Dependency Injection

Inject the repository interface; never construct a `DataSource`/`EntityManager` yourself.

```java
// Good
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
}

// Bad - manual wiring of persistence infrastructure ❌
```

## Query Patterns

### 1. Derived Queries (preferred for simple cases)

```java
Optional<User> findByEmail(String email);
List<User> findByDeletedAtIsNull();
List<User> findByStatusAndDeletedAtIsNull(UserAccountStatus status);
boolean existsByPhoneNumber(String phoneNumber);
```

### 2. Projections (select specific fields)

Return only the columns you need with an interface or record projection — the JPA equivalent of selecting specific fields.

```java
public interface UserSummary {
    UUID getId();
    String getFullName();
    String getEmail();
}

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<UserSummary> findSummaryById(UUID id);
    List<UserSummary> findByDeletedAtIsNull();
}
```

```java
// Record (DTO) projection via JPQL constructor expression
@Query("SELECT new com.igogo.user.dto.response.UserListItem(u.id, u.email, u.fullName, u.isActive) FROM User u")
List<UserListItem> findAllListItems();
```

### 3. JOIN Queries

```java
// JOIN FETCH to avoid N+1 and load relations eagerly
@Query("""
    SELECT u FROM User u
    JOIN FETCH u.account a
    JOIN FETCH a.role r
    WHERE u.id = :userId
    """)
Optional<User> findWithRole(@Param("userId") UUID userId);

// Optional relation via LEFT JOIN
@Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.id = :userId")
Optional<User> findWithOptionalProfile(@Param("userId") UUID userId);
```

Alternatively use `@EntityGraph` to declare what to fetch without writing JPQL:

```java
@EntityGraph(attributePaths = {"account", "account.role"})
Optional<User> findById(UUID id);
```

### 4. WHERE Conditions

```java
// Single condition (derived)
List<User> findByDeletedAtIsNull();

// Multiple conditions (derived)
Optional<User> findByPhoneNumberAndAccount_RoleIdAndDeletedAtIsNull(String phone, UUID roleId);

// OR (derived)
Optional<User> findByPhoneNumberOrEmail(String phone, String email);

// Complex condition (JPQL)
@Query("SELECT u FROM User u WHERE (u.phoneNumber = :id OR u.email = :id) AND u.deletedAt IS NULL")
Optional<User> findByIdentifier(@Param("id") String identifier);
```

For dynamic combinations of optional filters, use **Specifications** (see Service rules, section "Dynamic Queries") instead of many overloaded methods.

### 5. INSERT / Save

JPA persists entities through `save`. There is no separate "insert" — a new entity (null id) is inserted, an existing one is updated.

```java
// In the service
User user = new User();
user.setFullName(request.fullName());
user.setPhoneNumber(request.phoneNumber());
user.setEmail(request.email());      // null is fine for optional columns
User saved = userRepository.save(user);   // INSERT, returns managed entity with generated id
```

> Use `null` (not a magic value) for absent optional columns; the column must be nullable in the schema.

### 6. UPDATE

Preferred: load the entity, mutate it inside a transaction — JPA dirty-checking flushes the change on commit. No explicit `save` needed for a managed entity, though calling `save` is harmless and explicit.

```java
@Transactional
public UserResponse updateStatus(UUID accountId, UserAccountStatus status) {
    UserAccount account = accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("account.notFound"));
    account.setStatus(status);          // dirty-checked, flushed on commit
    return accountMapper.toResponse(account);
}
```

Bulk update without loading entities — use `@Modifying @Query` (bypasses the persistence context, so clear it if needed):

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE UserAccount a SET a.password = :pwd, a.updatedAt = CURRENT_TIMESTAMP WHERE a.userId = :userId")
int updatePassword(@Param("userId") UUID userId, @Param("pwd") String hashedPassword);
```

### 7. DELETE

```java
// Soft delete (recommended) - set a flag, run in a transaction
@Transactional
public void softDelete(UUID id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    user.setDeletedAt(Instant.now());
}

// Hard delete (use with caution)
void deleteById(UUID id);   // inherited from JpaRepository

// Conditional bulk delete
@Modifying
@Query("DELETE FROM VerificationCode v WHERE v.isUsed = true AND v.usedAt < :cutoff")
int deleteUsedOlderThan(@Param("cutoff") Instant cutoff);
```

> Consider Hibernate `@SQLDelete` + `@Where(clause = "deleted_at IS NULL")` to make soft-delete transparent across all queries.

## Transactions

Transaction boundaries belong in the **service** layer (`@Transactional`), not the repository. A single `JpaRepository` call is already transactional; wrap multiple calls that must be atomic.

```java
// In the service - atomic multi-step write
@Transactional
public User createUserWithAccount(CreateUserRequest request, UUID roleId) {
    User user = userRepository.save(toUser(request));
    UserAccount account = new UserAccount();
    account.setUser(user);
    account.setRoleId(roleId);
    accountRepository.save(account);
    return user;   // any exception rolls back both inserts
}
```

Do NOT wrap a single read in a transaction unnecessarily — a derived `findById` is fine on its own (use `@Transactional(readOnly = true)` on the service method that groups reads).

## Reusable Query Logic (Specifications)

Replace ad-hoc helper predicates with reusable `Specification`s:

```java
public final class UserSpecifications {
    private UserSpecifications() {}

    public static Specification<User> active() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<User> phoneEquals(String phone) {
        return (root, query, cb) -> cb.equal(root.get("phoneNumber"), phone);
    }
}

// Usage
userRepository.findAll(UserSpecifications.active().and(UserSpecifications.phoneEquals(phone)));
```

## Field Naming Conventions

The entity uses camelCase fields; the DB uses snake_case columns. JPA maps between them via `@Column(name = ...)` or a `CamelCaseToUnderscoresNamingStrategy`. Queries and projections always use the **Java field names**.

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue private UUID id;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;     // phone_number in DB

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;       // created_at in DB
}
```

## Return Types

### Use `Optional<T>` for single results — NOT null, NOT lists

This is the JPA replacement for the NestJS "always return an array and check `.length`" rule. Returning `Optional` makes "absent" explicit and prevents null-handling bugs.

```java
// Good - Optional for single result
Optional<User> findByPhoneNumber(String phoneNumber);

// In the service: handle absence explicitly
User user = userRepository.findByPhoneNumber(phone)
    .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

// Existence checks: return boolean, don't fetch
boolean existsByEmail(String email);
if (userRepository.existsByEmail(email)) {
    throw new ConflictException("user.emailExists");
}

// Lists for multiple results - check isEmpty(), never null
List<User> users = userRepository.findByStatus(status);
if (users.isEmpty()) { /* ... */ }
```

```java
// Bad - returning null or an entity that may be null
User findByPhoneNumber(String phoneNumber);   // ❌ caller may NPE
```

> Spring Data never returns `null` for `Optional`/`List` return types — it returns an empty `Optional`/empty list. Lean on that.

## Documentation

### Javadoc (Required on non-derived methods)

Derived methods are self-documenting; add Javadoc for `@Query`/`@Modifying` methods and the repository type itself.

```java
/**
 * Repository for {@link User}. Spring Data JPA provides the implementation.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Loads a user together with account, role, and sub-role for login flows.
     *
     * @param phoneNumber the user's phone number
     * @return the matching active user, or empty if none
     */
    @Query("...")
    Optional<User> findUserWithRoleByPhone(@Param("phoneNumber") String phoneNumber);
}
```

## Best Practices

### 1. Single Responsibility

```java
// Good - focused, intention-revealing methods
Optional<User> findByPhoneNumber(String phone);
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);

// Bad - one generic catch-all method ❌
List<User> findUser(Object criteria);
```

### 2. Type Safety & Projections

Select only the fields you need with projections instead of always loading whole entities for read-only views.

### 3. Consistent Method Naming

```java
findBy*    // SELECT (Optional/List)
existsBy*  // boolean existence check
countBy*   // long count
deleteBy*  // delete
```

### 4. Reuse with Specifications

Extract repeated predicates (e.g. "active") into `Specification`s rather than duplicating `deletedAtIsNull` everywhere.

### 5. Handle Nullable Columns Correctly

Map optional columns as nullable; set `null` for absent values. Don't invent sentinel values.

### 6. Keep Transactions in the Service

Repositories expose data access; the service decides atomic boundaries with `@Transactional`. Rollback is automatic on runtime exceptions.

### 7. No Business Logic in Repositories

```java
// Good - repository only queries
Optional<User> findByPhoneNumber(String phone);

// Bad - throwing business exceptions / checking status inside a repository ❌
```

### 8. Prefer Optional/exists over Fetch-then-check

```java
// Good
if (userRepository.existsByEmail(email)) { ... }
userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

// Bad - fetch a list then inspect size, or compare against null ❌
```

## Service Integration

### Use in Service

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findUserWithRoleByPhone(request.phoneNumber())
            .orElseThrow(() -> new UnauthorizedException("auth.invalidCredentials"));
        // ... verify password, issue tokens
        return buildAuthResponse(user);
    }
}
```

No "module registration" is needed — `@EnableJpaRepositories` (implicit via `@SpringBootApplication`) scans and registers all repository interfaces.

## Quick Reference

| Pattern | Usage | Example |
|---------|-------|---------|
| `JpaRepository<T, ID>` | Base repository | `interface UserRepository extends JpaRepository<User, UUID>` |
| Derived query | Auto-generated query | `Optional<User> findByEmail(String email)` |
| `existsBy*` | Existence check | `boolean existsByEmail(String email)` |
| `@Query` (JPQL) | Custom query | `@Query("SELECT u FROM User u WHERE ...")` |
| `@Modifying` | Bulk update/delete | `@Modifying @Query("UPDATE ...")` |
| `JpaSpecificationExecutor` | Dynamic queries | `findAll(spec, pageable)` |
| `@EntityGraph` | Eager fetch plan | `@EntityGraph(attributePaths = {"account"})` |
| Projection | Partial select | interface/record projection |
| `Pageable` / `Page<T>` | Pagination & sorting | `Page<User> findAll(Pageable p)` |
| `Optional<T>` | Single result | never returns null |
| `@Transactional` (service) | Atomic operations | rollback on runtime exception |

## Migration from Raw SQL

### Before (raw JDBC / native SQL in service)

```java
String sql = """
    SELECT u.*, ua.id AS account_id, r.name AS role_name
    FROM users u
    INNER JOIN user_accounts ua ON u.id = ua.user_id
    INNER JOIN roles r ON ua.role_id = r.id
    WHERE u.phone_number = ?
    """;
// manual ResultSet mapping, manual null checks...
```

### After (Spring Data JPA repository)

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("""
        SELECT u FROM User u
        JOIN FETCH u.account a
        JOIN FETCH a.role r
        WHERE u.phoneNumber = :phoneNumber
        """)
    Optional<User> findUserWithRoleByPhone(@Param("phoneNumber") String phoneNumber);
}

// Service
User user = userRepository.findUserWithRoleByPhone(request.phoneNumber())
    .orElseThrow(() -> new UnauthorizedException("auth.invalidCredentials"));
```

## Benefits

1. **Type Safety** — compile-time checking of entities and JPQL (with IDE support)
2. **No SQL Injection** — all parameters are bound automatically
3. **Maintainability** — data access separated from business logic
4. **Testability** — repositories are easily mocked, or tested with `@DataJpaTest`
5. **Less boilerplate** — CRUD, paging, and sorting provided by `JpaRepository`
6. **Dynamic queries** — Specifications compose optional filters cleanly
7. **Auditing & soft delete** — supported via JPA/Hibernate annotations
8. **Transaction safety** — declarative `@Transactional` with automatic rollback
