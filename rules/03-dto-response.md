# DTO Response Rules - iGoGo Backend

## Overview

Response DTOs define the structure of data sent back to clients. They ensure consistency, type safety, and proper API documentation while hiding sensitive internal data. Prefer immutable Java `record`s for responses and never return JPA entities directly from controllers.

## File Structure

```
src/main/java/com/igogo/<feature>/dto/response/
├── <Feature>Response.java
├── <Feature>ListResponse.java
├── <Feature>DetailResponse.java
└── <SpecificResponse>.java
```

## Naming Conventions

### File / Class Names

```java
// Good
UserResponse.java
UserListResponse.java
UserProfileResponse.java
OrderSummaryResponse.java

// Bad
UserResponseDTO.java        // ❌ redundant DTO uppercase
ResponseUser.java           // ❌ wrong order
userResponse.java           // ❌ wrong case
```

## Basic Structure

### Simple Response DTO (record)

```java
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(

    @Schema(description = "User unique identifier",
            example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "User full name", example = "John Doe")
    String fullName,

    @Schema(description = "Account creation date", example = "2025-01-18T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Account active status", example = "true")
    boolean isActive

    // Sensitive fields (password, passwordHash, refreshToken) are simply NOT declared here
) {}
```

## Using the ApiResponse Wrapper

### Standard Response Wrapper

The project wraps all responses in a generic `ApiResponse<T>`:

```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
```

### Controller Usage

```java
@Tag(name = "users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User found successfully")
    public com.igogo.common.response.ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
        UserResponse user = userService.findById(id);
        return com.igogo.common.response.ApiResponse.success("user.found", user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new user")
    public com.igogo.common.response.ApiResponse<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse newUser = userService.create(request);
        return com.igogo.common.response.ApiResponse.success("user.created", newUser);
    }
}
```

> Tip: a `ResponseBodyAdvice` or a custom annotation can wrap raw returns automatically so controllers can simply `return userResponse;`.

### Actual Response Format

```json
{
  "success": true,
  "message": "User found successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "fullName": "John Doe",
    "createdAt": "2025-01-18T10:30:00Z",
    "isActive": true
  }
}
```

## Excluding Sensitive Data

### Method 1 (Preferred): Don't Include the Field

The cleanest, most type-safe approach is to simply not declare sensitive fields on the response record. Entities map to a response that only contains safe fields.

```java
public record UserResponse(UUID id, String email, String fullName, Instant createdAt) {}
// password / passwordHash / refreshToken never leave the persistence layer
```

### Method 2: @JsonIgnore on a Field

If you must reuse a class that has a sensitive field, hide it from serialization:

```java
public class UserView {
    private UUID id;
    private String email;

    @JsonIgnore
    private String passwordHash;   // never serialized
}
```

### Method 3: Write-only Access

For a field you accept on input but never return:

```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String password;
```

> Never annotate entities for response shaping — keep entities and DTOs separate.

## List / Paginated Responses

### Simple List Response

```java
public record UserListResponse(
    @Schema(description = "List of users") List<UserResponse> users,
    @Schema(description = "Total number of users", example = "100") long total
) {}
```

### Paginated Response

Spring Data already returns `Page<T>`. Wrap it in a stable DTO so the JSON contract doesn't depend on Spring internals.

```java
public record PaginationMeta(
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "100") long total,
    @Schema(example = "5") int totalPages,
    @Schema(example = "true") boolean hasNext,
    @Schema(example = "false") boolean hasPrevious
) {
    public static PaginationMeta from(Page<?> page) {
        return new PaginationMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }
}

public record PagedResponse<T>(
    @Schema(description = "Page content") List<T> data,
    @Schema(description = "Pagination metadata") PaginationMeta meta
) {
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(page.map(mapper).getContent(), PaginationMeta.from(page));
    }
}
```

### Service Implementation

Let Spring Data do the paging, sorting, and counting; map entities to DTOs via the mapper.

```java
@Override
@Transactional(readOnly = true)
public PagedResponse<UserResponse> findAll(UserFilter filter, Pageable pageable) {
    Page<User> page = (filter.search() == null || filter.search().isBlank())
        ? userRepository.findAll(pageable)
        : userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
              filter.search(), filter.search(), pageable);

    return PagedResponse.of(page, userMapper::toResponse);
}
```

## Nested Response DTOs

### One-to-Many Relationship

