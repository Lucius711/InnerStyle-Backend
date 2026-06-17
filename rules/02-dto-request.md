# DTO Request Rules - iGoGo Backend

## Overview

Request DTOs (Data Transfer Objects) are used to validate and transform incoming HTTP request data. They ensure type safety and data integrity before processing business logic. Prefer Java `record`s for request payloads — they are immutable and concise — and annotate them with Jakarta Bean Validation constraints.

## File Structure

```
src/main/java/com/igogo/<feature>/dto/request/
├── Create<Feature>Request.java
├── Update<Feature>Request.java
├── <SpecificAction>Request.java
└── <Feature>Filter.java
```

## Naming Conventions

### File / Class Names

- Use PascalCase (file name matches the type)
- Suffix request DTOs with `Request` (or `Filter` for query DTOs)
- Be specific about the action

```java
// Good
CreateUserRequest.java
UpdateUserProfileRequest.java
ChangePasswordRequest.java
UserFilter.java
LoginRequest.java

// Bad
UserDto.java              // ❌ not specific
UserRequestDTO.java       // ❌ redundant DTO, uppercase
createUser.java           // ❌ wrong case
```

## Basic Structure

### Template (record + Bean Validation + springdoc)

```java
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateExampleRequest(

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @NotBlank(message = "1.name.required")
    @Size(min = 2, max = 100, message = "2.name.invalidLength")
    @Schema(description = "User full name", example = "John Doe", minLength = 2, maxLength = 100)
    String fullName,

    @Size(max = 500, message = "1.bio.tooLong")
    @Schema(description = "User biography", example = "Software developer", maxLength = 500)
    String bio
) {}
```

> A `null` optional field simply isn't validated by `@Size`/`@Email` (those constraints pass on `null`). Use `@NotNull`/`@NotBlank` only on required fields.

## Validation Constraints

### Required Fields

```java
public record ExampleRequest(

    @NotBlank(message = "1.name.required")
    String name,

    @NotNull(message = "1.age.required")
    Integer age,

    @NotNull(message = "1.active.required")
    Boolean isActive,

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    String email,

    @NotBlank(message = "1.website.required")
    @org.hibernate.validator.constraints.URL(message = "2.website.invalid")
    String website,

    @NotNull(message = "1.userId.required")
    UUID userId
) {}
```

### Optional Fields

In a record, an optional field is simply one without `@NotNull`/`@NotBlank`. Validate it only when present:

```java
public record UpdateUserRequest(

    @Size(min = 2, message = "1.name.tooShort")
    String name,           // optional

    @Min(value = 0, message = "1.age.negative")
    Integer age            // optional (boxed type allows null)
) {}
```

### String Validation

```java
public record UserRequest(

    @NotBlank(message = "1.username.required")
    @Size(min = 3, max = 20, message = "2.username.invalidLength")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "3.username.invalidFormat")
    String username,

    @NotBlank(message = "1.password.required")
    @Size(min = 8, message = "2.password.tooShort")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
        message = "3.password.weak")
    String password
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
    Integer quantity
) {}
```

### Collection Validation

```java
public record OrderRequest(

    @NotEmpty(message = "1.items.empty")
    @Size(max = 100, message = "2.items.tooMany")
    List<@NotBlank(message = "3.items.invalidFormat") String> items,

    List<@NotBlank String> tags,

    // Nested object validation: @Valid cascades into each element
    @NotEmpty(message = "1.orderItems.empty")
    List<@Valid OrderItemRequest> orderItems
) {}

public record OrderItemRequest(

    @NotBlank(message = "1.productId.required")
    String productId,

    @NotNull(message = "1.quantity.required")
    @Min(value = 1, message = "2.quantity.minimum")
    Integer quantity
) {}
```

### Date / Time Validation

Use proper `java.time` types so Jackson parses and validates the format automatically; add `@Future`/`@PastOrPresent` where relevant.

```java
public record EventRequest(

    @NotNull(message = "1.startDate.required")
    @Schema(description = "Event start date", example = "2025-01-18")
    LocalDate startDate,

    @NotNull(message = "1.endDate.required")
    @Schema(description = "Event end date", example = "2025-01-20")
    LocalDate endDate
) {}
```

