# Naming Conventions - iGoGo Backend

## General Principles

### 1. Be Descriptive and Meaningful

- Names should reveal intent
- Avoid abbreviations unless widely known (HTTP, URL, ID)
- Use full words instead of shortened versions

### 2. Be Consistent

- Follow established patterns in the codebase
- Use the same terminology across the project
- Match naming style with Java / Spring Boot conventions

### 3. Be Searchable

- Avoid single-letter variables (except loop counters)
- Use meaningful names that are easy to search
- Avoid generic names like `data`, `temp`, `result`

## File Naming

### Java Files

In Java, the file name MUST match the public type it declares, in `PascalCase`, with the `.java` extension. There is no kebab-case for Java source files. The "type" of a class is expressed through its **suffix** and its **package**, not through the file name.

**Controllers:** (package `controller`)

```
UserController.java
AuthController.java
OrderHistoryController.java
```

**Services:** (package `service`, implementations in `service.impl`)

```
UserService.java          // interface
EmailService.java         // interface
UserServiceImpl.java      // implementation
EmailServiceImpl.java     // implementation
PaymentProcessingService.java
```

**Repositories:** (package `repository`)

```
UserRepository.java
OrderRepository.java
```

**DTOs:** (package `dto.request` / `dto.response`)

```
dto/request/CreateUserRequest.java
dto/request/UpdateProfileRequest.java
dto/response/UserProfileResponse.java
dto/response/OrderListResponse.java
```

**Entities:** (package `entity`)

```
entity/User.java
entity/Order.java
entity/PaymentStatus.java   // enum
```

**Tests:** (mirror the package under `src/test/java`)

```
UserServiceTest.java          // unit test
UserControllerTest.java       // slice/web test
UserControllerIT.java         // integration test (IT suffix)
```

**Validators:** (package `validator`)

```
validator/ValidPhone.java               // annotation
validator/ValidPhoneValidator.java       // ConstraintValidator
validator/UniqueEmail.java
validator/UniqueEmailValidator.java
```

**Filters / Interceptors / Advice:**

```
exception/GlobalExceptionHandler.java     // @RestControllerAdvice
config/LoggingInterceptor.java            // HandlerInterceptor
security/JwtAuthenticationFilter.java     // OncePerRequestFilter
```

**Flyway Migrations:** (`src/main/resources/db/migration`)

```
V20250118120000__create_users_table.sql
V20250118130000__add_email_verification.sql
```

Format: `V<version>__<descriptive_name>.sql` (version commonly `YYYYMMDDHHMMSS`, double underscore before the description, snake_case description).

## Class Naming

### Use PascalCase

```java
// Controllers
public class UserController {}
public class AuthController {}
public class OrderHistoryController {}

// Services
public interface UserService {}
public class UserServiceImpl implements UserService {}
public class EmailServiceImpl implements EmailService {}

// DTOs (prefer records)
public record CreateUserRequest(...) {}
public record UpdateProfileRequest(...) {}
public record UserProfileResponse(...) {}

// Entities
@Entity
public class User {}

@Entity
public class Order {}

// Exceptions
public class UserNotFoundException extends ResourceNotFoundException {}
public class InvalidCredentialsException extends UnauthorizedException {}

// Security
public class JwtAuthenticationFilter extends OncePerRequestFilter {}

// Interceptors / Advice
public class LoggingInterceptor implements HandlerInterceptor {}
public class GlobalExceptionHandler {}
```

### Suffixes for Different Types

- Controllers: `*Controller`
- Service interfaces: `*Service`; implementations: `*ServiceImpl`
- Repositories: `*Repository`
- DTOs: `*Request` / `*Response`
- Entities: no suffix (e.g. `User`, `Order`)
- Mappers: `*Mapper`
- Validators: `*Validator` (the annotation has no suffix)
- Configuration: `*Config` / `*Configuration`
- Exceptions: `*Exception`

## Package Naming

### Use all-lowercase, no underscores

```java
com.igogo.controller
com.igogo.service
com.igogo.service.impl
com.igogo.repository
com.igogo.dto.request
com.igogo.dto.response
com.igogo.entity
com.igogo.exception
com.igogo.config
```

