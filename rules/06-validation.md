# Validation Rules - iGoGo Backend

## Overview

Validation ensures data integrity and provides clear error messages. The project uses **Jakarta Bean Validation** (Hibernate Validator) for DTO validation, triggered by `@Valid` / `@Validated`, with a global `@RestControllerAdvice` that converts violations into a consistent error response.

## Validation System

### Triggering Validation

Annotate the controller argument with `@Valid` (request bodies) or annotate the controller with `@Validated` (for `@RequestParam`/`@PathVariable` and method-level constraints).

```java
@RestController
@RequestMapping("/users")
@Validated   // enables method-level validation of params/path variables
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("user.created", userService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findOne(
            @PathVariable @org.hibernate.validator.constraints.UUID UUID id) {
        return ApiResponse.success("user.found", userService.findById(id));
    }
}
```

Configuration is mostly automatic when `spring-boot-starter-validation` is on the classpath. To strip/ignore unknown JSON properties (the equivalent of `whitelist`/`forbidNonWhitelisted`), configure Jackson:

```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: false   # ignore extra fields (like whitelist: true)
```

### Exception Handling

A `GlobalExceptionHandler` (`@RestControllerAdvice`) handles `MethodArgumentNotValidException` (body) and `ConstraintViolationException` (params) and produces the standardized format (see 09-error-handling.md):

```json
{
  "success": false,
  "error": { "email": "Email format is invalid" },
  "errors": { "email": ["Email format is invalid", "Email already exists"] },
  "message": "Bad Request"
}
```

## Validation Message Format

### Priority-Based Messages

Format: `"priority.fieldName.errorType"`. The advice sorts messages per field by the numeric prefix to surface the most important one in `error`.

```java
public record CreateUserRequest(

    @NotBlank(message = "1.email.required")        // Priority 1
    @Email(message = "2.email.invalid")            // Priority 2
    @UniqueEmail(message = "3.email.alreadyExists")// Priority 3
    String email
) {}
```

### Priority Guidelines

- **Priority 1:** Required field validation (`@NotNull`, `@NotBlank`, `@NotEmpty`)
- **Priority 2:** Format/type validation (`@Email`, `@Pattern`)
- **Priority 3:** Length validation (`@Size`, `@Min`, `@Max`)
- **Priority 4+:** Business rules and complex validation

### Message Files (`MessageSource`)

```properties
# messages_en.properties
validation.email.required=Email is required
validation.email.invalid=Email format is invalid
validation.email.alreadyExists=Email already exists
```

```properties
# messages_vi.properties
validation.email.required=Email là bắt buộc
validation.email.invalid=Định dạng email không hợp lệ
validation.email.alreadyExists=Email đã tồn tại
```

> You can also let Hibernate Validator resolve message templates directly. Configure it to use Spring's `MessageSource` via a `LocalValidatorFactoryBean` so `{key}` messages and i18n stay in one place.

## Built-in Constraints

### String Validation

```java
public record ExampleRequest(

    @NotBlank(message = "1.username.required")
    @Size(min = 3, max = 20, message = "2.username.invalidLength")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "3.username.invalidCharacters")
    String username,

    @Size(max = 500, message = "1.bio.tooLong")
    String bio,

    @Pattern(regexp = "^[A-Z]{3}\\d{3}$", message = "1.code.invalidFormat")
    String code   // Format: ABC123
) {}
```

### Number Validation

```java
public record ProductRequest(

    @NotNull(message = "1.price.required")
    @DecimalMin(value = "0.01", message = "2.price.tooLow")
    @DecimalMax(value = "1000000", message = "3.price.tooHigh")
    BigDecimal price,

    @NotNull(message = "1.quantity.required")
    @Min(value = 1, message = "2.quantity.minimum")
    @Max(value = 9999, message = "3.quantity.maximum")
    Integer quantity,

    @Min(value = 0, message = "1.discount.minimum")
    @Max(value = 100, message = "2.discount.maximum")
    @MultipleOf5(message = "3.discount.mustBeDivisibleBy5")   // custom constraint
    Integer discountPercent
) {}
```

> Jakarta Bean Validation has no `@IsDivisibleBy`; implement a small custom constraint (see Custom Validators).

### Boolean Validation

