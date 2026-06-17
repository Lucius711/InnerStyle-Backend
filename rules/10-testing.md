# Testing Rules - iGoGo Backend

## Overview

Testing ensures code quality, prevents regressions, and documents expected behavior. The project uses **JUnit 5** with **Mockito** and **AssertJ** for unit tests, Spring Boot **test slices** (`@WebMvcTest`, `@DataJpaTest`) for layer tests, and **`@SpringBootTest` + Testcontainers + MockMvc** for integration/end-to-end tests.

## Testing Stack

- **JUnit 5 (Jupiter)** — test framework
- **Mockito** — mocking (`@Mock`, `@InjectMocks`, `@MockBean`)
- **AssertJ** — fluent assertions (`assertThat(...)`)
- **Spring Boot Test** — `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, `MockMvc`
- **Testcontainers** — real PostgreSQL for integration tests
- **JaCoCo** — coverage

### Commands

```bash
./mvnw test            # unit + slice tests (surefire, *Test.java)
./mvnw verify          # also runs integration tests (failsafe, *IT.java)
./mvnw jacoco:report   # coverage report (target/site/jacoco)
./mvnw -Dtest=UserServiceImplTest test   # a single test class
```

## File Structure & Naming

Tests mirror the main package under `src/test/java`.

```
src/test/java/com/igogo/user/
├── service/impl/UserServiceImplTest.java   # unit test (Mockito)
├── controller/UserControllerTest.java      # web slice test (@WebMvcTest)
├── repository/UserRepositoryTest.java      # @DataJpaTest
└── UserControllerIT.java                   # integration test (@SpringBootTest)
```

- `*Test.java` — unit/slice tests, run by Surefire in `./mvnw test`.
- `*IT.java` — integration tests, run by Failsafe in `./mvnw verify`.

## Unit Testing (Service, Mockito)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl service;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setEmail("test@example.com");
        mockUser.setFullName("Test User");
    }

    @Nested
    class FindById {

        @Test
        void returnsUserWhenFound() {
            var response = new UserResponse(mockUser.getId(), "test@example.com", "Test User", true, Instant.now());
            when(userRepository.findById(mockUser.getId())).thenReturn(Optional.of(mockUser));
            when(userMapper.toResponse(mockUser)).thenReturn(response);

            UserResponse result = service.findById(mockUser.getId());

            assertThat(result.email()).isEqualTo("test@example.com");
            verify(userRepository).findById(mockUser.getId());
        }

        @Test
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user.notFound");
        }
    }

    @Nested
    class Create {

        private final CreateUserRequest request =
            new CreateUserRequest("new@example.com", "Password123!", "New User");

        @Test
        void createsUserSuccessfully() {
            var response = new UserResponse(UUID.randomUUID(), "new@example.com", "New User", true, Instant.now());
            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(passwordEncoder.encode(request.password())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);
            when(userMapper.toResponse(mockUser)).thenReturn(response);

            UserResponse result = service.create(request);

            assertThat(result.email()).isEqualTo("new@example.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        void throwsConflictWhenEmailExists() {
            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
            verify(userRepository, never()).save(any());
        }
    }
}
```

## Web Layer Testing (@WebMvcTest + MockMvc)

Tests the controller, JSON (de)serialization, validation, and the exception advice — collaborators are `@MockBean`s.

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserService userService;

    @Test
    void getReturnsUser() throws Exception {
        UUID id = UUID.randomUUID();
        var user = new UserResponse(id, "test@example.com", "Test User", true, Instant.now());
        when(userService.findById(id)).thenReturn(user);

        mockMvc.perform(get("/users/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void createReturns201() throws Exception {
        var request = new CreateUserRequest("new@example.com", "Password123!", "New User");
        var created = new UserResponse(UUID.randomUUID(), "new@example.com", "New User", true, Instant.now());
        when(userService.create(any())).thenReturn(created);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("new@example.com"));
    }

    @Test
    void returns400ForInvalidEmail() throws Exception {
        var request = new CreateUserRequest("invalid-email", "Password123!", "New User");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors.email").exists());
    }
}
```

> If the controller is secured, add `spring-security-test` and `@WithMockUser` (or import the security config and provide a test JWT).

## Repository Testing (@DataJpaTest + Testcontainers)

Test custom queries against a **real PostgreSQL** (H2 doesn't match Postgres semantics like FK behavior, JSONB, arrays).

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private UserRepository userRepository;

    @Test
    void findsByEmail() {
        User u = new User();
        u.setEmail("test@example.com");
        u.setFullName("Test User");
        userRepository.save(u);

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }
}
```

> `@ServiceConnection` (Spring Boot 3.1+) wires the container's JDBC URL automatically — no manual `@DynamicPropertySource`.

## Integration / End-to-End Testing (@SpringBootTest)

Boots the full context against a Testcontainers Postgres and drives the API through MockMvc. Name the class `*IT` so Failsafe runs it on `./mvnw verify`.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional   // roll back DB changes after each test
class UserControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String body(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    @Test
    void createsUser() throws Exception {
        var request = new CreateUserRequest("test@example.com", "Password123!", "Test User");

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void returns409ForDuplicateEmail() throws Exception {
        var request = new CreateUserRequest("dup@example.com", "Password123!", "Test User");
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void returns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/users/{id}", "00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound());
    }
}
```

## Best Practices

### 1. Arrange-Act-Assert

```java
@Test
void createsUser() {
    // Arrange
    var request = new CreateUserRequest("test@example.com", "Password123!", "Test User");
    when(userService.create(request)).thenReturn(expected);

    // Act
    UserResponse result = service.create(request);

    // Assert
    assertThat(result).isEqualTo(expected);
    verify(userService).create(request);
}
```

### 2. Clear, Behavior-Oriented Names

```java
// Good
@Test void throwsNotFoundWhenUserMissing() { }
@Test void returnsUserWhenFound() { }