Group primarily by **layer** (as above) or by **feature** (`com.igogo.user.controller`), but be consistent project-wide.

## Variable Naming

### Use camelCase

```java
// Good
String userName = "John";
boolean isActive = true;
int totalAmount = 1000;
List<User> users = new ArrayList<>();
Instant createdAt = Instant.now();

// Bad
String user_name = "John";  // ❌ snake_case
String UserName = "John";   // ❌ PascalCase
String USERNAME = "John";   // ❌ UPPER_CASE (reserved for constants)
```

### Boolean Variables

Prefix with `is`, `has`, `should`, `can`, `will`

```java
// Good
boolean isActive = true;
boolean hasPermission = false;
boolean shouldValidate = true;
boolean canEdit = false;
boolean willExpire = true;

// Bad
boolean active = true;        // ❌
boolean permission = false;   // ❌
boolean validate = true;      // ❌
```

### Collections

Use plural nouns

```java
// Good
List<User> users = new ArrayList<>();
List<Order> orders = new ArrayList<>();
List<String> errorMessages = new ArrayList<>();

// Bad
List<User> userList = new ArrayList<>();   // ❌ redundant 'List'
List<User> userArray = new ArrayList<>();  // ❌ redundant 'Array'
List<User> user = new ArrayList<>();       // ❌ singular for collection
```

### Avoid Ambiguous Names

```java
// Good
List<User> activeUsers = users.stream().filter(User::isActive).toList();
List<Order> sortedOrders = orders.stream()
        .sorted(Comparator.comparing(Order::getDate)).toList();

// Bad
List<User> temp = ...;    // ❌
List<Order> result = ...; // ❌
var data2 = ...;          // ❌
```

## Method Naming

### Use camelCase with a Verb Prefix

```java
// Good - CRUD operations
User createUser(CreateUserRequest request);
User findUserById(UUID id);
User updateUser(UUID id, UpdateUserRequest request);
void deleteUser(UUID id);
Page<User> findAllUsers(UserFilter filter, Pageable pageable);

// Good - Specific actions
void sendEmail(String to, String subject);
boolean validateToken(String token);
BigDecimal calculateTotal(List<OrderItem> items);
Report generateReport(ReportParams params);

// Bad
User user(UUID id);          // ❌ missing verb
User get(UUID id);           // ❌ too generic
void doSomething();          // ❌ unclear
```

### Common Verb Prefixes

- **get/find:** Retrieve data (prefer `find` for queries that may return empty / `Optional`)
- **set/update:** Modify data
- **create/add:** Create new data
- **delete/remove:** Delete data
- **is/has/can:** Return boolean
- **calculate/compute:** Perform calculation
- **validate/verify:** Check validity
- **send/fetch:** Network operations
- **process/handle:** Process data
- **generate/build:** Create derived data

### Boolean-Returning Methods

```java
// Good
private boolean isValidEmail(String email) {}
private boolean hasPermission(User user, String action) {}
private boolean canAccessResource(UUID userId, UUID resourceId) {}
private boolean shouldRetry(int attempt) {}

// Bad
private boolean checkEmail(String email) {}   // ❌
private boolean permission(User user) {}       // ❌
private boolean access(UUID userId) {}         // ❌
```

## Constant Naming

### Use UPPER_SNAKE_CASE

```java
public final class AppConstants {
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int TOKEN_EXPIRY_HOURS = 24;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final String API_VERSION = "v1";
    public static final int BCRYPT_STRENGTH = 12;

    // Configuration constants
    public static final long DATABASE_TIMEOUT_MS = 10_000L;
    public static final long CACHE_TTL_SECONDS = 3_600L;

    private AppConstants() {}
}

// Bad
public static final int maxLoginAttempts = 5;  // ❌
public static final int tokenExpiryHours = 24; // ❌
```

### Regular Expressions

```java
public static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
public static final String PHONE_REGEX = "^\\+?[1-9]\\d{1,14}$";
public static final String NUMERIC_ONLY_REGEX = "^\\d+$";
public static final Pattern UUID_PATTERN = Pattern.compile(
    "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    Pattern.CASE_INSENSITIVE);
```

