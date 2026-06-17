# Controller Rules - iGoGo Backend

## Overview

Controllers handle HTTP requests and responses. They should be thin, delegating business logic to services and focusing solely on HTTP concerns. Use `@RestController` so return values are serialized straight to the response body.

## File Structure

```
src/main/java/com/igogo/<feature>/controller/
├── UserController.java
└── (complex modules)
    ├── UserAdminController.java
    └── UserPublicController.java
```

## Naming Conventions

### File / Class Names

```java
// Good
UserController.java        -> public class UserController {}
AuthController.java        -> public class AuthController {}
OrderHistoryController.java-> public class OrderHistoryController {}

// Bad
User.java                  // ❌ missing Controller suffix
userController.java        // ❌ wrong case
```

## Basic Structure

### Template

```java
package com.igogo.user.controller;

import com.igogo.common.response.ApiResponse;
import com.igogo.user.dto.request.CreateUserRequest;
import com.igogo.user.dto.request.UpdateUserRequest;
import com.igogo.user.dto.request.UserFilter;
import com.igogo.user.dto.response.UserResponse;
import com.igogo.user.dto.response.PagedResponse;
import com.igogo.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("user.created", userService.create(request));
    }

    @GetMapping
    @Operation(summary = "Get all users")
    public ApiResponse<PagedResponse<UserResponse>> findAll(
            @ParameterObject @Valid UserFilter filter,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success("users.found", userService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
        return ApiResponse.success("user.found", userService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("user.updated", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user")
    public void remove(@PathVariable UUID id) {
        userService.remove(id);
    }
}
```

## HTTP Methods and Annotations

### GET Requests

```java
// Get all resources
@GetMapping
@Operation(summary = "Get all users")
public ApiResponse<PagedResponse<UserResponse>> findAll(
        @ParameterObject @Valid UserFilter filter, @ParameterObject Pageable pageable) {
    return ApiResponse.success("users.found", userService.findAll(filter, pageable));
}

// Get single resource by ID
@GetMapping("/{id}")
public ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
    return ApiResponse.success("user.found", userService.findById(id));
}

// Get nested resource
@GetMapping("/{userId}/orders")
public ApiResponse<List<OrderResponse>> getUserOrders(@PathVariable UUID userId) {
    return ApiResponse.success("orders.found", userService.getUserOrders(userId));
}

// Get with multiple parameters
@GetMapping("/{userId}/orders/{orderId}")
public ApiResponse<OrderResponse> getUserOrder(@PathVariable UUID userId,
                                               @PathVariable UUID orderId) {
    return ApiResponse.success("order.found", userService.getUserOrder(userId, orderId));
}
```

### POST Requests

```java
// Create resource
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@Operation(summary = "Create user")
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ApiResponse.success("user.created", userService.create(request));
}

// Custom action
@PostMapping("/bulk-delete")
@Operation(summary = "Bulk delete users")
public ApiResponse<Void> bulkDelete(@Valid @RequestBody BulkDeleteRequest request) {
    userService.bulkDelete(request.ids());
    return ApiResponse.success("users.deleted", null);
}

// Nested resource creation
@PostMapping("/{userId}/addresses")
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<AddressResponse> addAddress(@PathVariable UUID userId,
                                               @Valid @RequestBody CreateAddressRequest request) {
    return ApiResponse.success("address.added", userService.addAddress(userId, request));
}
```

### PUT/PATCH Requests

```java
// Full update (PUT)
@PutMapping("/{id}")
@Operation(summary = "Update user (full)")
public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateUserRequest request) {
    return ApiResponse.success("user.updated", userService.update(id, request));
}

// Partial update (PATCH)
@PatchMapping("/{id}")
@Operation(summary = "Update user (partial)")
public ApiResponse<UserResponse> partialUpdate(@PathVariable UUID id,
                                               @Valid @RequestBody PatchUserRequest request) {
    return ApiResponse.success("user.updated", userService.patch(id, request));
}

// Specific field update
@PatchMapping("/{id}/activate")
@Operation(summary = "Activate user account")
public ApiResponse<UserResponse> activate(@PathVariable UUID id) {
    return ApiResponse.success("user.activated", userService.activate(id));
}
```

### DELETE Requests

```java
// Delete resource (204 No Content)
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
@Operation(summary = "Delete user")
public void remove(@PathVariable UUID id) {
    userService.remove(id);   // void -> empty body
}

// Soft delete (200 OK with body)
@DeleteMapping("/{id}/soft")
@Operation(summary = "Soft delete user")
public ApiResponse<UserResponse> softRemove(@PathVariable UUID id) {
    return ApiResponse.success("user.deleted", userService.softRemove(id));
}
```

## Request Parameters

### Path Variables