```java
public record OrderItemResponse(
    @Schema(example = "123e4567-e89b-12d3-a456-426614174000") UUID id,
    @Schema(example = "Product Name") String productName,
    @Schema(example = "2") int quantity,
    @Schema(example = "29.99") BigDecimal price,
    @Schema(example = "59.98") BigDecimal subtotal
) {}

public record OrderResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000") UUID id,
    @Schema(example = "ORD-2025-001") String orderNumber,
    @Schema(description = "Order items") List<OrderItemResponse> items,
    @Schema(example = "59.98") BigDecimal totalAmount,
    @Schema(example = "PENDING") OrderStatus status,
    Instant createdAt
) {}
```

### Many-to-One Relationship

```java
public record AuthorResponse(UUID id, String name, String email) {}

public record PostResponse(
    UUID id,
    String title,
    String content,
    @Schema(description = "Post author") AuthorResponse author,
    Instant createdAt
) {}
```

## Computed / Derived Fields

### Compute in the Mapper or a Factory Method

Records are immutable, so compute derived values when constructing the DTO.

```java
public record ProductResponse(
    UUID id,
    String name,
    @Schema(example = "99.99") BigDecimal price,
    @Schema(example = "20") int discountPercentage,
    @Schema(description = "Final price after discount", example = "79.99") BigDecimal finalPrice,
    @Schema(description = "Discount amount", example = "20.00") BigDecimal discountAmount
) {
    public static ProductResponse from(Product p) {
        BigDecimal discount = p.getPrice()
            .multiply(BigDecimal.valueOf(p.getDiscountPercentage()))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new ProductResponse(
            p.getId(), p.getName(), p.getPrice(), p.getDiscountPercentage(),
            p.getPrice().subtract(discount), discount);
    }
}
```

> With MapStruct you can express the same with `@Mapping(target = "finalPrice", expression = "java(...)")` or a default method.

## Mapping Entities to DTOs

### Preferred: MapStruct

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
    List<UserResponse> toResponseList(List<User> users);
}
```

```java
@Override
@Transactional(readOnly = true)
public UserResponse findById(UUID id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    return userMapper.toResponse(user);
}
```

### Alternative: Static Factory / Manual Mapping

```java
public record UserResponse(UUID id, String email, String fullName, boolean isActive, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.isActive(), u.getCreatedAt());
    }
}
```

> Entity field names are camelCase in Java and snake_case in the DB; the JPA `@Column` mapping (or naming strategy) handles that translation, so DTO mapping deals only with Java field names.

## Different Response DTOs for Different Contexts

### List vs Detail DTOs

```java
// Minimal data for list view
public record UserListItem(UUID id, String email, String fullName, boolean isActive) {}

// Complete data for detail view
public record UserDetailResponse(
    UUID id,
    String email,
    String fullName,
    String phoneNumber,
    String bio,
    String address,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    List<OrderResponse> orders,
    long totalOrders
) {}
```

### Public vs Private Profile

Records don't support inheritance; compose shared fields or just declare each profile explicitly.

```java
// Public profile (other users can see)
public record UserPublicProfile(UUID id, String fullName, String bio, String avatarUrl, Instant joinedAt) {}

// Private profile (own user can see) - includes the public fields plus private ones
public record UserPrivateProfile(
    UUID id, String fullName, String bio, String avatarUrl, Instant joinedAt,
    String email, String phoneNumber, String address, Map<String, Object> preferences
) {}
```

## Enum Response Values

Serialize enums by name (the default). Document them with `@Schema`.

```java
public enum OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

