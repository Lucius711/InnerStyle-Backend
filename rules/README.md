# iGoGo Backend - Coding Rules & Standards

## Overview

This directory contains comprehensive coding rules and best practices for the iGoGo backend project. These rules ensure code consistency, maintainability, and quality across the entire codebase.

## Purpose

- **Consistency:** Maintain uniform code style and patterns
- **Quality:** Ensure clean, maintainable, and testable code
- **Onboarding:** Help new developers understand project conventions
- **Reference:** Quick lookup for coding patterns and best practices
- **Standards:** Establish team coding standards

## Rules Index

### 00. Clean Code Principles

**File:** [00-clean-code-principles.md](00-clean-code-principles.md)

Core principles for writing clean, maintainable code: SRP, DRY, KISS, readability, documentation, and performance considerations.

**When to read:** Before starting any feature development

### 01. Naming Conventions

**File:** [01-naming-conventions.md](01-naming-conventions.md)

Naming standards: Java files (PascalCase), classes (PascalCase), packages (lowercase), variables/methods (camelCase), constants (UPPER_SNAKE_CASE), database columns (snake_case), message keys (dot.notation).

**When to read:** When creating any new file, class, or variable

### 02. DTO Request

**File:** [02-dto-request.md](02-dto-request.md)

Request DTO patterns and validation: record-based DTOs, Bean Validation annotations, custom validators, springdoc/OpenAPI documentation, query/filter DTOs, and transformation patterns.

**When to read:** When creating API endpoints that accept input

### 03. DTO Response

**File:** [03-dto-response.md](03-dto-response.md)

Response DTO patterns and data transformation: response structure, hiding sensitive data, pagination, nested DTOs, computed fields, and entity → DTO mapping (MapStruct).

**When to read:** When returning data from API endpoints

### 04. Controller

**File:** [04-controller.md](04-controller.md)

Controller best practices: `@RestController`, request mapping annotations, path/query parameters, request validation, response status codes, OpenAPI docs, and method-level security.

**When to read:** When creating API endpoints

### 05. Service

**File:** [05-service.md](05-service.md)

Service layer patterns: business logic organization, transactions (`@Transactional`), error handling, logging, and constructor-based dependency injection.

**When to read:** When implementing business logic

### 06. Validation

**File:** [06-validation.md](06-validation.md)

Validation rules and custom validators: built-in Bean Validation constraints, custom `ConstraintValidator`s, message format, priority-based messages, conditional/group validation, and binding.

**When to read:** When adding input validation

### 07. Module / Application Structure

**File:** [07-module.md](07-module.md)

Spring application structure: package organization by layer/feature, `@Configuration` classes, beans, component scanning, profiles, and configuration best practices.

**When to read:** When creating new features or organizing code

### 08. Database Migration

**File:** [08-database-migration.md](08-database-migration.md)

Flyway migration patterns: migration file structure, creating tables, adding/removing columns, indexes and constraints, data migrations, and Flyway commands.

**When to read:** When making database schema changes

### 09. Error Handling

**File:** [09-error-handling.md](09-error-handling.md)

Error handling and exception patterns: custom exceptions, `@RestControllerAdvice`, database error handling, error logging, and message keys.

**When to read:** When implementing error handling

### 10. Testing

**File:** [10-testing.md](10-testing.md)

Testing strategies and patterns: unit tests (JUnit 5 + Mockito), web/slice tests (MockMvc), integration tests (`@SpringBootTest`, Testcontainers), coverage, and CI integration.

**When to read:** When writing tests for your code

### 11. Multi-Role System

**File:** [11-multi-role-system.md](11-multi-role-system.md)

Multi-role authentication system: role-based access control with Spring Security, multiple accounts per user, role switching, permission management, and authentication flows.

**When to read:** When implementing authentication or authorization

### 12. No Hardcoding & No Raw Object

**File:** [12-no-hardcoding-no-any.md](12-no-hardcoding-no-any.md)

Code quality standards: avoid hardcoded values, use `@ConfigurationProperties` and constants, avoid raw `Object`/`Map<String,Object>`, and embrace strong typing & generics.

