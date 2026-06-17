# Application Structure & Configuration Rules - iGoGo Backend

## Overview

Spring Boot has no NestJS-style "modules". Instead, the application is organized into **packages** (by feature/layer), wired automatically by **component scanning**, and configured through **`@Configuration` classes** that declare `@Bean`s. All beans live in one `ApplicationContext` and are injectable anywhere — there is no per-module `imports`/`exports`. This file explains how the NestJS module concepts map to Spring Boot.

## Concept Mapping (NestJS → Spring Boot)

| NestJS | Spring Boot equivalent |
|--------|------------------------|
| `@Module()` | A package + (optionally) a `@Configuration` class |
| `controllers` | `@RestController` classes (found by component scan) |
| `providers` | `@Service` / `@Component` / `@Repository` / `@Bean` |
| `imports` | Nothing needed — beans are context-wide |
| `exports` | Nothing needed — control visibility via package-private classes |
| `@Global()` | Default behavior (all beans are global in the context) |
| Value provider | `@Bean` returning a value / `@ConfigurationProperties` / `@Value` |
| Factory provider | A `@Bean` method |
| `forRoot()` dynamic module | `@Configuration` + `@ConfigurationProperties` (or auto-configuration) |
| `forwardRef()` | Restructure, or `@Lazy` to break a constructor cycle |

## File Structure

Organize **by feature**, then by layer inside each feature:

```
src/main/java/com/igogo/
├── IgogoApplication.java          # @SpringBootApplication (entry point)
├── config/                        # cross-cutting @Configuration classes
│   ├── SecurityConfig.java
│   ├── OpenApiConfig.java
│   ├── JpaConfig.java
│   └── WebConfig.java
├── common/                        # shared utilities, ApiResponse, base classes
├── user/                          # feature package
│   ├── controller/UserController.java
│   ├── service/UserService.java
│   ├── service/impl/UserServiceImpl.java
│   ├── repository/UserRepository.java
│   ├── entity/User.java
│   ├── mapper/UserMapper.java
│   └── dto/{request,response}/
├── auth/
└── order/
```

## Component Scanning (replaces imports/providers/controllers)

`@SpringBootApplication` enables component scanning of the package it lives in and all sub-packages. Any `@RestController`, `@Service`, `@Repository`, `@Component`, or `@Configuration` under `com.igogo` is auto-registered.

```java
package com.igogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication   // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class IgogoApplication {
    public static void main(String[] args) {
        SpringApplication.run(IgogoApplication.class, args);
    }
}
```

> Keep the main class at the **root package** so scanning covers every feature package automatically.

## Bean Definitions

### Stereotype Annotations (the common case)

```java
@RestController          // a controller bean
public class UserController { ... }

@Service                 // a service bean
public class UserServiceImpl implements UserService { ... }

@Repository              // a repository bean (Spring Data creates the impl automatically)
public interface UserRepository extends JpaRepository<User, UUID> { ... }

@Component               // any other bean
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> { ... }
```

### @Configuration + @Bean (replaces value/factory providers)

Use a `@Configuration` class when a bean isn't a simple stereotype (third-party objects, factory logic, conditional creation):

```java
@Configuration
public class AppBeansConfig {

    // "value provider" equivalent
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    // "factory provider" equivalent
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // factory with dependencies — params are injected from the context
    @Bean
    public NotificationService notificationService(EmailClient emailClient, AppProperties props) {
        return new NotificationServiceImpl(emailClient, props.getNotification());
    }
}
```

## Configuration Properties (replaces value providers / env config)

Bind external config (`application.yml`, env vars) into type-safe records/classes.

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String accessTokenKey,
    String refreshTokenKey,
    Duration accessTokenExpiry,
    Duration refreshTokenExpiry
) {}
```

```yaml
# application.yml
app:
  jwt:
    access-token-key: ${JWT_SECRET_ACCESS_TOKEN_KEY}
    refresh-token-key: ${JWT_SECRET_REFRESH_TOKEN_KEY}
    access-token-expiry: 15m
    refresh-token-expiry: 7d
```

Enable binding once (or annotate the class with `@ConfigurationPropertiesScan` on the app):

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class IgogoApplication { ... }
```

Inject like any other bean:

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JwtProperties jwtProperties;   // type-safe config
}
```

## Bean Visibility (replaces exports)

Every bean is visible across the context, so there is no `exports`. To keep something "private" to a feature, make the class **package-private** and only expose an interface:

```java
package com.igogo.user.service.impl;

