# Service Rules - iGoGo Backend

## Overview

Services contain business logic, orchestration, and interactions with external systems. They are the core of the application and should be thoroughly tested. Data access is delegated to repositories (see 13-repository-pattern.md), and entity ↔ DTO conversion to mappers.

We use an **interface + implementation** split: a `UserService` interface in `service/` and a `UserServiceImpl` in `service/impl/`.

## File Structure

```
src/main/java/com/igogo/<feature>/service/
├── UserService.java            # interface
└── impl/
    ├── UserServiceImpl.java     # implementation
    ├── UserValidationService.java
    └── UserCalculationService.java
```

## Naming Conventions

### File / Class Names

```java
// Good
UserService.java            -> public interface UserService {}
UserServiceImpl.java        -> public class UserServiceImpl implements UserService {}
EmailNotificationService.java

// Bad
User.java                   // ❌ missing Service suffix
userService.java            // ❌ wrong case
```

## Basic Structure

### Interface

```java
package com.igogo.user.service;

public interface UserService {
    UserResponse create(CreateUserRequest request);
    PagedResponse<UserResponse> findAll(UserFilter filter, Pageable pageable);
    UserResponse findById(UUID id);
    UserResponse update(UUID id, UpdateUserRequest request);
    void remove(UUID id);
}
```

### Implementation

```java
package com.igogo.user.service.impl;

import com.igogo.common.exception.ConflictException;
import com.igogo.common.exception.ResourceNotFoundException;
import com.igogo.user.dto.request.*;
import com.igogo.user.dto.response.*;
import com.igogo.user.entity.User;
import com.igogo.user.mapper.UserMapper;
import com.igogo.user.repository.UserRepository;
import com.igogo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("user.emailExists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.info("User created successfully: {}", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> findAll(UserFilter filter, Pageable pageable) {
        Page<User> page = (filter.search() == null || filter.search().isBlank())
            ? userRepository.findAll(pageable)
            : userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                  filter.search(), filter.search(), pageable);
        return PagedResponse.of(page, userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userMapper.toResponse(getUserOrThrow(id));
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = getUserOrThrow(id);

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
                throw new ConflictException("user.emailExists");
            }
            user.setEmail(request.email());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully: {}", id);
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void remove(UUID id) {
        User user = getUserOrThrow(id);
        userRepository.delete(user);
        log.info("User deleted successfully: {}", id);
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }
}
```

## Dependency Injection

### Constructor Injection (Required)

```java
// Good - constructor injection via Lombok
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EmailService emailService;
}

// Bad - instantiating dependencies manually
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository = new UserRepositoryImpl(); // ❌
}
```

### Field Injection (Avoid)

```java
// Bad - prefer constructor injection (better for testing & immutability)
@Service
public class UserServiceImpl {
    @Autowired
    private UserRepository userRepository;  // ❌
}
```

## Business Logic

### Single Responsibility

```java
// Good - focused services
@Service
public class UserServiceImpl implements UserService {
    public UserResponse create(CreateUserRequest request) { /* user creation only */ }
}

@Service
public class EmailServiceImpl implements EmailService {
    public void sendWelcomeEmail(String email) { /* email sending only */ }
}

// Bad - one service doing creation + email + audit + cache ❌
```

### Composition Over Inheritance

```java
// Good - compose collaborators
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        User user = userRepository.save(toEntity(request));
        emailService.sendWelcomeEmail(user.getEmail());
        auditService.logUserCreation(user.getId());
        return userMapper.toResponse(user);
    }
}

// Bad - deep inheritance: BaseService -> DataService -> UserDataService ❌
```

## Database Operations

