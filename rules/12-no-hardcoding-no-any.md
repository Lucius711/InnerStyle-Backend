# Rule 12: No Hardcoding and No Raw `Object`

## Overview

This rule prevents hardcoded values and the use of raw, untyped `Object` / `Map<String, Object>` in the codebase. In Java these practices improve maintainability, type safety, and code quality — the equivalent of TypeScript's "no `any`" is "use real types and generics, not `Object`".

## 1. No Hardcoding

### 1.1 Magic Numbers and Strings

**Bad:**

```java
if (user.getStatus() == 2) {   // What does 2 mean?
    throw new UnauthorizedException("auth.accountInactive");
}

if (otp.getExpiresAt().isBefore(Instant.now().plusMillis(10 * 60 * 1000))) {  // magic number
    // ...
}
```

**Good:**

```java
if (user.getStatus() == UserAccountStatus.INACTIVE) {
    throw new UnauthorizedException("auth.accountInactive");
}

if (otp.getExpiresAt().isBefore(Instant.now().plus(AppConstants.OTP_EXPIRY))) {
    // ...
}
```

### 1.2 Use Enums for Fixed Values

**Bad:**

```java
Map<String, List<Integer>> rolePathPatterns = Map.of(
    "/user/", List.of(1),
    "/seller/", List.of(2),
    "/partner/", List.of(3));
```

**Good:**

```java
// enum RolePathPrefix.java
public enum RolePathPrefix {
    USER("/user/"), SELLER("/seller/"), PARTNER("/partner/");
    private final String path;
    RolePathPrefix(String path) { this.path = path; }
    public String getPath() { return path; }
}

// constant: map prefixes to roles
public static final Map<RolePathPrefix, List<UserRole>> ROLE_PATH_PATTERNS = Map.of(
    RolePathPrefix.USER,    List.of(UserRole.USER),
    RolePathPrefix.SELLER,  List.of(UserRole.SELLER),
    RolePathPrefix.PARTNER, List.of(UserRole.PARTNER));
```

### 1.3 Configuration Values

Move tunable/secret values into `@ConfigurationProperties` (or constants for true compile-time constants) — never inline them.

**Bad:**

```java
String hashed = new BCryptPasswordEncoder(12).encode(password);  // hardcoded strength

emailService.send(EmailMessage.builder()
    .subject("Welcome to iGoGo")   // hardcoded subject
    .templateId(1)                 // hardcoded template id
    .build());
```

**Good:**

```java
// constant
public static final int BCRYPT_STRENGTH = 12;

// enum + config for email templates
public enum EmailTemplate { VERIFICATION, PASSWORD_RESET }

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(Map<EmailTemplate, TemplateConfig> templates) {
    public record TemplateConfig(long templateId, String subject) {}
}

// service
String hashed = passwordEncoder.encode(password);  // PasswordEncoder bean built with BCRYPT_STRENGTH

var tpl = emailProperties.templates().get(EmailTemplate.VERIFICATION);
emailService.send(EmailMessage.builder()
    .subject(tpl.subject())
    .templateId(tpl.templateId())
    .build());
```

### 1.4 Public Routes / Path Lists

**Bad:**

```java
private final List<String> publicPaths = List.of("/auth/login", "/auth/register", "/health");
```

**Good:**

```java
// constant
public static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/auth/register", "/health");

// or, better, bind from config so ops can change it without a redeploy
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<String> publicPaths) {}
```

## 2. No Raw `Object` / Untyped Maps

The Java equivalent of "no `any`": don't use `Object`, `Map<String, Object>`, or raw collections as a substitute for a real type.

### 2.1 Define Proper Types

**Bad:**

```java
public Object login(LoginRequest request) {
    Object user = findUser(request.phoneNumber());
    return user;
}

private Object generateTokens(Object payload) { /* ... */ }
```

**Good:**

```java
public record TokenPayload(UUID userId, UUID accountId, UUID roleId) {}
public record AuthTokens(String accessToken, String refreshToken) {}

public AuthResponse login(LoginRequest request) {
    User user = findUser(request.phoneNumber());
    return buildResponse(user);
}

private AuthTokens generateTokens(TokenPayload payload) { /* ... */ }
```

### 2.2 Use Generics

**Bad:**

```java
List process(List data) {                         // raw types
    return (List) data.stream().map(i -> ((Map) i).get("value")).toList();
}
```

**Good:**

```java
public <T extends HasValue> List<String> process(List<T> data) {
    return data.stream().map(HasValue::getValue).toList();
}

interface HasValue { String getValue(); }
```

### 2.3 Don't Catch and Treat Everything as `Object`

**Bad:**

```java
try {
    // ...
} catch (Exception ex) {
    Object detail = ex;            // loses type info
    log.error(detail.toString());
}
```