### Date/Time Formats

```java
public static final String DATE_FORMAT = "yyyy-MM-dd";
public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
public static final String TIME_FORMAT = "HH:mm:ss";
public static final String ISO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
```

## Enum Naming

### Use PascalCase for the Enum Type, UPPER_SNAKE_CASE for Constants

```java
// Good
public enum UserRole {
    ADMIN, USER, MODERATOR, GUEST
}

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}

public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CASH_ON_DELIVERY
}

// Bad
public enum userRole {       // ❌ camelCase type
    admin, user
}

public enum OrderStatus {
    pending, Confirmed       // ❌ camel/Pascal constants
}
```

If you need to persist a custom string value, attach a field rather than relying on the constant name:

```java
public enum PaymentMethod {
    CREDIT_CARD("credit_card"),
    BANK_TRANSFER("bank_transfer");

    private final String code;

    PaymentMethod(String code) { this.code = code; }
    public String getCode() { return code; }
}
```

Persist enums with `@Enumerated(EnumType.STRING)` — never `ORDINAL`.

## Interface Naming

### Use PascalCase, NO 'I' prefix (Java convention)

```java
// Good
public interface UserService {}
public interface PaymentGateway {}
public interface PaginationOptions {}

// Bad
public interface IUserService {}   // ❌ 'I' prefix is not Java style
public interface user {}           // ❌ camelCase
public interface UserInterface {}  // ❌ redundant suffix
```

When an interface has a single implementation, name the implementation `*Impl` (e.g. `UserServiceImpl`).

## Annotation Usage Naming

```java
// Good
@RestController
@RequestMapping("/users")
public class UserController {}

@Service
public class UserServiceImpl implements UserService {}

@GetMapping("/{id}")
public UserResponse findOne(@PathVariable UUID id) {}

@PreAuthorize("hasRole('ADMIN')")
public ProfileResponse getProfile() {}

// Custom annotations - PascalCase
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}

@Operation(summary = "Create new user")
@ApiResponse(responseCode = "201", description = "User created successfully")
public ApiResponse<UserResponse> create(...) {}
```

## DTO Property Naming

### Request DTOs

```java
// Good - use a record with Bean Validation + springdoc annotations
public record CreateUserRequest(

    @NotBlank
    @Email
    @Schema(example = "user@example.com")
    String email,

    @NotBlank
    @Size(min = 8)
    @Schema(example = "SecurePass123!")
    String password,

    @NotBlank
    @Schema(example = "John Doe")
    String fullName,

    @ValidPhone
    @Schema(example = "+84901234567")
    String phoneNumber
) {}

// Bad - inconsistent naming
public record CreateUserRequest(
    String Email,          // ❌ PascalCase
    String user_password,  // ❌ snake_case
    String full_name       // ❌ snake_case
) {}
```

### Response DTOs

```java
// Good
public record UserProfileResponse(
    UUID id,
    String email,
    String fullName,
    Instant createdAt,
    boolean isActive
) {}
```

## Parameter Naming

### Path Variables

```java
// Good
@GetMapping("/{userId}/orders/{orderId}")
public OrderResponse findOrder(
        @PathVariable UUID userId,
        @PathVariable UUID orderId) {}

// Bad
@GetMapping("/{id1}/orders/{id2}")   // ❌ unclear
public OrderResponse findOrder(
        @PathVariable UUID id1,
        @PathVariable UUID id2) {}
```

### Query Parameters

```java
// Good
@GetMapping
public Page<UserResponse> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction order) {}

// Bad
@GetMapping
public Page<UserResponse> findAll(
        @RequestParam int p,   // ❌ unclear
        @RequestParam int l) {} // ❌ unclear
```

## Database Column Naming

### Use snake_case for Tables and Columns

```sql
-- Good
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) NOT NULL UNIQUE,
  full_name VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Bad
CREATE TABLE Users (        -- ❌ PascalCase table
  Id UUID PRIMARY KEY,      -- ❌ PascalCase column
  fullName VARCHAR(255)     -- ❌ camelCase column
);
```