```java
// Single
@GetMapping("/{id}")
public ApiResponse<UserResponse> findOne(@PathVariable UUID id) { ... }

// Multiple
@GetMapping("/{userId}/orders/{orderId}")
public ApiResponse<OrderResponse> findOrder(@PathVariable UUID userId,
                                            @PathVariable UUID orderId) { ... }
```

Spring converts `{id}` to `UUID`/`Long`/etc. automatically. An invalid value throws `MethodArgumentTypeMismatchException`, handled centrally by the `GlobalExceptionHandler` (equivalent to NestJS `ParseUUIDPipe`/`ParseIntPipe`).

### Query Parameters

```java
// Object-based (Recommended): bind a filter record + Pageable
@GetMapping
public ApiResponse<PagedResponse<UserResponse>> findAll(
        @ParameterObject @Valid UserFilter filter, @ParameterObject Pageable pageable) {
    return ApiResponse.success("users.found", userService.findAll(filter, pageable));
}

// Individual parameters with defaults
@GetMapping("/search")
public ApiResponse<List<UserResponse>> search(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search) {
    return ApiResponse.success("users.found", userService.search(page, size, search));
}
```

> Prefer accepting `Pageable` for paging/sorting — `?page=0&size=20&sort=createdAt,desc` is parsed for free.

### Request Body

```java
// Single DTO (always validate)
@PostMapping
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) { ... }
```

Avoid extracting individual JSON fields manually — always bind a validated DTO.

### Headers & Current User

```java
// Reading a header
@GetMapping
public ApiResponse<List<UserResponse>> findAll(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) { ... }

// Better: inject the authenticated principal (Spring Security)
@GetMapping("/profile")
public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success("profile.found", userService.findById(principal.getId()));
}
```

A custom `@CurrentUser` annotation can wrap `@AuthenticationPrincipal` for brevity.

## Response Status Codes

### Using @ResponseStatus or ResponseEntity

```java
// 200 OK (default)
@GetMapping("/{id}")
public ApiResponse<UserResponse> findOne(@PathVariable UUID id) { ... }

// 201 Created
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) { ... }

// 204 No Content (void method)
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void remove(@PathVariable UUID id) { userService.remove(id); }

// Dynamic status with ResponseEntity
@PostMapping("/custom")
public ResponseEntity<ApiResponse<Void>> custom() {
    return ResponseEntity.accepted().body(ApiResponse.success("accepted", null));
}
```

### Common Status Codes

- **200 OK:** Successful GET, PUT, PATCH, DELETE (with body)
- **201 Created:** Successful POST (resource created)
- **204 No Content:** Successful DELETE (no body)
- **400 Bad Request:** Validation error (`MethodArgumentNotValidException`)
- **401 Unauthorized:** Authentication required
- **403 Forbidden:** Insufficient permissions
- **404 Not Found:** Resource not found
- **409 Conflict:** Resource conflict (e.g. duplicate email)
- **500 Internal Server Error:** Server error

## springdoc / OpenAPI Documentation

### Basic Documentation

```java
@Tag(name = "users")   // Groups endpoints in Swagger UI
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public com.igogo.common.response.ApiResponse<UserResponse> findOne(@PathVariable UUID id) { ... }
}
```

### Advanced Documentation

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@Operation(summary = "Create new user",
           description = "Creates a new user account with the provided information")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "User created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input data"),
    @ApiResponse(responseCode = "409", description = "Email already exists")
})
public com.igogo.common.response.ApiResponse<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request) { ... }
```

### Protected & Deprecated Endpoints

```java
@SecurityRequirement(name = "bearerAuth")    // indicates auth required
@GetMapping("/profile")
@Operation(summary = "Get current user profile")
public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) { ... }

@Deprecated
@GetMapping("/old-endpoint")
@Operation(summary = "Old endpoint", deprecated = true)
public ApiResponse<Void> oldEndpoint() { ... }
```

## Security & Authorization

### Method-level Security

Enable with `@EnableMethodSecurity`, then annotate methods/classes:

```java
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@GetMapping("/profile")
public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void remove(@PathVariable UUID id) { userService.remove(id); }

@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR') and #id == principal.id")
@PatchMapping("/{id}")
public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateUserRequest request) { ... }
```

### URL-based Security

Coarse rules live in the `SecurityFilterChain` (see 11-multi-role-system.md):

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**", "/users/public-info").permitAll()
    .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")
    .anyRequest().authenticated());
```

## Interceptors & Filters

### HandlerInterceptor (logging, timing)

```java
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        log.info("{} {}", req.getMethod(), req.getRequestURI());
        return true;
    }
}

// Register in a WebMvcConfigurer
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor);
    }
}
```

### Response Wrapping

A `ResponseBodyAdvice<Object>` can wrap raw return values into `ApiResponse` so controllers can return DTOs directly.