**Good:**

```java
try {
    // ...
} catch (DataAccessException ex) {          // catch the specific type
    log.error("DB error: {}", ex.getMessage(), ex);
    throw new InternalServerException("common.serverError");
}
```

### 2.4 Query Results

**Bad:**

```java
@Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
Object getUser(@Param("id") UUID id);   // untyped result
```

**Good:**

```java
// Map to an entity or a typed projection (see 13-repository-pattern.md)
Optional<User> findById(UUID id);                       // entity

public interface UserRow { UUID getId(); String getEmail(); String getFullName(); }
Optional<UserRow> findRowById(UUID id);                 // projection
```

## 3. Constants & Config Organization

### 3.1 Structure

```
src/main/java/com/igogo/common/
├── constant/
│   ├── AppConstants.java           # general constants
│   ├── AuthConstants.java          # auth-related constants
│   └── enums/
│       ├── UserRole.java
│       └── EmailTemplate.java
└── config/properties/              # @ConfigurationProperties records
    ├── JwtProperties.java
    └── EmailProperties.java
```

### 3.2 Naming Conventions

- Constants: `UPPER_SNAKE_CASE` (`public static final`)
- Enum types: `PascalCase`; enum constants: `UPPER_SNAKE_CASE`
- Config properties records/classes: `PascalCase` + `Properties` suffix

```java
public final class AppConstants {
    public static final Duration OTP_EXPIRY = Duration.ofMinutes(10);
    public static final int BCRYPT_STRENGTH = 12;
    public static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/auth/register", "/health");

    private AppConstants() {}   // prevent instantiation
}

public enum UserRole { USER, SELLER, PARTNER }

public enum EmailTemplate { VERIFICATION, PASSWORD_RESET }
```

> Use immutable structures: `List.of(...)`, `Map.of(...)`, `record`s. There is no `as const` — immutability comes from `final` fields and immutable collections.

## 4. When Hardcoding Is Acceptable

### 4.1 Type/Record Definitions

```java
// Fine - structure definition, not runtime config
public record User(UUID id, String name) {}
```

### 4.2 Default Method Parameters (via overloads / annotations)

```java
// Spring supplies defaults declaratively
@GetMapping
public Page<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) { ... }
```

### 4.3 Test Files

```java
@Test
void hashesWithStrength12() {
    assertThat(strength).isEqualTo(12);   // hardcoded test values are fine
}
```

## 5. Benefits

- **Maintainability** — single source of truth; change a value once.
- **Type Safety** — compile-time checks, IDE autocomplete, safe refactoring.
- **Consistency** — standardized values; fewer typos and divergences.

## 6. Implementation Checklist

- [ ] Replace magic numbers with named constants / `Duration` / enums
- [ ] Replace magic strings with enums or constants
- [ ] Remove raw `Object` / `Map<String,Object>`; use real types & generics
- [ ] Move tunable/secret values into `@ConfigurationProperties`
- [ ] Make constant collections immutable (`List.of`, `Map.of`)
- [ ] Add a private constructor to constant holder classes
- [ ] Document non-obvious constants
- [ ] Enforce with static analysis (below)

## 7. Static Analysis Enforcement

Enforce these rules with Checkstyle / PMD (see 17-eslint-check-required.md). Relevant rules:

```xml
<!-- Checkstyle (config/checkstyle/checkstyle.xml) -->
<module name="MagicNumber">
    <property name="ignoreNumbers" value="-1, 0, 1"/>
    <property name="ignoreHashCodeMethod" value="true"/>
    <property name="ignoreAnnotation" value="true"/>
</module>
<module name="IllegalType"/>   <!-- discourage raw/loose types -->
```

```xml
<!-- PMD ruleset -->
<rule ref="category/java/codestyle.xml/AvoidUsingHardCodedIP"/>
<rule ref="category/java/bestpractices.xml/AvoidUsingHardCodedCrypto"/>
<rule ref="category/java/design.xml/AvoidDeeplyNestedIfStmts"/>
<rule ref="category/java/errorprone.xml/AvoidUsingOctalValues"/>
```

Also enable compiler lint and treat raw-type/unchecked warnings as errors:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs>
      <arg>-Xlint:all,-processing</arg>
      <arg>-Werror</arg>   <!-- fail build on raw types / unchecked, etc. -->
    </compilerArgs>
  </configuration>
</plugin>
```

## References

- Effective Java (Joshua Bloch) — items on enums, generics, and avoiding raw types
- Spring `@ConfigurationProperties` docs
- 17-eslint-check-required.md (Checkstyle / Spotless gate)
- `com/igogo/common/constant/AuthConstants.java`
- `com/igogo/common/config/properties/EmailProperties.java`