public record OrderResponse(
    UUID id,
    @Schema(description = "Current order status", example = "PENDING") OrderStatus status,
    Instant createdAt
) {}
```

## Timestamp Formatting

### ISO Format (Recommended)

Use `java.time` types. With `spring.jackson.serialization.write-dates-as-timestamps=false` and the JSR-310 module (auto-registered), `Instant`/`OffsetDateTime` serialize as ISO-8601 strings.

```java
public record UserResponse(
    UUID id,
    @Schema(example = "2025-01-18T10:30:00.000Z") Instant createdAt,
    @Schema(example = "2025-01-18T15:45:30.000Z") Instant updatedAt
) {}
```

### Custom Date Formatting

```java
public record EventResponse(
    UUID id,
    String title,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(description = "Event start date (yyyy-MM-dd)", example = "2025-01-20")
    LocalDate startDate,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(example = "2025-01-22")
    LocalDate endDate
) {}
```

## Success / Error Response DTOs

### Success Response

Use the `ApiResponse.success(...)` helper — no per-endpoint success DTO needed.

```java
return ApiResponse.success("user.created", data);
```

### Error Response (handled by @RestControllerAdvice)

Error responses are produced centrally by the `GlobalExceptionHandler` (see 09-error-handling.md):

```json
{
  "success": false,
  "error": { "fieldName": "First error message" },
  "errors": { "fieldName": ["All error messages"] },
  "message": "Bad Request"
}
```

## springdoc / OpenAPI Response Documentation

### Single Resource Response

```java
@GetMapping("/{id}")
@Operation(summary = "Get user by ID")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "User found successfully",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(responseCode = "404", description = "User not found")
})
public ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
    return ApiResponse.success("user.found", userService.findById(id));
}
```

### List Response

```java
@GetMapping
@Operation(summary = "Get all users")
@ApiResponse(responseCode = "200", description = "Users retrieved successfully")
public ApiResponse<PagedResponse<UserResponse>> findAll(
        @ParameterObject @Valid UserFilter filter,
        @ParameterObject Pageable pageable) {
    return ApiResponse.success("users.found", userService.findAll(filter, pageable));
}
```

### Multiple Response Types

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@Operation(summary = "Create user")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "User created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input data"),
    @ApiResponse(responseCode = "409", description = "Email already exists")
})
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ApiResponse.success("user.created", userService.create(request));
}
```

## Best Practices

### 1. Never Expose Sensitive Data

```java
// Good
public record UserResponse(UUID id, String email, String fullName) {}

// Bad
public record UserResponse(UUID id, String email, String passwordHash, String refreshToken) {} // ❌
```

### 2. Use the Right DTO for the Context

```java
// Good
public record UserListItem(...) {}        // minimal
public record UserDetailResponse(...) {}  // full
public record UserPublicProfile(...) {}   // public

// Bad - one DTO for everything, including sensitive fields ❌
```

### 3. Document All Fields

```java
// Good
@Schema(description = "User unique identifier",
        example = "550e8400-e29b-41d4-a716-446655440000") UUID id

// Bad
UUID id  // ❌ no documentation
```

### 4. Use Consistent camelCase Field Names

```java
// Good
public record UserResponse(String fullName, Instant createdAt, boolean isActive) {}

// Bad - mixed conventions ❌
```

### 5. Include Metadata in List Responses

```java
// Good
return PagedResponse.of(page, userMapper::toResponse);

// Bad
return userMapper.toResponseList(page.getContent());  // ❌ loses paging metadata
```

### 6. Keep Entities Out of the Controller Layer

```java
// Good - return a DTO
return userMapper.toResponse(user);

// Bad - return the entity (leaks schema, risks lazy-loading issues) ❌
return user;
```

## Common Patterns

### Authentication Response

```java
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserResponse user
) {}
```

### Upload Response

```java
public record FileUploadResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000") UUID id,
    @Schema(example = "document.pdf") String filename,
    @Schema(example = "https://storage.example.com/files/document.pdf") String url,
    @Schema(example = "application/pdf") String mimeType,
    @Schema(example = "1048576") long size,
    Instant uploadedAt
) {}
```

### Statistics Response

```java
public record DashboardStatsResponse(
    @Schema(example = "1250") long totalUsers,
    @Schema(example = "3420") long totalOrders,
    @Schema(example = "125000.50") BigDecimal totalRevenue,
    @Schema(example = "15.5") BigDecimal growthPercentage
) {}
```

## Quick Reference

| Pattern | Usage | Example |
|---------|-------|---------|
| Simple DTO | Single resource | `UserResponse` |
| List DTO | Multiple resources | `UserListResponse` |
| Paged DTO | Paginated list | `PagedResponse<UserResponse>` |
| Detail DTO | Full details | `UserDetailResponse` |
| Nested DTO | Related data | `OrderResponse` with `items` |
| Public DTO | Public data | `UserPublicProfile` |
| Private DTO | Owner-only data | `UserPrivateProfile` |
| `@JsonIgnore` | Hide a field | `@JsonIgnore String passwordHash` |
| `@JsonProperty(access = WRITE_ONLY)` | Input-only field | password on a shared class |
| `@JsonFormat` | Custom date format | `@JsonFormat(pattern = "yyyy-MM-dd")` |
| `@Schema` | Document field | `@Schema(example = "value")` |
| MapStruct `@Mapper` | Entity → DTO | `UserResponse toResponse(User u)` |