### File Upload

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
    return ApiResponse.success("file.uploaded", fileService.store(file));
}

// Multiple files
@PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<List<FileUploadResponse>> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
    return ApiResponse.success("files.uploaded", fileService.storeAll(files));
}
```

## Exception Handling

Throw domain exceptions from the **service** layer; the `@RestControllerAdvice` maps them to HTTP responses. Controllers should rarely throw.

```java
// In the service (preferred location)
public UserResponse findById(UUID id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    return userMapper.toResponse(user);
}

public UserResponse create(CreateUserRequest request) {
    if (userRepository.existsByEmail(request.email())) {
        throw new ConflictException("user.emailExists");
    }
    // ...
}
```

See 09-error-handling.md for the exception hierarchy and the handler.

## Best Practices

### 1. Keep Controllers Thin

```java
// Good - delegate to the service
@PostMapping
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ApiResponse.success("user.created", userService.create(request));
}

// Bad - business logic, queries, and hashing in the controller ❌
```

### 2. Use Validated DTOs

```java
// Good
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) { ... }

// Bad - extracting raw fields, no validation ❌
```

### 3. Use the ApiResponse Wrapper

```java
// Good
return ApiResponse.success("user.created", user);

// Bad - ad-hoc map/object, inconsistent shape ❌
```

### 4. Document Every Endpoint

```java
// Good
@Operation(summary = "Get user by ID")
@ApiResponse(responseCode = "404", description = "User not found")

// Bad - undocumented endpoint ❌
```

### 5. Use the Right HTTP Method

```java
@GetMapping        // Retrieve
@PostMapping       // Create
@PutMapping        // Full update
@PatchMapping      // Partial update
@DeleteMapping     // Delete

// Bad: @PostMapping("/get-user"), @GetMapping("/delete-user") ❌
```

### 6. Use Proper Status Codes

```java
@PostMapping @ResponseStatus(HttpStatus.CREATED)        // 201
@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)  // 204
```

### 7. Route Organization

```java
@RequestMapping("/users")
public class UserController {
    // GET /users, GET /users/{id}, POST /users, PATCH /users/{id}, DELETE /users/{id}
    // Nested: GET /users/{id}/orders, POST /users/{id}/orders
    // Custom actions (sparingly): POST /users/{id}/activate, POST /users/bulk-delete
}
// Bad: /getUserById/{id}, /createNewUser, /user-delete ❌
```

### 8. Return Values, Don't Manage Threads

`@RestController` methods return the value to serialize. For genuinely async endpoints use `CompletableFuture<T>` or reactive types — don't block on futures manually.

## Common Patterns

### CRUD Controller

```java
@Tag(name = "users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("user.created", userService.create(request));
    }

    @GetMapping
    public ApiResponse<PagedResponse<UserResponse>> findAll(
            @ParameterObject @Valid UserFilter filter, @ParameterObject Pageable pageable) {
        return ApiResponse.success("users.found", userService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
        return ApiResponse.success("user.found", userService.findById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("user.updated", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        userService.remove(id);
    }
}
```

### Auth Controller

```java
@Tag(name = "auth")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("auth.registered", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("auth.loggedIn", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("auth.refreshed", authService.refreshTokens(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getId());
        return ApiResponse.success("auth.loggedOut", null);
    }
}
```

## Quick Reference

| Annotation | Purpose | Example |
|-----------|---------|---------|
| `@RestController` | REST controller | `@RestController` |
| `@RequestMapping` | Base path | `@RequestMapping("/users")` |
| `@GetMapping` | GET request | `@GetMapping("/{id}")` |
| `@PostMapping` | POST request | `@PostMapping` |
| `@PutMapping` | PUT request | `@PutMapping("/{id}")` |
| `@PatchMapping` | PATCH request | `@PatchMapping("/{id}")` |
| `@DeleteMapping` | DELETE request | `@DeleteMapping("/{id}")` |
| `@RequestBody` | Request body | `@Valid @RequestBody CreateUserRequest req` |
| `@PathVariable` | Path variable | `@PathVariable UUID id` |
| `@RequestParam` | Query parameter | `@RequestParam String search` |
| `@RequestHeader` | Request header | `@RequestHeader("Authorization") String auth` |
| `@ResponseStatus` | Status code | `@ResponseStatus(HttpStatus.CREATED)` |
| `@AuthenticationPrincipal` | Current user | `@AuthenticationPrincipal UserPrincipal p` |
| `@PreAuthorize` | Method security | `@PreAuthorize("hasRole('ADMIN')")` |
| `@Tag` | Swagger group | `@Tag(name = "users")` |
| `@Operation` | Swagger operation | `@Operation(summary = "...")` |
| `@ApiResponse` | Swagger response | `@ApiResponse(responseCode = "404")` |