```java
public record SettingsRequest(

    @NotNull(message = "1.isActive.required")
    Boolean isActive,

    @AssertTrue(message = "1.terms.mustAccept")
    boolean termsAccepted
) {}
```

### Email Validation

```java
public record ContactRequest(

    @NotBlank @Email(message = "1.email.invalid")
    @Schema(example = "user@example.com")
    String email,

    // Stricter pattern via regexp attribute
    @Email(regexp = "^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$", message = "1.email.invalid")
    String strictEmail
) {}
```

### URL Validation

```java
import org.hibernate.validator.constraints.URL;

public record ProfileRequest(

    @URL(message = "1.website.invalidUrl")
    @Schema(example = "https://example.com")
    String website,

    @URL(protocol = "https", message = "1.url.invalidUrl")
    String profileUrl
) {}
```

### UUID Validation

```java
import org.hibernate.validator.constraints.UUID;

public record RelationRequest(

    @NotNull @UUID(message = "1.userId.invalidUuid")
    java.util.UUID userId,   // bind directly to UUID — invalid value fails deserialization

    @NotNull
    java.util.UUID orderId
) {}
```

### Phone Number Validation

There is no built-in `@IsPhoneNumber`; use `@Pattern` or a custom `@ValidPhone` constraint (with libphonenumber):

```java
public record ContactRequest(

    @ValidPhone(region = "VN", message = "1.phoneNumber.invalid")
    @Schema(example = "+84901234567")
    String phoneNumber,

    @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "1.phone.invalid")
    String phone
) {}
```

### Collection Validation

```java
public record OrderRequest(

    @NotEmpty(message = "1.tags.empty")
    @Size(min = 1, max = 10, message = "2.tags.invalidSize")
    @UniqueElements(message = "3.tags.duplicate")   // hibernate-validator
    List<@NotBlank(message = "4.tags.invalidFormat") String> tags,

    @NotEmpty(message = "1.items.empty")
    List<@Valid OrderItemRequest> items   // @Valid cascades into each element
) {}

public record OrderItemRequest(
    @NotNull @org.hibernate.validator.constraints.UUID java.util.UUID productId,
    @NotNull @Min(value = 1, message = "1.quantity.minimum") Integer quantity
) {}
```

### Enum Validation

```java
public enum UserRole { ADMIN, USER, MODERATOR }

public record UpdateRoleRequest(

    @NotNull(message = "1.role.invalid")
    UserRole role,   // binding to the enum rejects unknown values

    @Pattern(regexp = "active|inactive|suspended", message = "1.status.invalid")
    String status
) {}
```

### Nested Object Validation

```java
public record AddressRequest(
    @NotBlank(message = "1.street.required") String street,
    @NotBlank(message = "1.city.required") String city,
    @NotBlank(message = "1.country.required") String country
) {}

public record UserRequest(
    @NotBlank String name,

    @NotNull @Valid    // cascade validation into the nested object
    @Schema(implementation = AddressRequest.class)
    AddressRequest address
) {}
```

## Custom Validators

A custom constraint = an annotation (`@Constraint`) + a `ConstraintValidator`. Validators are Spring beans, so they can inject dependencies.

### Simple Constraint (date format)

```java
// validator/ValidDate.java
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDateValidator.class)
public @interface ValidDate {
    String message() default "1.date.invalid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// validator/ValidDateValidator.java
public class ValidDateValidator implements ConstraintValidator<ValidDate, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;
        try {
            LocalDate.parse(value, DateTimeFormatter.ofPattern(AppConstants.DATE_FORMAT));
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
```

> Prefer binding date fields to `LocalDate`/`LocalDateTime` directly; only use a string + `@ValidDate` when the raw string must be preserved.

### Unique Value Constraint (injects repository)

```java
// validator/UniqueEmail.java
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "2.email.alreadyExists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// validator/UniqueEmailValidator.java
@Component
@RequiredArgsConstructor
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext ctx) {
        if (email == null) return true;        // let @NotBlank handle null
        return !userRepository.existsByEmail(email);
    }
}

// Usage
public record CreateUserRequest(
    @NotBlank @Email(message = "1.email.invalid")
    @UniqueEmail(message = "2.email.alreadyExists")
    String email
) {}
```

### Cross-field Constraint (date comparison)

Compare two fields with a class-level constraint:

```java
// validator/EndAfterStart.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EndAfterStartValidator.class)
public @interface EndAfterStart {
    String message() default "2.endDate.mustBeAfterStart";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// validator/EndAfterStartValidator.java
public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, CreateEventRequest> {
    @Override
    public boolean isValid(CreateEventRequest req, ConstraintValidatorContext ctx) {
        if (req.startDate() == null || req.endDate() == null) return true;
        if (req.endDate().isAfter(req.startDate())) return true;
        // attach the error to the endDate node
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
           .addPropertyNode("endDate").addConstraintViolation();
        return false;
    }
}

@EndAfterStart
public record CreateEventRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
```

### Password Strength Constraint

```java
// validator/StrongPasswordValidator.java
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext ctx) {
        if (password == null) return false;
        return password.length() >= 8
            && password.matches(".*[A-Z].*")
            && password.matches(".*[a-z].*")
            && password.matches(".*\\d.*")
            && password.matches(".*[!@#$%^&*(),.?\":{}|<>].*");
    }
}

// Usage
public record CreateUserRequest(
    @NotBlank(message = "1.password.required")
    @StrongPassword(message = "3.password.weak")
    String password
) {}
```

## Optional Fields

### Records: omit the required constraints

In a record, an optional field is simply one without `@NotNull`/`@NotBlank`. Format constraints still apply when a value is present.

```java
public record UpdateProfileRequest(
    @Size(min = 2, message = "1.fullName.tooShort") String fullName,   // optional
    @Min(0) @Max(150) Integer age                                      // optional (boxed)
) {}
```

### Default Values

Use a compact constructor (records have no field initializers):

```java
public record PaginationRequest(Integer page, Integer size) {
    public PaginationRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}
```

## Conditional Validation

Use validation **groups** or a class-level constraint for "validate B only when A == X". (There is no `@ValidateIf`.)

```java
public enum PaymentMethod { CREDIT_CARD, BANK_TRANSFER, CASH }

@ValidPaymentDetails  // class-level constraint enforces conditional rules
public record CreateOrderRequest(
    @NotNull(message = "1.paymentMethod.invalid") PaymentMethod paymentMethod,
    String cardNumber,   // required only for CREDIT_CARD
    String cvv,          // required only for CREDIT_CARD
    String bankAccount   // required only for BANK_TRANSFER
) {}

public class ValidPaymentDetailsValidator
        implements ConstraintValidator<ValidPaymentDetails, CreateOrderRequest> {
    @Override
    public boolean isValid(CreateOrderRequest r, ConstraintValidatorContext ctx) {
        return switch (r.paymentMethod()) {
            case CREDIT_CARD -> r.cardNumber() != null && r.cardNumber().matches("\\d{16}")
                             && r.cvv() != null && r.cvv().matches("\\d{3,4}");
            case BANK_TRANSFER -> r.bankAccount() != null && !r.bankAccount().isBlank();
            case CASH -> true;
        };
    }
}
```

### Validation Groups (alternative)

```java
public interface OnCreate {}
public interface OnUpdate {}

public record SaveUserRequest(
    @Null(groups = OnCreate.class) @NotNull(groups = OnUpdate.class) UUID id,
    @NotBlank(groups = OnCreate.class) String password
) {}

// Controller
@PostMapping
public ApiResponse<UserResponse> create(@Validated(OnCreate.class) @RequestBody SaveUserRequest req) { ... }
```

## Transformation

Jackson handles type conversion during deserialization. Normalize values in a record compact constructor or with a global Jackson module — there is no class-transformer.

```java
public record QueryRequest(Integer page, Boolean isActive, String name, @Email String email, List<String> tags) {
    public QueryRequest {
        name  = name  != null ? name.trim() : null;
        email = email != null ? email.toLowerCase().trim() : null;
    }
}
```

For converting query parameters (e.g. comma-separated values to a list), Spring binds `?tags=a&tags=b` to `List<String>` automatically; for custom string parsing, register a `Converter<String, T>` or use `@InitBinder`.

## Best Practices

### 1. Always Provide Message Keys

```java
// Good
@Email(message = "1.email.invalid") String email
// Bad
@Email String email  // ❌ no message key
```

### 2. Use Priority-Based Messages