Cross-field rules (e.g. `endDate` after `startDate`) belong in a class-level constraint — see Custom Validators below.

### Enum Validation

Binding directly to an enum type means an invalid value fails deserialization automatically. Use `@NotNull` to require it:

```java
public enum UserRole { ADMIN, USER, MODERATOR }

public record UpdateRoleRequest(

    @NotNull(message = "1.role.invalid")
    @Schema(implementation = UserRole.class, example = "USER")
    UserRole role,

    // For a constrained string, use a custom @ValueOfEnum or @Pattern
    @Pattern(regexp = "active|inactive|suspended", message = "1.status.invalid")
    String status
) {}
```

### Email and Phone Validation

```java
public record ContactRequest(

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @Schema(description = "Email address", example = "user@example.com")
    String email,

    @ValidPhone(message = "1.phoneNumber.invalid")   // custom constraint
    @Schema(description = "Phone number (Vietnam)", example = "+84901234567")
    String phoneNumber,

    // Alternative: plain regex
    @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "1.phone.invalid")
    String phone
) {}
```

## Custom Validators

### Field-level Validator (ConstraintValidator)

A custom constraint is an annotation + a `ConstraintValidator`. The validator is a Spring bean, so it can inject repositories.

```java
// File: validator/UniqueEmail.java
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "1.email.alreadyExists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// File: validator/UniqueEmailValidator.java
@Component
@RequiredArgsConstructor
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) {
            return true;   // let @NotBlank handle null
        }
        return !userRepository.existsByEmail(email);
    }
}

// Usage in DTO
public record CreateUserRequest(

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @UniqueEmail(message = "3.email.alreadyExists")
    String email
) {}
```

### Class-level (Cross-field) Validator

For rules that compare multiple fields (e.g. `endDate` after `startDate`):

```java
// File: validator/EndAfterStart.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EndAfterStartValidator.class)
public @interface EndAfterStart {
    String message() default "2.endDate.mustBeAfter";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// File: validator/EndAfterStartValidator.java
public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, CreateEventRequest> {

    @Override
    public boolean isValid(CreateEventRequest req, ConstraintValidatorContext context) {
        if (req.startDate() == null || req.endDate() == null) {
            return true;
        }
        return req.endDate().isAfter(req.startDate());
    }
}

// Usage
@EndAfterStart
public record CreateEventRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {}
```

## Validation Message Format

### Priority-Based Messages

Messages follow the format: `priority.fieldName.errorType`. The priority prefix lets the response builder surface the most important message first.

```java
public record CreateUserRequest(

    @NotBlank(message = "1.email.required")          // Priority 1: Required
    @Email(message = "2.email.invalid")              // Priority 2: Format
    @UniqueEmail(message = "3.email.alreadyExists")  // Priority 3: Business rule
    String email,

    @NotBlank(message = "1.password.required")
    @Size(min = 8, max = 100, message = "2.password.invalidLength")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "3.password.weak")
    String password
) {}
```

### Message Files (Spring `MessageSource`)

```properties
# messages_en.properties
validation.email.required=Email is required
validation.email.invalid=Email format is invalid
validation.email.alreadyExists=Email already exists
validation.password.required=Password is required
validation.password.tooShort=Password must be at least 8 characters
validation.password.weak=Password must contain uppercase, lowercase, and number
```

You can also have Hibernate Validator resolve `{key}` messages directly from `ValidationMessages.properties`.

## OpenAPI / springdoc Documentation

### Basic Documentation

Use `@Schema` from springdoc to document fields. `requiredMode` marks required vs optional.

```java
public record CreateUserRequest(

    @NotBlank @Email(message = "1.email.invalid")
    @Schema(description = "User email address", example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    String email,

    @Size(max = 500)
    @Schema(description = "User biography", example = "Software developer passionate about clean code",
            maxLength = 500, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String bio
) {}
```

### Advanced Documentation

