# Error Handling Rules - iGoGo Backend

## Overview

Proper error handling ensures consistent API responses, meaningful messages, and easier debugging. The project uses a small hierarchy of **custom exceptions** plus a single global `@RestControllerAdvice` (`GlobalExceptionHandler`) that maps exceptions to HTTP responses. Services throw exceptions; the advice formats them. Messages are resolved via Spring `MessageSource` (i18n).

## Error Response Format

### Standard Error Response

The `GlobalExceptionHandler` produces:

```json
{
  "success": false,
  "error": { "email": "Email format is invalid" },
  "errors": { "email": ["Email format is invalid", "Email already exists"] },
  "message": "Bad Request"
}
```

### Properties

- **success**: always `false` for errors
- **error**: field name → first (highest priority) error message
- **errors**: field name → array of all error messages
- **message**: HTTP status reason phrase

## Custom Exception Hierarchy

Define a small set of unchecked exceptions carrying a message **key** (resolved by `MessageSource`) and a default HTTP status.

```java
// exception/AppException.java - base type
@Getter
public abstract class AppException extends RuntimeException {
    private final HttpStatus status;
    private final transient Object[] args;   // optional message arguments

    protected AppException(String messageKey, HttpStatus status, Object... args) {
        super(messageKey);            // getMessage() returns the i18n KEY
        this.status = status;
        this.args = args;
    }
}

// 404
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String key, Object... args) { super(key, HttpStatus.NOT_FOUND, args); }
}
// 400
public class BadRequestException extends AppException {
    public BadRequestException(String key, Object... args) { super(key, HttpStatus.BAD_REQUEST, args); }
}
// 401
public class UnauthorizedException extends AppException {
    public UnauthorizedException(String key, Object... args) { super(key, HttpStatus.UNAUTHORIZED, args); }
}
// 403
public class ForbiddenException extends AppException {
    public ForbiddenException(String key, Object... args) { super(key, HttpStatus.FORBIDDEN, args); }
}
// 409
public class ConflictException extends AppException {
    public ConflictException(String key, Object... args) { super(key, HttpStatus.CONFLICT, args); }
}
// 422
public class UnprocessableEntityException extends AppException {
    public UnprocessableEntityException(String key, Object... args) { super(key, HttpStatus.UNPROCESSABLE_ENTITY, args); }
}
// 500
public class InternalServerException extends AppException {
    public InternalServerException(String key, Object... args) { super(key, HttpStatus.INTERNAL_SERVER_ERROR, args); }
}
```

### When to Use Each

| HTTP | Exception | Use case |
|------|-----------|----------|
| 400 | `BadRequestException` | Invalid input / malformed request |
| 401 | `UnauthorizedException` | Missing/invalid authentication |
| 403 | `ForbiddenException` | Insufficient permissions |
| 404 | `ResourceNotFoundException` | Resource doesn't exist |
| 409 | `ConflictException` | Duplicate / version conflict |
| 422 | `UnprocessableEntityException` | Valid syntax, semantically wrong |
| 500 | `InternalServerException` | Unexpected server error |

```java
// 400
if (request.items().isEmpty()) {
    throw new BadRequestException("order.emptyCart");
}
// 401
if (decoded == null) {
    throw new UnauthorizedException("auth.invalidToken");
}
// 403
if (!currentUser.isAdmin() && !currentUserId.equals(targetUserId)) {
    throw new ForbiddenException("user.insufficientPermissions");
}
// 404
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
// 409
if (userRepository.existsByEmail(request.email())) {
    throw new ConflictException("user.emailExists");
}
// 422
if (request.endDate().isBefore(request.startDate())) {
    throw new UnprocessableEntityException("event.endBeforeStart");
}
```

## Global Exception Handler (@RestControllerAdvice)

A single advice maps every exception type to the standard response and resolves i18n messages.