### Use Repositories, Not Raw SQL

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // Derived query
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        return userMapper.toResponse(user);
    }

    // Optional return for "may not exist"
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);   // bound parameters -> no SQL injection
    }
}
```

Spring Data JPA binds parameters automatically, so injection isn't a concern with derived queries, `@Query` (JPQL), or Specifications. See 13-repository-pattern.md.

### Transactions

Declare boundaries with `@Transactional`; everything inside commits or rolls back atomically. Use `readOnly = true` for queries.

```java
@Transactional
public void transferCredits(UUID fromUserId, UUID toUserId, BigDecimal amount) {
    User from = getUserOrThrow(fromUserId);
    User to = getUserOrThrow(toUserId);

    if (from.getCredits().compareTo(amount) < 0) {
        throw new BadRequestException("user.insufficientCredits");
    }

    from.setCredits(from.getCredits().subtract(amount));
    to.setCredits(to.getCredits().add(amount));

    transactionRepository.save(new CreditTransaction(fromUserId, toUserId, amount));
    log.info("Credits transferred: {} from {} to {}", amount, fromUserId, toUserId);
    // any exception thrown here rolls back all three changes
}
```

> Note: a runtime exception triggers rollback by default; checked exceptions do not unless you set `rollbackFor`. Keep `@Transactional` on the service layer, not the controller.

### Dynamic Queries (Specifications)

For optional filters, use JPA Specifications instead of string concatenation:

```java
@Transactional(readOnly = true)
public Page<User> findAll(UserFilter filter, Pageable pageable) {
    Specification<User> spec = Specification.where(null);

    if (filter.search() != null && !filter.search().isBlank()) {
        String like = "%" + filter.search().toLowerCase() + "%";
        spec = spec.and((root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("fullName")), like),
            cb.like(cb.lower(root.get("email")), like)));
    }
    if (filter.role() != null) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), filter.role()));
    }
    if (filter.isActive() != null) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("isActive"), filter.isActive()));
    }

    return userRepository.findAll(spec, pageable);   // paging & sorting handled by Pageable
}
```

## Error Handling

### Catch Only What You Can Handle

```java
// Good - translate a known DB error, let the advice handle the rest
@Transactional
public UserResponse create(CreateUserRequest request) {
    try {
        return userMapper.toResponse(userRepository.save(toEntity(request)));
    } catch (DataIntegrityViolationException ex) {   // e.g. unique violation
        log.error("Failed to create user: {}", ex.getMessage());
        throw new ConflictException("user.emailExists");
    }
}

// Bad - swallow the error
public UserResponse create(CreateUserRequest request) {
    try {
        // ...
    } catch (Exception ex) {
        log.error("error", ex);   // ❌ logged then...
        return null;              // ❌ swallowed
    }
}
```

Prefer checking preconditions (`existsByEmail`) over relying on DB exceptions, but keep the catch as a safety net for race conditions.

### Business Rule Validation

```java
@Transactional
public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
    User user = getUserOrThrow(userId);

    if (!user.isActive()) {
        throw new ForbiddenException("user.accountInactive");
    }
    if (user.getCredits().compareTo(request.totalAmount()) < 0) {
        throw new BadRequestException("user.insufficientCredits");
    }

    for (OrderItemRequest item : request.items()) {
        Product product = productService.getEntity(item.productId());
        if (product.getStock() < item.quantity()) {
            throw new BadRequestException("product.insufficientStock");
        }
    }

    return orderMapper.toResponse(orderRepository.save(buildOrder(userId, request)));
}
```

### Throwing Appropriate Exceptions

Use the project's custom exceptions (mapped to HTTP status by the advice — see 09-error-handling.md):

```java
// 404
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

// 400
if (amount.signum() <= 0) {
    throw new BadRequestException("payment.invalidAmount");
}

// 409
if (userRepository.existsByEmail(email)) {
    throw new ConflictException("user.emailExists");
}

// 403
if (!currentUserId.equals(targetUserId) && !isAdmin(currentUserId)) {
    throw new ForbiddenException("user.insufficientPermissions");
}
```

## Logging

### Use SLF4J (Lombok `@Slf4j`)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.email());
        try {
            User user = userRepository.save(toEntity(request));
            log.info("User created successfully: {}", user.getId());
            return userMapper.toResponse(user);
        } catch (RuntimeException ex) {
            log.error("Failed to create user: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional
    public void remove(UUID id) {
        log.warn("Deleting user: {}", id);
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }
}
```

### What to Log

```java
// ✓ Important operations
log.info("User registered successfully: {}", user.getId());

// ✓ Errors with context (pass the exception as the last arg for the stack trace)
log.error("Failed to send email to {}: {}", email, ex.getMessage(), ex);

// ✓ Warnings for unusual situations
log.warn("Login attempt for non-existent user: {}", email);

// ✓ Debug info
log.debug("Executing query with filter: {}", filter);

// ❌ Never log sensitive data
log.info("Password: {}", password);   // ❌
log.info("Token: {}", token);          // ❌
```

Use parameterized logging (`{}`) rather than string concatenation.

## Asynchronous & Parallel Operations

### Parallel Independent Calls

```java
// Good - run independent calls in parallel with @Async + CompletableFuture
@Transactional(readOnly = true)
public DashboardResponse getUserDashboard(UUID userId) {
    CompletableFuture<UserResponse> userF = userAsyncService.findByIdAsync(userId);
    CompletableFuture<List<OrderResponse>> ordersF = orderAsyncService.findUserOrdersAsync(userId);
    CompletableFuture<UserStats> statsF = statsAsyncService.getUserStatsAsync(userId);

    CompletableFuture.allOf(userF, ordersF, statsF).join();
    return new DashboardResponse(userF.join(), ordersF.join(), statsF.join());
}
```

