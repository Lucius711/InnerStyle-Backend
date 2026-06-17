# Rule 17: Static Analysis Must Pass — No Errors, No Warnings

> (This rule was "ESLint must pass" on the TypeScript stack. On Spring Boot the equivalent gate is **Spotless + Checkstyle + the Java compiler's `-Xlint`/`-Werror`**, optionally SpotBugs/PMD.)

## Overview

**MANDATORY**: Every code change must leave the build with **0 formatting violations, 0 Checkstyle violations, and 0 compiler warnings**. This is non-negotiable. The codebase was brought to a clean state and must stay that way.

## Why

- Warnings hide real bugs. Compiler lint surfaces live problems: unchecked/raw-type usage that masks `ClassCastException`s, ignored return values, deprecated API calls, and unsafe casts.
- Once a single warning is allowed back in, the count creeps upward and the signal is lost — a new warning becomes invisible in a sea of pre-existing ones.
- `@SuppressWarnings`, disabling Checkstyle rules, and unchecked casts are loopholes that defer the problem instead of fixing it. The root cause is almost always a type that can be tightened (use generics, real types, or sealed types).

## The Workflow (mandatory after every edit batch)

After writing or modifying ANY `.java` file, before declaring work done:

```bash
# 1. Auto-format the code (fails CI if not applied)
./mvnw spotless:apply

# 2. Compile + run Checkstyle + lint (one of your edits may affect callers)
./mvnw clean compile checkstyle:check
```

Both must produce **0 errors / 0 warnings**. If either reports anything, fix it before moving on — do not create a "fix lint later" task.

For larger changes (5+ files, or refactored shared types), also run the full quality gate:

```bash
./mvnw clean verify        # compile (-Werror), checkstyle, spotbugs, tests
```

## Hard Rules

1. **NO `@SuppressWarnings`** as an escape hatch (especially `"unchecked"`, `"rawtypes"`). If a warning fires, the type or shape is wrong — fix that. The rare genuine boundary case requires a one-line comment explaining the upstream cause.
2. **NO disabling Checkstyle/SpotBugs rules** to silence a class of warning. Same reason. Don't add `@SuppressFBWarnings` or `<suppress>` to dodge findings.
3. **NO unchecked / raw-type casts** as a shortcut. Use generics, a real type, a `record`, or a sealed hierarchy.
4. **NO raw `Object` / `Map<String,Object>`** on method params or return types (see Rule 12). Model the type.
5. **NO leaving a warning "for later".** Fix it in the same edit. The warning count must monotonically decrease.

## Common Patterns (use these instead of loose types)

### Typed query results (no raw types)

```java
// Bad - raw query, untyped row
@Query(value = "SELECT id, name FROM users", nativeQuery = true)
List getUsers();                       // raw List -> unchecked warnings

// Good - typed projection / entity (see 13-repository-pattern.md)
interface UserRow { UUID getId(); String getName(); }
@Query(value = "SELECT id, name FROM users", nativeQuery = true)
List<UserRow> getUsers();
```

### Generics instead of raw collections

```java
// Bad - raw type triggers unchecked warning
List items = repository.findAll();

// Good
List<User> items = repository.findAll();
```

### Avoid unsafe casts; model with sealed types / pattern matching

```java
// Bad
Object payload = decode(token);
String email = ((Map) payload).get("email").toString();   // unchecked

// Good - a typed record + JWT claim accessors
TokenPayload payload = decode(token);   // record TokenPayload(UUID userId, String email)
String email = payload.email();
```

### Enum membership check (no unchecked cast)

```java
// Bad
if (Arrays.asList(values).contains((Object) input)) { ... }

// Good
boolean allowed = EnumSet.allOf(OrderStatus.class).stream()
    .anyMatch(s -> s.name().equals(input));
```

### Don't ignore returned values (SpotBugs RV_RETURN_VALUE_IGNORED)

```java
// Bad - return value ignored
userRepository.save(user);   // fine to ignore here, but...
list.stream().filter(...);   // ❌ result discarded, does nothing

// Good - use the result
List<User> active = list.stream().filter(User::isActive).toList();
```

### Reading the authenticated principal (no Object)

```java
// Bad
Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

// Good - inject the typed principal
@GetMapping("/profile")
public ApiResponse<UserResponse> profile(@AuthenticationPrincipal UserPrincipal principal) { ... }
```

### JSON parsing (no raw Map)

```java
// Bad
Map args = objectMapper.readValue(json, Map.class);   // raw type

// Good - a record, or a typed map
ToolArguments args = objectMapper.readValue(json, ToolArguments.class);
// or, if truly dynamic:
Map<String, Object> args = objectMapper.readValue(json, new TypeReference<>() {});
```

### Deprecated API usage

Replace deprecated calls rather than suppressing the warning (e.g. `WebSecurityConfigurerAdapter` → `SecurityFilterChain` bean, `new Date()` → `java.time`).

## Build Configuration

### Compiler — treat lint as errors

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <release>21</release>
    <compilerArgs>
      <arg>-Xlint:all,-processing</arg>
      <arg>-Werror</arg>            <!-- fail build on any warning -->
    </compilerArgs>
  </configuration>
</plugin>
```

### Spotless — formatting gate

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <configuration>
    <java>
      <googleJavaFormat/>
      <removeUnusedImports/>
      <importOrder/>
    </java>
  </configuration>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>   <!-- bound to verify -->
  </executions>
</plugin>
```

### Checkstyle — style gate

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <configuration>
    <configLocation>config/checkstyle/checkstyle.xml</configLocation>
    <failOnViolation>true</failOnViolation>
    <violationSeverity>warning</violationSeverity>   <!-- warnings fail the build -->
  </configuration>
  <executions>
    <execution><phase>verify</phase><goals><goal>check</goal></goals></execution>
  </executions>
</plugin>
```

(SpotBugs / PMD are optional but recommended; bind them to `verify` the same way.)

## When Pre-existing Code Has Warnings

If you touch a file that already has violations unrelated to your change:

1. **Fix them in your edit** (preferred — the file is in your context anyway), or
2. **Leave a `// TODO` and a separate commit** — only if the fix is genuinely out of scope. The violation count for that file must not increase.

You may NEVER add a new warning. The count must monotonically decrease.

## Enforcement Check

Before marking any task complete, self-verify:

```bash
./mvnw -q clean verify
# Build must be SUCCESS with 0 Spotless/Checkstyle/SpotBugs violations and 0 compiler warnings
```

If the build is not green, the task is not done. Period.

## Exceptions

The only acceptable exceptions:

- Generated sources (e.g. MapStruct/QueryDSL output, Flyway scripts) excluded in the plugin config. Don't add new exclusions to dodge violations.
- Third-party APIs that force an unchecked cast at a genuine boundary. In that case add a comment explaining the upstream cause and use the narrowest `@SuppressWarnings("unchecked")` on the smallest possible scope (a single statement extracted into a private method) — never blanket-suppress a class.

## Related

- Rule 12: No Hardcoding, No raw `Object` — this rule adds the enforcement workflow.
- Run `./mvnw spotless:apply` before every commit; CI runs `./mvnw verify`.