```java
// exception/GlobalExceptionHandler.java
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    // ---- Custom application exceptions ----
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex, Locale locale) {
        String message = messageSource.getMessage(ex.getMessage(), ex.getArgs(), ex.getMessage(), locale);
        log.warn("{} -> {}: {}", ex.getClass().getSimpleName(), ex.getStatus(), message);
        return ResponseEntity.status(ex.getStatus())
            .body(ErrorResponse.simple(message, ex.getStatus().getReasonPhrase()));
    }

    // ---- Bean Validation on @RequestBody ----
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String msg = resolvePriorityMessage(fe.getDefaultMessage(), locale);
            errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>()).add(msg);
        }
        return ResponseEntity.badRequest().body(ErrorResponse.fields(errors, "Bad Request"));
    }

    // ---- Bean Validation on @RequestParam / @PathVariable ----
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, Locale locale) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String field = lastNode(v.getPropertyPath());
            errors.computeIfAbsent(field, k -> new ArrayList<>()).add(resolvePriorityMessage(v.getMessage(), locale));
        }
        return ResponseEntity.badRequest().body(ErrorResponse.fields(errors, "Bad Request"));
    }

    // ---- Type mismatch (e.g. bad UUID in path) ----
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.simple("Invalid value for '" + ex.getName() + "'", "Bad Request"));
    }

    // ---- DB integrity (unique/FK/etc.) ----
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, Locale locale) {
        return DbErrorTranslator.translate(ex)
            .map(app -> handleApp(app, locale))
            .orElseGet(() -> {
                log.error("Data integrity violation", ex);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.simple(msg("common.constraintViolation", locale), "Conflict"));
            });
    }

    // ---- Spring Security ----
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.simple(msg("user.insufficientPermissions", locale), "Forbidden"));
    }

    // ---- Fallback ----
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, Locale locale) {
        log.error("Unexpected error", ex);   // log full stack trace
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.simple(msg("common.serverError", locale), "Internal Server Error"));
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
    // resolvePriorityMessage(): strip the "1." priority prefix and resolve via MessageSource
}
```

> The advice replaces NestJS exception filters. There is exactly one per application; do not scatter try/catch-to-response logic across controllers.

## Throwing Patterns

### In Services

Services throw domain exceptions for business-rule violations. Catch only what you can meaningfully translate; let the advice handle the rest.

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userMapper.toResponse(userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound")));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("user.emailExists");   // pre-check
        }
        try {
            return userMapper.toResponse(userRepository.save(toEntity(request)));
        } catch (DataIntegrityViolationException ex) {          // race-condition safety net
            throw new ConflictException("user.emailExists");
        }
    }
}
```

### In Controllers

Controllers let service exceptions bubble up to the advice — no try/catch.

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findOne(@PathVariable UUID id) {
        return ApiResponse.success("user.found", userService.findById(id));  // throws -> advice
    }
}
```

## Database Error Handling

Spring wraps JDBC `SQLException` into `DataIntegrityViolationException`. Inspect the underlying PostgreSQL `SQLState` to map specific cases.

```java
public final class DbErrorTranslator {
    private DbErrorTranslator() {}

    public static Optional<AppException> translate(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql) {
            return switch (sql.getSQLState()) {
                case "23505" -> Optional.of(new ConflictException("user.emailExists"));        // unique_violation
                case "23503" -> Optional.of(new BadRequestException("common.invalidReference"));// foreign_key_violation
                case "23514" -> Optional.of(new BadRequestException("common.constraintViolation")); // check_violation
                case "23502" -> Optional.of(new BadRequestException("common.requiredField"));   // not_null_violation
                case "22P02" -> Optional.of(new BadRequestException("common.invalidDataType")); // invalid_text_representation
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }
}
```

> Prefer pre-checks (`existsByEmail`) for user-friendly messages; use the translator as a safety net for concurrent inserts.

### Transactions and Errors

With `@Transactional`, a thrown runtime exception automatically rolls back the transaction — no manual `ROLLBACK`. Use a pessimistic lock for read-modify-write races.

```java
@Transactional
public void transferCredits(UUID fromId, UUID toId, BigDecimal amount) {
    User from = userRepository.findByIdForUpdate(fromId)        // SELECT ... FOR UPDATE
        .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    if (from.getCredits().compareTo(amount) < 0) {
        throw new BadRequestException("user.insufficientCredits");   // rolls back
    }
    from.setCredits(from.getCredits().subtract(amount));
    User to = userRepository.findById(toId)
        .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
    to.setCredits(to.getCredits().add(amount));
    log.info("Credits transferred: {} from {} to {}", amount, fromId, toId);
}
```

```java
// Repository pessimistic lock
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdForUpdate(@Param("id") UUID id);
```

## Custom Domain Exceptions

For richer errors, extend `AppException` and carry extra data.

```java
public class UserNotVerifiedException extends AppException {
    public UserNotVerifiedException() {
        super("auth.emailNotVerified", HttpStatus.FORBIDDEN);
    }
}

public class InsufficientCreditsException extends AppException {
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientCreditsException(BigDecimal required, BigDecimal available) {
        super("user.insufficientCredits", HttpStatus.BAD_REQUEST, required, available);
        this.required = required;
        this.available = available;
    }
    // getters used by the advice if you want to expose required/available/shortage
}
```

## Validation Errors