```java
@NotBlank(message = "1.email.required")
@Email(message = "2.email.invalid")
@UniqueEmail(message = "3.email.alreadyExists")
String email
```

### 3. Order Constraints by Importance

```java
@NotBlank(message = "1.password.required")
@Size(min = 8, max = 100, message = "2.password.invalidLength")
@StrongPassword(message = "3.password.weak")
String password
```

### 4. Use the Most Specific Type / Constraint

```java
// Good
@NotNull @org.hibernate.validator.constraints.UUID UUID userId
// Bad
@NotBlank String userId  // ❌ bind to UUID instead
```

### 5. Document with @Schema

```java
@Email(message = "1.email.invalid")
@Schema(description = "User email address", example = "user@example.com")
String email
```

### 6. Bind to the Right Java Type

Let Jackson convert query/body values by declaring the correct field type (`Integer`, `Boolean`, `LocalDate`, enum). Don't accept everything as `String`.

### 7. Validate Collection Elements

```java
// Good
List<@NotBlank String> tags
// Bad
List<String> tags  // ❌ elements unvalidated
```

## Common Validation Patterns

### Registration DTO

```java
public record RegisterRequest(

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @UniqueEmail(message = "3.email.alreadyExists")
    @Schema(example = "user@example.com")
    String email,

    @NotBlank(message = "1.password.required")
    @Size(min = 8, message = "2.password.tooShort")
    @StrongPassword(message = "3.password.weak")
    @Schema(example = "SecurePass123!")
    String password,

    @NotBlank(message = "1.fullName.required")
    @Size(min = 2, max = 100, message = "2.fullName.invalidLength")
    @Schema(example = "John Doe")
    String fullName
) {}
```

### Pagination DTO

```java
public record PaginationRequest(

    @Min(value = 0, message = "1.page.minimum")
    @Schema(defaultValue = "0")
    Integer page,

    @Min(value = 1, message = "1.size.minimum")
    @Max(value = 100, message = "2.size.maximum")
    @Schema(defaultValue = "20")
    Integer size
) {
    public PaginationRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}
```

## Testing Validation

Use the Jakarta `Validator` directly — fast and Spring-free for pure constraint tests.

```java
class CreateUserRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void passesWithValidData() {
        var dto = new CreateUserRequest("user@example.com", "SecurePass123!", "John Doe");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void failsWithInvalidEmail() {
        var dto = new CreateUserRequest("invalid-email", "SecurePass123!", "John Doe");
        var violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void failsWithShortPassword() {
        var dto = new CreateUserRequest("user@example.com", "123", "John Doe");
        var violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
```

> For constraints that inject Spring beans (e.g. `@UniqueEmail`), test through `@WebMvcTest`/`@SpringBootTest` so the validator's dependencies are wired.

## Quick Reference

| Constraint | Purpose | Example |
|-----------|---------|---------|
| `@NotNull` | Not null | `@NotNull Integer age` |
| `@NotBlank` | String not null/blank | `@NotBlank String name` |
| `@NotEmpty` | Collection/String not empty | `@NotEmpty List<UUID> ids` |
| `@Email` | Email format | `@Email String email` |
| `@Size` | Length/size range | `@Size(min = 8, max = 100)` |
| `@Min` / `@Max` | Integer bounds | `@Min(0) @Max(100)` |
| `@DecimalMin` / `@DecimalMax` | Decimal bounds | `@DecimalMin("0.01")` |
| `@Pattern` | Regex | `@Pattern(regexp = "^[A-Z].*")` |
| `@Positive` / `@Negative` | Sign | `@Positive BigDecimal amount` |
| `@Past` / `@Future` | Date constraints | `@Future LocalDate date` |
| `@AssertTrue` | Must be true | `@AssertTrue boolean accepted` |
| `@URL` (hibernate) | URL format | `@URL String website` |
| `@UUID` (hibernate) | UUID format | `@UUID UUID id` |
| `@UniqueElements` (hibernate) | No duplicates | `@UniqueElements List<String> tags` |
| `@Valid` | Cascade into nested | `@Valid AddressRequest address` |
| `@Constraint` | Declare custom constraint | `@Constraint(validatedBy = ...)` |
| `ConstraintValidator` | Custom logic | `implements ConstraintValidator<A, T>` |