### JPA Entity Mapping

```java
// Map database snake_case to Java camelCase via @Column,
// or rely on a PhysicalNamingStrategy that converts camelCase -> snake_case.
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;          // -> full_name

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;      // -> password_hash

    @Column(name = "is_active")
    private boolean isActive;         // -> is_active

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;        // -> created_at

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;        // -> updated_at
}
```

> Tip: configure `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy` so most `@Column(name=...)` overrides become unnecessary.

## Configuration Properties

### Use kebab-case in YAML, UPPER_SNAKE_CASE for environment variables

```yaml
# application.yml - kebab-case keys
server:
  port: 2207
spring:
  datasource:
    url: ${DATABASE_URL}
  jpa:
    open-in-view: false
app:
  jwt:
    access-token-key: ${JWT_SECRET_ACCESS_TOKEN_KEY}
    refresh-token-key: ${JWT_SECRET_REFRESH_TOKEN_KEY}
  cors:
    allowed-origins: ${CORS_ORIGIN:http://localhost:3000}
```

```bash
# Environment variables - UPPER_SNAKE_CASE
DATABASE_URL=jdbc:postgresql://...
JWT_SECRET_ACCESS_TOKEN_KEY=...
JWT_SECRET_REFRESH_TOKEN_KEY=...
ENCRYPTION_KEY=...
CORS_ORIGIN=http://localhost:3000
```

Bind them with type-safe `@ConfigurationProperties(prefix = "app.jwt")` classes.

## Message / Translation Keys

### Use dot notation with lowercase (Spring `MessageSource`)

```properties
# messages_en.properties
api.running=API is running
user.notFound=User not found
user.created=User created successfully
user.updated=User updated successfully
user.deleted=User deleted successfully
validation.email.required=Email is required
validation.email.invalid=Email format is invalid
validation.password.required=Password is required
validation.password.tooShort=Password must be at least 8 characters
```

```java
// Usage in code
messageSource.getMessage("user.notFound", null, locale);
messageSource.getMessage("validation.email.invalid", null, locale);
```

## Git Branch Naming

### Use kebab-case with a prefix

```bash
# Good
feature/user-authentication
feature/order-management
bugfix/email-validation
hotfix/critical-security-patch
refactor/user-service
docs/api-documentation

# Bad
Feature/UserAuthentication    # ❌ PascalCase
user_authentication           # ❌ no prefix
fix-bug                       # ❌ unclear
```

## Examples Summary

### Complete Example

```java
// File: src/main/java/com/igogo/user/controller/UserController.java
package com.igogo.user.controller;

import com.igogo.common.response.ApiResponse;
import com.igogo.user.dto.request.CreateUserRequest;
import com.igogo.user.dto.response.UserResponse;
import com.igogo.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new user")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse newUser = userService.createUser(request);
        return ApiResponse.success("user.created", newUser);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ApiResponse<UserResponse> findUserById(@PathVariable UUID userId) {
        UserResponse user = userService.findUserById(userId);
        return ApiResponse.success("user.found", user);
    }
}
```

## Quick Reference

| Type | Convention | Example |
|------|------------|---------|
| Java source file | PascalCase (matches type) | `UserService.java` |
| Class | PascalCase | `UserServiceImpl` |
| Interface | PascalCase (no `I` prefix) | `UserService` |
| Package | lowercase | `com.igogo.user.service` |
| Method | camelCase | `createUser()` |
| Variable | camelCase | `userName` |
| Constant | UPPER_SNAKE_CASE | `MAX_ATTEMPTS` |
| Enum type | PascalCase | `UserRole` |
| Enum constant | UPPER_SNAKE_CASE | `ADMIN` |
| Database table | snake_case | `user_profiles` |
| Database column | snake_case | `created_at` |
| YAML property | kebab-case | `access-token-key` |
| Environment Var | UPPER_SNAKE_CASE | `DATABASE_URL` |
| Message key | dot.case | `user.notFound` |
| Flyway migration | `V<ver>__<name>.sql` | `V20250118__create_users.sql` |
| Git Branch | kebab-case | `feature/user-auth` |