**When to read:** When writing any code (always applicable)

### 13. Repository Pattern

**File:** [13-repository-pattern.md](13-repository-pattern.md)

Repository pattern with Spring Data JPA: data access layer separation, type-safe queries (derived queries, JPQL, Specifications), transaction management, projections, and best practices.

**When to read:** When implementing database operations or creating repositories

### 14. Database Schema

**File:** [14-database-schema.md](14-database-schema.md)

Schema design conventions: tables, columns, keys, indexes, constraints, and entity ↔ table mapping.

**When to read:** When designing or changing the data model

### 15. API Prefix Pattern

**File:** [15-api-prefix-pattern.md](15-api-prefix-pattern.md)

API path/versioning conventions and the global context path / prefix configuration.

**When to read:** When adding new controllers or versioning the API

### 16. File / URL Storage

**File:** [16-file-url-storage.md](16-file-url-storage.md)

How to store file references and serve URLs (object storage, signed URLs, path handling).

**When to read:** When handling uploads or file references

### 17. Static Analysis (Checkstyle / Spotless)

**File:** [17-eslint-check-required.md](17-eslint-check-required.md)

Mandatory static analysis & formatting gate before commit (Checkstyle, Spotless / google-java-format, optional SpotBugs/PMD).

**When to read:** Before committing any code

### Master Data Standardization

**File:** [master-data-standardization.md](master-data-standardization.md)

Standardization rules for master/reference data.

**When to read:** When introducing or modifying reference data

## Quick Start Guide

### For New Developers

1. Start with [00-clean-code-principles.md](00-clean-code-principles.md)
2. Read [01-naming-conventions.md](01-naming-conventions.md)
3. Review the specific rule files as you work on features

### For Feature Development

1. **Creating a new feature:** Module/Structure, Controller, Service, Repository Pattern, DTO Request/Response
2. **Adding validation:** Validation, DTO Request
3. **Database changes:** Database Migration, Database Schema, Repository Pattern
4. **Database operations:** Repository Pattern, Service
5. **Error handling:** Error Handling
6. **Writing tests:** Testing

## Common Patterns Quick Reference

### File Naming (Java)

```
UserController.java         # Controller
UserService.java            # Service interface
UserServiceImpl.java        # Service implementation
UserRepository.java         # Repository
CreateUserRequest.java      # Request DTO (record)
UserResponse.java           # Response DTO (record)
UserServiceTest.java        # Unit test
```

### Class Naming

```java
public class UserController {}
public interface UserService {}
public class UserServiceImpl implements UserService {}
public interface UserRepository extends JpaRepository<User, UUID> {}
public record CreateUserRequest(...) {}
public record UserResponse(...) {}
```

### Variable Naming

```java
String userName = "John";          // camelCase
static final int MAX_ATTEMPTS = 5; // UPPER_SNAKE_CASE
boolean isActive = true;           // boolean with is/has prefix
```

### Database Naming

```sql
-- snake_case for tables and columns
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255),
  full_name VARCHAR(255),
  created_at TIMESTAMPTZ
);
```

## Technology Stack

### Core

- **Framework:** Spring Boot 3.x
- **Language:** Java 21 (LTS)
- **Build Tool:** Maven

### Database

- **Database:** PostgreSQL
- **ORM:** Spring Data JPA / Hibernate
- **Driver:** PostgreSQL JDBC
- **Migrations:** Flyway

### Validation

- **Jakarta Bean Validation** (Hibernate Validator)

### Documentation

- **springdoc-openapi** (Swagger UI)

### Mapping

- **MapStruct**
- **Lombok**

### Testing

- **JUnit 5**, **Mockito**, **AssertJ**
- **Spring Boot Test** (MockMvc, `@SpringBootTest`)
- **Testcontainers** (PostgreSQL)

### Logging

- **SLF4J + Logback** (Spring Boot default)

### i18n

- **Spring `MessageSource`** (`messages*.properties`)

## Project Structure