```java
public record CreateProductRequest(

    @NotBlank @Size(min = 3, max = 100)
    @Schema(description = "Product name", example = "Wireless Mouse", minLength = 3, maxLength = 100)
    String name,

    @NotNull @DecimalMin("0.01") @DecimalMax("1000000")
    @Schema(description = "Product price in USD", example = "29.99", type = "number", format = "double")
    BigDecimal price,

    @NotNull
    @Schema(description = "Product category", implementation = ProductCategory.class,
            example = "ELECTRONICS")
    ProductCategory category,

    @Schema(description = "Product tags", example = "[\"wireless\", \"ergonomic\", \"office\"]")
    List<@NotBlank String> tags
) {}
```

## Query / Filter DTOs

### Pagination

Spring Data exposes `Pageable` and `Sort` directly. Prefer accepting a `Pageable` in the controller and a dedicated filter record for the rest:

```java
public record PaginationRequest(

    @Min(value = 0, message = "1.page.minimum")
    @Schema(description = "Page number (0-based)", example = "0", defaultValue = "0")
    Integer page,

    @Min(value = 1, message = "1.size.minimum")
    @Max(value = 100, message = "2.size.maximum")
    @Schema(description = "Items per page", example = "20", defaultValue = "20")
    Integer size
) {
    public PaginationRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}
```

### Sorting and Filtering

```java
public record UserFilter(

    @Schema(description = "Search keyword", example = "john")
    String search,

    @Schema(description = "Filter by role")
    UserRole role,

    @Pattern(regexp = "createdAt|updatedAt|name", message = "1.sortBy.invalid")
    @Schema(description = "Sort field", defaultValue = "createdAt")
    String sortBy,

    @Pattern(regexp = "ASC|DESC", message = "1.order.invalid")
    @Schema(description = "Sort order", defaultValue = "DESC")
    String order
) {}
```

In the controller, bind filters with `@ParameterObject` (springdoc) so each field becomes a query parameter:

```java
@GetMapping
public Page<UserResponse> findAll(@ParameterObject @Valid UserFilter filter,
                                  @ParameterObject Pageable pageable) { ... }
```

## Partial Update DTOs

Java has no `PartialType`. For PATCH semantics, either make all fields optional, or wrap them in `JsonNullable` (openapi-jackson-nullable) to distinguish "absent" from "explicit null".

### All Fields Optional

```java
public record UpdateUserProfileRequest(

    @Size(min = 2, message = "1.fullName.tooShort")
    @Schema(example = "John Doe")
    String fullName,

    @Size(max = 500, message = "1.bio.tooLong")
    @Schema(example = "Developer")
    String bio
    // email/password typically not part of a profile update
) {}
```

### Distinguish "not provided" from "set to null"

```java
public record PatchUserRequest(
    JsonNullable<String> fullName,
    JsonNullable<String> bio
) {}
// In the service: if (req.fullName().isPresent()) user.setFullName(req.fullName().get());
```

## Transformation

Jackson handles most type conversion during deserialization. Use Jackson annotations / `@InitBinder` instead of class-transformer.

```java
public record QueryRequest(

    // String -> number happens automatically when the field type is Integer
    Integer page,

    Boolean isActive,

    // Trim & normalize via a compact constructor
    String name,

    @Email String email,

    List<String> tags
) {
    public QueryRequest {
        name  = name  != null ? name.trim() : null;
        email = email != null ? email.toLowerCase().trim() : null;
    }
}
```

For request bodies, you can also register a Jackson `Module` (e.g. a `StringTrimDeserializer`) globally instead of per-DTO compact constructors.

## Conditional / Group Validation

Use validation **groups** to require fields only in certain scenarios, and class-level validators for "validate B only if A equals X".

```java
public enum PaymentMethod { CREDIT_CARD, BANK_TRANSFER, CASH }

@ValidPaymentDetails   // class-level constraint
public record CreateOrderRequest(

    @NotNull(message = "1.paymentMethod.invalid")
    PaymentMethod paymentMethod,

    String cardNumber,   // required only when CREDIT_CARD
    String cvv,          // required only when CREDIT_CARD
    String bankAccount   // required only when BANK_TRANSFER
) {}

// Validator enforces the conditional rules
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

## Best Practices

### 1. Always Use Validation Messages

```java
// Good
@Email(message = "1.email.invalid") String email