Bean Validation failures on a `@Valid @RequestBody` raise `MethodArgumentNotValidException`, handled by the advice (above). With message keys like `2.email.invalid`, strip the priority prefix and resolve via `MessageSource`:

```java
// DTO
public record CreateUserRequest(
    @NotBlank @Email(message = "2.email.invalid") String email,
    @Size(min = 8, message = "2.password.tooShort") String password
) {}
```

```json
// Resulting response
{
  "success": false,
  "error": { "email": "Email format is invalid", "password": "Password is too short" },
  "errors": {
    "email": ["Email format is invalid"],
    "password": ["Password is too short"]
  },
  "message": "Bad Request"
}
```

## Error Logging

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
            log.error("Failed to create user: {}", ex.getMessage(), ex);   // last arg = stack trace
            throw ex;
        }
    }
}
```

### What NOT to Log

```java
// ❌ NEVER log sensitive data
log.info("Password: {}", password);     // ❌
log.info("Token: {}", accessToken);     // ❌
log.info("Card number: {}", cardNumber);// ❌

// ✓ Log non-sensitive context
log.info("User {} logged in", userId);
log.error("Failed to charge card ending in {}", last4);
```

## Best Practices

### 1. Throw Specific Domain Exceptions

```java
// Good
throw new ResourceNotFoundException("user.notFound");
// Bad
throw new RuntimeException("User not found");  // ❌ generic, no status
```

### 2. Use Message Keys (i18n), Not Hardcoded Text

```java
// Good
throw new ResourceNotFoundException("user.notFound");
// Bad
throw new ResourceNotFoundException("User not found");  // ❌ hardcoded English
```

### 3. Centralize Handling in the Advice

Don't catch-and-format in controllers. Let exceptions propagate to `GlobalExceptionHandler`.

### 4. Don't Swallow Exceptions

```java
// Good - translate then rethrow
catch (DataIntegrityViolationException ex) {
    throw new ConflictException("user.emailExists");
}
// Bad
catch (Exception ex) { return null; }   // ❌
```

### 5. Let the Fallback Handle the Unexpected

Only catch exceptions you can translate. The advice's `Exception` handler logs the stack trace and returns 500 — don't wrap everything yourself and lose the type.

### 6. Validate Business Rules in Services

```java
// Good - service enforces the rule
if (!user.isActive()) throw new ForbiddenException("user.accountInactive");

// Bad - business rule checked in the controller ❌
```

## Message Keys

```properties
# messages_en.properties
common.serverError=Internal server error
common.invalidReference=Invalid reference
common.constraintViolation=Constraint violation
common.requiredField=Required field is missing
common.invalidDataType=Invalid data type
user.notFound=User not found
user.emailExists=Email already exists
user.accountInactive=Account is inactive
user.insufficientCredits=Insufficient credits
user.insufficientPermissions=You don't have permission to perform this action
auth.tokenRequired=Authentication token is required
auth.invalidToken=Invalid or expired token
auth.emailNotVerified=Please verify your email address
auth.invalidCredentials=Invalid email or password
order.emptyCart=Cart cannot be empty
order.invalidAmount=Invalid order amount
event.endBeforeStart=End date must be after start date
```

## Quick Reference

| Exception | Status | Use case |
|-----------|--------|----------|
| `BadRequestException` | 400 | Invalid input, malformed request |
| `UnauthorizedException` | 401 | Missing/invalid authentication |
| `ForbiddenException` | 403 | Insufficient permissions |
| `ResourceNotFoundException` | 404 | Resource not found |
| `ConflictException` | 409 | Duplicate resource, conflict |
| `UnprocessableEntityException` | 422 | Semantic error in request |
| `InternalServerException` | 500 | Unexpected server error |

| Spring/Validation exception | Handled as | Notes |
|-----------------------------|-----------|-------|
| `MethodArgumentNotValidException` | 400 field errors | `@Valid @RequestBody` |
| `ConstraintViolationException` | 400 field errors | `@Validated` params |
| `MethodArgumentTypeMismatchException` | 400 | bad path/query type |
| `DataIntegrityViolationException` | mapped via `DbErrorTranslator` | unique/FK/etc. |
| `AccessDeniedException` | 403 | Spring Security |

| PostgreSQL SQLState | Meaning | Mapped exception |
|---------------------|---------|------------------|
| `23505` | Unique violation | `ConflictException` |
| `23503` | Foreign key violation | `BadRequestException` |
| `23514` | Check violation | `BadRequestException` |
| `23502` | Not-null violation | `BadRequestException` |
| `22P02` | Invalid text representation | `BadRequestException` |