// Bad
@Test void test1() { }   // ❌
```

Use `@Nested` classes to group by method under test, and `@DisplayName` for readable reports.

### 3. One Logical Assertion per Test

Prefer focused tests; AssertJ chaining for related properties of one outcome is fine.

```java
assertThat(user)
    .extracting(UserResponse::id, UserResponse::email)
    .containsExactly(expectedId, "test@example.com");
```

### 4. Mock Collaborators, Not the Class Under Test

```java
// Good - mock the repository
@Mock UserRepository userRepository;
// Bad - hitting a real DB in a unit test ❌
```

### 5. No Manual Cleanup Needed for Mocks

`MockitoExtension` resets mocks per test. For integration tests, use `@Transactional` (auto-rollback) or `@Sql` scripts to reset state — avoid leaking data between tests.

### 6. Test Edge Cases

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "invalid", "a@", "@b.com"})
void rejectsInvalidEmails(String email) {
    var dto = new CreateUserRequest(email, "Password123!", "Name");
    assertThat(validator.validate(dto))
        .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
}
```

### 7. Use Builders / Factories for Fixtures

Centralize test data creation (e.g. an `Instancio`/`@TestFactory` or a simple `UserTestData` helper) instead of repeating object construction.

## Mocking Patterns (Mockito)

```java
// Stub a return value
when(userRepository.findById(id)).thenReturn(Optional.of(user));

// Stub consecutive calls
when(userRepository.existsByEmail(any()))
    .thenReturn(false)   // first call
    .thenReturn(true);   // second call

// Stub a thrown exception
when(emailService.send(any())).thenThrow(new MailException("down") {});

// Verify interactions
verify(userRepository).save(any(User.class));
verify(emailService, never()).send(any());
verify(auditService, times(1)).log(any());

// Capture arguments
ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(userRepository).save(captor.capture());
assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
```

## Coverage Goals

Configure JaCoCo thresholds in the Maven plugin (`pom.xml`):

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>check</id>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### What to Test
- ✓ Business logic in services
- ✓ Controller request/response & status codes
- ✓ Validation logic
- ✓ Error handling (the advice)
- ✓ Edge cases & boundaries
- ✓ Authentication & authorization

### What NOT to Test
- ❌ Framework / third-party code
- ❌ Trivial getters/setters / Lombok-generated code
- ❌ Spring Data derived queries with no custom logic (test custom `@Query` instead)
- ❌ Constants & configuration

## Testing DTOs (Bean Validation)

```java
class CreateUserRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void passesWithValidData() {
        var dto = new CreateUserRequest("test@example.com", "Password123!", "Test User");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void failsWithInvalidEmail() {
        var dto = new CreateUserRequest("invalid-email", "Password123!", "Test User");
        assertThat(validator.validate(dto))
            .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }
}
```

## Testing Security (filters / method security)

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserService userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDelete() throws Exception {
        mockMvc.perform(delete("/users/{id}", UUID.randomUUID()).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }
}
```

## Continuous Integration

```yaml
# .github/workflows/test.yml
name: Tests
on:
  push: { branches: [main, develop] }
  pull_request: { branches: [main, develop] }
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Static analysis
        run: ./mvnw -B checkstyle:check spotless:check
      - name: Unit + integration tests
        run: ./mvnw -B verify          # Testcontainers needs Docker (available on ubuntu-latest)
      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: ./target/site/jacoco/jacoco.xml
```

## Quick Reference

| Command | Purpose |
|---------|---------|
| `./mvnw test` | Unit + slice tests |
| `./mvnw verify` | + integration tests (`*IT`) |
| `./mvnw jacoco:report` | Coverage report |
| `./mvnw -Dtest=ClassName test` | Single test class |

| Annotation | Usage |
|-----------|-------|
| `@ExtendWith(MockitoExtension.class)` | Pure unit test with mocks |
| `@WebMvcTest` | Controller/web slice + MockMvc |
| `@DataJpaTest` | Repository slice |
| `@SpringBootTest` | Full context (integration) |
| `@AutoConfigureMockMvc` | MockMvc in `@SpringBootTest` |
| `@Testcontainers` / `@Container` | Real PostgreSQL |
| `@MockBean` | Replace a context bean with a mock |
| `@Mock` / `@InjectMocks` | Mockito unit wiring |
| `@WithMockUser` | Authenticated test principal |

| Assertion / Mock | Example |
|------------------|---------|
| `assertThat(x).isEqualTo(y)` | value equality |
| `assertThatThrownBy(() -> ...).isInstanceOf(...)` | expected exception |
| `assertThat(opt).isPresent()` / `.isEmpty()` | Optional |
| `when(...).thenReturn(...)` | stub |
| `verify(mock).method(args)` | interaction check |
| `mockMvc.perform(...).andExpect(status().isOk())` | web assertion |
| `jsonPath("$.data.email").value(...)` | JSON assertion |