// package-private: not referenceable outside this package
class UserValidationService { ... }
```

Inject by the public interface (`UserService`) rather than the implementation so features depend on abstractions.

## Profiles (environment-specific beans/config)

Use Spring profiles instead of dynamic `forRoot(envPath)`.

```java
@Configuration
@Profile("dev")
public class DevDataConfig {
    @Bean
    CommandLineRunner seedData(UserRepository repo) { return args -> { /* seed */ }; }
}
```

```yaml
# application.yml (common)
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
---
# application-dev.yml, application-prod.yml hold env-specific overrides
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Conditional Beans (replaces optional providers)

```java
@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true")
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }
}
```

## Common Configuration Classes

### Database / JPA Configuration

Spring Boot auto-configures the `DataSource`, `EntityManagerFactory`, and `JpaTransactionManager` from `application.yml`. Add a config class only for extras like auditing or repository scanning.

```java
@Configuration
@EnableJpaAuditing                          // enables @CreatedDate / @LastModifiedDate
@EnableJpaRepositories(basePackages = "com.igogo")   // usually implicit
public class JpaConfig {}
```

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate          # schema managed by Flyway, not Hibernate
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity                       // enables @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### OpenAPI / springdoc Configuration

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI igogoOpenApi() {
        return new OpenAPI()
            .info(new Info().title("iGoGo API").version("v1"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
```

### Web / MVC Configuration

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*");
    }
}
```

## Avoiding Circular Dependencies

A constructor-injection cycle (A needs B, B needs A) fails at startup. Fix by:

1. **Restructuring** — extract the shared logic into a third bean both depend on (preferred).
2. **Events** — publish an `ApplicationEvent` instead of calling back.
3. **`@Lazy`** — as a last resort, inject one side lazily:

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final @Lazy OrderService orderService;   // breaks the cycle
}
```

## Best Practices

### 1. Organize by Feature

```
com.igogo.user.{controller,service,repository,entity,dto,mapper}
```
Keep each feature self-contained. Don't put `OrderController` inside the `user` package.

### 2. Depend on Interfaces

Inject `UserService`, not `UserServiceImpl`. Keep implementations package-private where possible.

### 3. One Concern per @Configuration Class

`SecurityConfig`, `JpaConfig`, `OpenApiConfig`, `WebConfig` — don't dump everything into one giant config.

### 4. Prefer @ConfigurationProperties over scattered @Value

Group related settings into a typed properties record; avoid `@Value("${...}")` sprinkled across services.

### 5. Let Auto-configuration Do the Work

Don't define `DataSource`/`EntityManagerFactory`/`ObjectMapper` beans unless you must override defaults.

### 6. Use Profiles, Not Hand-rolled Env Switches

`@Profile("prod")` + `application-prod.yml` instead of reading an env path manually.

## Common Patterns

### Auth Configuration (JWT)

```java
@Configuration
@RequiredArgsConstructor
public class AuthBeansConfig {

    private final JwtProperties jwtProperties;

    @Bean
    public JwtEncoder jwtEncoder() {
        // build from jwtProperties.accessTokenKey()
        return new NimbusJwtEncoder(/* key source */);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

### Common / Shared Beans

```java
@Configuration
public class CommonConfig {

    @Bean
    public MessageSource messageSource() {
        var source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        var bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);   // i18n validation messages
        return bean;
    }
}
```

## Testing Slices & Context

Use Spring Boot test slices instead of constructing a NestJS `TestingModule`:

```java
// Web layer only — controller + MockMvc, services mocked
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserService userService;   // provide the collaborator

    @Test
    void contextLoads() { assertThat(mockMvc).isNotNull(); }
}

// Full context
@SpringBootTest
class ApplicationContextTest {
    @Autowired UserController controller;
    @Autowired UserService service;

    @Test
    void beansAreWired() {
        assertThat(controller).isNotNull();
        assertThat(service).isNotNull();
    }
}

// Provide test-only beans
@TestConfiguration
class TestBeans {
    @Bean Clock fixedClock() { return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC); }
}
```

## Quick Reference

| Pattern | Usage | Example |
|---------|-------|---------|
| `@SpringBootApplication` | Entry point + scan | root `IgogoApplication` |
| `@Configuration` | Bean definitions | `SecurityConfig`, `JpaConfig` |
| `@Bean` | Define a bean | `@Bean PasswordEncoder ...` |
| `@Service` / `@Component` / `@Repository` | Stereotype beans | auto-scanned |
| `@ConfigurationProperties` | Type-safe config | `@ConfigurationProperties("app.jwt")` |
| `@Profile` | Env-specific beans | `@Profile("prod")` |
| `@ConditionalOnProperty` | Conditional bean | feature toggles |
| `@EnableMethodSecurity` | Enable `@PreAuthorize` | in `SecurityConfig` |
| `@EnableJpaAuditing` | Auditing timestamps | in `JpaConfig` |
| `@Lazy` | Break a DI cycle | last resort |
| `@WebMvcTest` / `@SpringBootTest` | Test slices | controller / full context |