`@Async` methods must live in a separate Spring bean and return `CompletableFuture<T>`; enable with `@EnableAsync` and a configured `TaskExecutor`. For simple sequential reads, plain method calls are fine.

## Helper & Validation Methods

### Private Helpers

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    }

    private void validateEmailUnique(String email, UUID excludeUserId) {
        boolean exists = excludeUserId != null
            ? userRepository.existsByEmailAndIdNot(email, excludeUserId)
            : userRepository.existsByEmail(email);
        if (exists) {
            throw new ConflictException("user.emailExists");
        }
    }
}
```

## Service Communication

### Calling Other Services

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final UserService userService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        UserResponse user = userService.findById(userId);
        request.items().forEach(item -> productService.findById(item.productId()));

        Order order = orderRepository.save(buildOrder(userId, request));

        // Decouple side effects: publish an event, handle email/notifications in a listener
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), user.email()));

        return orderMapper.toResponse(order);
    }
}
```

> Prefer `ApplicationEventPublisher` (optionally `@TransactionalEventListener`) for "fire-and-forget" side effects so they don't break the main transaction.

## Best Practices

### 1. Use Constructor Injection

```java
// Good
@Service @RequiredArgsConstructor
public class UserServiceImpl { private final UserRepository userRepository; }

// Bad - field injection or manual instantiation ❌
```

### 2. Keep Methods Focused

```java
// Good
@Transactional
public UserResponse create(CreateUserRequest request) {
    validateEmailUnique(request.email(), null);
    return userMapper.toResponse(userRepository.save(toEntity(request)));
}
// Bad - validation + hashing + insert + email + audit + cache in one method ❌
```

### 3. Use Explicit Types (no raw Object)

```java
// Good
UserResponse findById(UUID id);
// Bad
Object findById(Object id);  // ❌
```

### 4. Handle Errors Properly

```java
// Good
catch (DataAccessException ex) {
    log.error("Operation failed: {}", ex.getMessage(), ex);
    throw new InternalServerException("common.serverError");
}
// Bad - print + return null ❌
```

### 5. Rely on Bound Parameters

```java
// Good - derived query / @Query with :params
userRepository.findByEmail(email);
// Bad - string-concatenated native SQL ❌
```

### 6. Log with SLF4J, Not stdout

```java
// Good
log.info("User created successfully: {}", id);
// Bad
System.out.println("User created");  // ❌
```

### 7. Return DTOs, Not Entities

```java
// Good
public UserResponse findById(UUID id) {
    return userMapper.toResponse(getUserOrThrow(id));
}
// Bad - returning the JPA entity leaks schema & risks lazy-loading issues ❌
```

### 8. Put @Transactional on Service Methods

Reads: `@Transactional(readOnly = true)`. Writes: `@Transactional`. Don't open transactions in controllers or repositories.

## Testing Services

### Unit Test (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl service;

    @Test
    void createsUser() {
        var request = new CreateUserRequest("test@example.com", "Password123!", "Test User");
        var saved = new User(/* ... */);
        var response = new UserResponse(UUID.randomUUID(), "test@example.com", "Test User", true, Instant.now());

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(response);

        UserResponse result = service.create(request);

        assertThat(result.email()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void throwsWhenEmailExists() {
        var request = new CreateUserRequest("dup@example.com", "Password123!", "Dup");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ConflictException.class);
    }
}
```

## Quick Reference

| Pattern | Usage | Example |
|---------|-------|---------|
| `@Service` | Define service bean | `@Service class UserServiceImpl` |
| Interface + `Impl` | Abstraction | `UserService` / `UserServiceImpl` |
| `@RequiredArgsConstructor` | Constructor injection | inject `final` fields |
| `@Transactional` | Transaction boundary | `@Transactional(readOnly = true)` for reads |
| Repository | Data access | `userRepository.findById(id)` |
| MapStruct mapper | Entity ↔ DTO | `userMapper.toResponse(user)` |
| `@Slf4j` | Logging | `log.info("...", arg)` |
| Custom exceptions | Business errors | `throw new ResourceNotFoundException(...)` |
| Specifications | Dynamic queries | `userRepository.findAll(spec, pageable)` |
| `@Async` + `CompletableFuture` | Parallel work | `CompletableFuture.allOf(...).join()` |