```
src/
├── main/
│   ├── java/com/igogo/
│   │   ├── common/                 # Shared utilities
│   │   │   ├── constant/           # Constants and enums
│   │   │   ├── annotation/         # Custom annotations
│   │   │   ├── response/           # ApiResponse wrappers
│   │   │   └── util/
│   │   ├── config/                 # @Configuration classes (security, openapi, jpa)
│   │   ├── exception/              # Custom exceptions + GlobalExceptionHandler
│   │   ├── <feature>/              # Feature package
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   │   └── impl/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   ├── mapper/
│   │   │   └── dto/
│   │   │       ├── request/
│   │   │       └── response/
│   │   └── IgogoApplication.java   # Application entry (@SpringBootApplication)
│   └── resources/
│       ├── application.yml
│       ├── db/migration/           # Flyway migrations (V*.sql)
│       ├── messages_en.properties  # i18n
│       └── messages_vi.properties
└── test/
    └── java/com/igogo/             # Mirror of main packages
```

## Best Practices Summary

### 1. Always Use Strong Types

```java
// Good
UserResponse findById(UUID id);

// Bad
Object findById(Object id);  // ❌
```

### 2. Use Constructor Injection

```java
// Good
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
}

// Bad
UserService userService = new UserServiceImpl();  // ❌
```

### 3. Validate All Input

```java
// Good
public record CreateUserRequest(
    @NotBlank @Email(message = "validation.email.invalid") String email
) {}

// Bad - No validation
public record CreateUserRequest(String email) {}  // ❌
```

### 4. Handle Errors Properly

```java
// Good
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

// Bad
User user = userRepository.findById(id).orElse(null);  // ❌
```

### 5. Log Important Operations

```java
// Good (Slf4j logger)
log.info("User created: {}", user.getId());
log.error("Failed to create user: {}", ex.getMessage(), ex);

// Bad
System.out.println("User created");  // ❌
```

### 6. Write Tests

```text
Always write tests for:
✓ Business logic
✓ Validation
✓ Error handling
✓ Edge cases
```

## Validation Message Format

All validation messages follow the format: `priority.fieldName.errorType`

```java
public record CreateUserRequest(
    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @UniqueEmail(message = "3.email.exists")
    String email
) {}
```

## Response Format

### Success Response

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { }
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "email": "Email format is invalid"
  },
  "errors": {
    "email": ["Email format is invalid", "Email already exists"]
  },
  "message": "Bad Request"
}
```

## Environment Variables

```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=2207
DATABASE_URL=jdbc:postgresql://host:5432/db
DATABASE_USERNAME=user
DATABASE_PASSWORD=pass
CORS_ORIGIN=http://localhost:3000
```

## CLI Commands

### Development

```bash
./mvnw spring-boot:run                              # Start the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # With dev profile
```

### Building

```bash
./mvnw clean package          # Build a runnable jar
./mvnw clean install          # Build + install to local repo
```

### Testing

```bash
./mvnw test                   # Unit tests
./mvnw verify                 # Unit + integration tests (failsafe)
./mvnw jacoco:report          # Coverage report
```

### Migrations (Flyway)

```bash
./mvnw flyway:migrate         # Apply pending migrations
./mvnw flyway:info            # Migration status
./mvnw flyway:repair          # Repair the schema history table
```

### Code Quality

```bash
./mvnw spotless:apply         # Auto-format (google-java-format)
./mvnw checkstyle:check       # Lint
```

## Contributing

When contributing to this project:

1. **Read relevant rules** before starting work
2. **Follow naming conventions** consistently
3. **Write tests** for your code
4. **Document your code** appropriately
5. **Use i18n** (`MessageSource`) for all user-facing messages
6. **Handle errors** properly
7. **Run Spotless/Checkstyle** before committing

## Support

If you have questions about these rules or need clarification:

1. Check the specific rule file for detailed examples
2. Look at existing code in the project for reference
3. Ask team members for guidance
4. Update rules if you find improvements

## Updates

These rules are living documents. When you discover better patterns or practices:

1. Discuss with the team
2. Update the relevant rule file
3. Update this README if needed
4. Communicate changes to the team

## Version

**Last Updated:** 2026-06-17
**Project Version:** 0.0.1
**Spring Boot Version:** 3.x
**Java Version:** 21
