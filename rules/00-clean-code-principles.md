# Clean Code Principles - iGoGo Backend

## Core Principles

### 1. Single Responsibility Principle (SRP)

- Each class/method should have ONE reason to change
- Controllers handle HTTP requests ONLY
- Services contain business logic ONLY
- Repositories process database access ONLY
- Validators validate data ONLY

### 2. DRY (Don't Repeat Yourself)

- Extract shared logic into services, utils, or helpers
- Use an abstract `BaseService` or a common repository interface when appropriate
- Leverage Spring features like Bean Validation, AOP, and MapStruct
- Centralize constants and enums

### 3. KISS (Keep It Simple, Stupid)

- Prefer simple solutions over complex ones
- Avoid premature optimization
- Write self-documenting code
- Use clear variable names

### 4. Code Readability

- Code is read more than written
- Use meaningful names
- Keep methods small (max 20-30 lines)
- Maximum nesting level: 3
- Add comments ONLY when necessary

### 5. Error Handling

- Always handle errors explicitly
- Throw appropriate exceptions and let `@RestControllerAdvice` translate them to HTTP responses
- Log errors before throwing
- Never swallow exceptions silently

### 6. Consistency

- Follow existing patterns in the codebase
- Use consistent naming conventions
- Follow the established project structure
- Maintain consistent formatting (use Spotless / google-java-format)

## Code Organization

### Package Structure

```
src/main/java/com/igogo/
├── config/
├── controller/
├── service/
│   └── impl/
├── repository/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── exception/
├── util/
├── constant/
├── mapper/
└── validator/
```

### Separation of Concerns

- **Controllers:** Handle HTTP, validate input, return responses
- **Services:** Business logic, orchestration, external API calls
- **ServiceImpl:** Concrete implementation of the service interface
- **Repositories:** Spring Data JPA / Hibernate data access
- **DTOs:** Data validation and transformation (use `record` for immutable payloads)
- **Entities:** Database schema representation (`@Entity`)
- **Mappers:** Convert Entity ↔ DTO (e.g., MapStruct)

## Best Practices

### 1. Dependency Injection

```java
// Good - Use constructor injection (Lombok @RequiredArgsConstructor)
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
}

// Bad - Field injection makes testing harder and hides dependencies
@Autowired
private UserRepository userRepository;
```

### 2. Asynchronous Processing

```java
// Good - Offload long-running work with @Async (returns CompletableFuture)
@Async
public CompletableFuture<User> findUser(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("user.notFound"));
    return CompletableFuture.completedFuture(user);
}

// Bad - Blocking on a future or doing heavy work on the request thread
public User findUser(Long id) {
    return userRepository.findById(id).get(); // ❌
}
```

### 3. Type Safety

```java
// Good - Use strong types and records
public record CreateUserRequest(
    @NotBlank String email,
    @NotBlank String name,
    @Min(1) int age
) {}

public User createUser(CreateUserRequest data) {
    // ...
}

// Bad - Use Object / Map<String, Object> as a catch-all
public User createUser(Object data) { // ❌
    // ...
}
```

### 4. Magic Numbers and Strings

```java
// Good - Use constants
public final class AppConstants {
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int TOKEN_EXPIRY_HOURS = 24;

    private AppConstants() {}
}

if (attempts > AppConstants.MAX_LOGIN_ATTEMPTS) {
    // ...
}

// Bad - Use magic numbers
if (attempts > 5) { // ❌
    // ...
}
```

### 5. Method Parameters

```java
// Good - Max 3 parameters; wrap more into a request object
public User createUser(CreateUserRequest request) {
    return userService.create(request);
}

// Bad - Too many parameters
public User createUser(String email, String name, int age, String address, String phone) { // ❌
    return null;
}
```

## Documentation

### When to Comment

- Complex algorithms that aren't immediately obvious
- Business rules that require context
- Workarounds for third-party library issues
- TODOs with assignee and date

### When NOT to Comment

- Self-explanatory code
- Redundant comments that repeat the code
- Commented-out code (use git history instead)

```java
// Good - Explain WHY
// Using BCrypt strength 12 based on OWASP 2024 recommendations
String hashed = passwordEncoder.encode(password);

// Bad - Explain WHAT (code already shows this)
// Hash the password
String hashed = passwordEncoder.encode(password); // ❌

// Good - TODO format
// TODO(username): Implement caching layer - 2025-01-15
```

## Code Smells to Avoid

### 1. Long Methods

- Break into smaller, focused methods
- Extract complex logic into private helper methods

### 2. Large Classes

- Split into multiple services
- Follow Single Responsibility Principle

### 3. Duplicate Code

- Extract into shared utilities
- Create reusable validators / aspects

### 4. Complex Conditionals

```java
// Good - Extract to a well-named method
private boolean isEligibleForDiscount(User user) {
    return user.getAge() >= 65
        || user.isPremium()
        || user.getTotalOrders() > 100;
}

if (isEligibleForDiscount(user)) {
    // ...
}

// Bad - Complex inline condition
if (user.getAge() >= 65 || user.isPremium() || user.getTotalOrders() > 100) { // ❌
    // ...
}
```

### 5. Primitive Obsession

```java
// Good - Use proper value objects
public record Money(BigDecimal amount, Currency currency) {}

// Bad - Use primitives everywhere
public BigDecimal calculateTotal(BigDecimal price, String currency) { // ❌
    // ...
}
```

## Performance Considerations

### 1. Database Queries

- Use connection pooling (HikariCP, already configured)
- Avoid N+1 queries (use `@EntityGraph`, `JOIN FETCH`, or projections)
- Use indexes on frequently queried columns
- Limit result sets appropriately (pagination)

### 2. Caching

- Cache frequently accessed data with Spring Cache (`@Cacheable`)
- Use Redis or an in-memory cache for sessions
- Invalidate cache appropriately (`@CacheEvict`)

### 3. Async Operations

- Use `@Async` for background tasks
- Avoid blocking request threads

## Security Best Practices

### 1. Input Validation

- Always validate user input using DTOs
- Use Bean Validation annotations (`@NotBlank`, `@Email`, etc.)
- Sanitize data before database operations

### 2. SQL Injection Prevention

```java
// Good - Use parameterized queries / bound parameters
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// Never concatenate user input into JPQL/native SQL strings
```

### 3. Sensitive Data

- Never log passwords or tokens
- Use environment variables / Spring `application.yml` profiles for secrets
- Don't commit secret files (`.env`, credentials) to git

## Testing

### 1. Test Coverage

- Aim for 80%+ coverage
- Test business logic thoroughly
- Write integration tests for critical flows

### 2. Test Structure

- Arrange, Act, Assert pattern
- One logical assertion per test when possible
- Clear test method names

### 3. Mock External Dependencies

- Mock repositories and collaborators with Mockito
- Mock external APIs
- Mock time-dependent operations (`Clock`)

## Git Practices

### 1. Commit Messages

```
feat: add user authentication
fix: resolve email validation bug
refactor: simplify user service logic
docs: update API documentation
test: add user service tests
```

### 2. Branch Naming

```
feature/user-authentication
bugfix/email-validation
hotfix/critical-security-patch
```

### 3. Pull Requests

- Keep PRs small and focused
- Write descriptive PR descriptions
- Link related issues
- Request code review before merging

## Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/index.html)
- [Spring Framework Documentation](https://docs.spring.io/spring-framework/reference/index.html)
- [Clean Code by Robert C. Martin](https://www.amazon.com/Clean-Code-Handbook-Software-Craftsmanship/dp/0132350882)
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- [Effective Java by Joshua Bloch](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)