// Bad
@Email String email  // ❌ no custom message key
```

### 2. Use the Most Specific Constraint

```java
// Good
@NotNull UUID userId

// Bad
@NotBlank String userId  // ❌ loses type safety; bind to UUID instead
```

### 3. Order Constraints by Priority

```java
// Good - most important first (reflected in message priority prefix)
@NotBlank(message = "1.email.required")
@Email(message = "2.email.invalid")
@UniqueEmail(message = "3.email.alreadyExists")
String email
```

### 4. Document with @Schema

```java
// Good
@Schema(description = "User email address", example = "user@example.com") String email

// Bad
String email  // ❌ no documentation
```

### 5. Provide Defaults Safely

Use a record compact constructor (shown above) to default optional values — do not rely on field initializers, which records don't have.

### 6. Keep DTOs Focused

```java
// Good - separate DTOs per use case
public record CreateUserRequest(...) {}
public record UpdateUserRequest(...) {}
public record ChangePasswordRequest(...) {}

// Bad - one DTO for everything
public record UserRequest(...) {}  // ❌
```

## Common Patterns

### File Upload DTO

File uploads use `MultipartFile`. Bind metadata as a separate part or as request params; you cannot put `MultipartFile` inside a JSON `@RequestBody`.

```java
public record UploadFileMetadata(

    @NotBlank(message = "1.title.required")
    @Size(max = 200, message = "2.title.tooLong")
    @Schema(description = "File title", example = "Profile Picture")
    String title,

    @Size(max = 1000, message = "1.description.tooLong")
    @Schema(description = "File description")
    String description
) {}

// Controller
@PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<FileResponse> upload(
        @Valid @RequestPart("metadata") UploadFileMetadata metadata,
        @RequestPart("file") MultipartFile file) { ... }
```

### Bulk Operation DTO

```java
public record BulkDeleteRequest(

    @NotEmpty(message = "1.ids.empty")
    @Size(max = 100, message = "2.ids.tooMany")
    @Schema(description = "IDs to delete", example = "[\"uuid1\", \"uuid2\"]")
    List<@NotNull(message = "3.ids.invalidFormat") UUID> ids
) {}
```

## Testing DTOs

Use the Jakarta `Validator` directly in a unit test — no Spring context needed.

```java
class CreateUserRequestTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void passesValidationWithValidData() {
        var dto = new CreateUserRequest("user@example.com", "SecurePass123!", "John Doe");

        var violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void failsValidationWithInvalidEmail() {
        var dto = new CreateUserRequest("invalid-email", "SecurePass123!", "John Doe");

        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }
}
```

## Quick Reference

| Constraint | Purpose | Example |
|-----------|---------|---------|
| `@NotNull` | Value must not be null | `@NotNull Integer age` |
| `@NotBlank` | String not null/empty/whitespace | `@NotBlank String name` |
| `@NotEmpty` | Collection/String not empty | `@NotEmpty List<UUID> ids` |
| `@Email` | Valid email | `@Email String email` |
| `@Size` | String/collection length range | `@Size(min = 8, max = 100)` |
| `@Min` / `@Max` | Integer bounds | `@Min(1) @Max(100) Integer qty` |
| `@DecimalMin` / `@DecimalMax` | Decimal bounds | `@DecimalMin("0.01") BigDecimal price` |
| `@Pattern` | Regex | `@Pattern(regexp = "^[A-Z].*")` |
| `@Positive` / `@Negative` | Sign | `@Positive BigDecimal amount` |
| `@Past` / `@Future` | Date constraints | `@Future LocalDate date` |
| `@Valid` | Cascade into nested object | `List<@Valid ItemRequest> items` |
| `@Constraint` | Declare a custom constraint | `@Constraint(validatedBy = ...)` |
| `ConstraintValidator` | Implement custom logic | `implements ConstraintValidator<A, T>` |
