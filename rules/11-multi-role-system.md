# Multi-Role System Rules - iGoGo Backend

## Overview

The system implements a multi-role architecture where each role has dedicated API paths and controllers while sharing common services. Authorization is enforced by **Spring Security** — a JWT filter establishes the authenticated principal with its authorities, the `SecurityFilterChain` applies coarse path-based rules, and `@PreAuthorize` enforces fine-grained method security.

## Role Structure

### Role Hierarchy

```
mtb_roles (table)
├── id 1 - USER    (End user)
├── id 2 - CTV     (Collaborator/Partner)
├── id 3 - ADMIN   (Administrator)
└── id 4+ - Custom roles

mtb_sub_roles (table)
└── Additional subdivisions under main roles
```

Model roles as an enum and map them to Spring Security authorities (`ROLE_USER`, `ROLE_CTV`, `ROLE_ADMIN`):

```java
public enum UserRole {
    USER, CTV, ADMIN;
    public String authority() { return "ROLE_" + name(); }
}
```

## API Path Convention

### Role-Based Path Structure

```
/{role}/{resource}/{action}

Examples:
- /user/profile      # User endpoints
- /ctv/dashboard     # CTV endpoints
- /admin/users       # Admin endpoints
- /common/locations  # Shared endpoints
```

### Path Rules

1. **Each role gets a dedicated prefix:** `/user/**`, `/ctv/**`, `/admin/**`, `/common/**`.
2. **Auth endpoints per role:** `/user/auth/login`, `/ctv/auth/login`, `/admin/auth/login`, etc.

The global context path / API prefix is configured centrally (see 15-api-prefix-pattern.md).

## Controller Structure

### Naming Convention

```java
UserProfileController     // /user/profile
CtvDashboardController    // /ctv/dashboard
AdminUsersController      // /admin/users
CommonLocationController  // /common/locations  (shared)
```

### Implementation

```java
@Tag(name = "User Profile")
@RestController
@RequestMapping("/user/profile")
@PreAuthorize("hasRole('USER')")     // class-level: USER only
@RequiredArgsConstructor
public class UserProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get user profile")
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("profile.found", profileService.getUserProfile(principal.getId()));
    }
}

@Tag(name = "CTV Dashboard")
@RestController
@RequestMapping("/ctv/dashboard")
@PreAuthorize("hasRole('CTV')")      // CTV only
@RequiredArgsConstructor
public class CtvDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get CTV dashboard")
    public ApiResponse<DashboardResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("dashboard.found", dashboardService.getCtvDashboard(principal.getId()));
    }
}
```

Enable method security once (`@EnableMethodSecurity` in `SecurityConfig` — see 07-module.md).

## Service Structure

### Shared Services

```java
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;

    public UserProfileResponse getUserProfile(UUID userId) { /* common logic */ }
    public CtvProfileResponse getCtvProfile(UUID userId)   { /* CTV-specific */ }
    public AdminProfileResponse getAdminProfile(UUID userId) { /* admin-specific */ }
}
```

Different role controllers call different methods on the same service — one service, no duplication.

## Authentication & Authorization

### Flow

```
1. JwtAuthenticationFilter extracts the Bearer token.
2. It validates the token and loads the user (or trusts verified claims).
3. It builds an Authentication with authorities (ROLE_USER / ROLE_CTV / ROLE_ADMIN)
   and stores it in the SecurityContext.
4. SecurityFilterChain applies path-based rules (/admin/** requires ROLE_ADMIN, ...).
5. @PreAuthorize enforces method-level rules.
6. Controllers read the principal via @AuthenticationPrincipal.
```

### JWT Authentication Filter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountService userAccountService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(req);
        if (token != null && jwtService.isValid(token)) {
            UUID userId = jwtService.getUserId(token);
            // Re-verify role against the DB (don't trust the token blindly for sensitive ops)
            UserPrincipal principal = userAccountService.loadActivePrincipal(userId);
            var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }

    private String resolveToken(HttpServletRequest req) {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
```

### Security Filter Chain (path-based authorization)

Path prefixes map to required roles here instead of a hand-rolled middleware `Map`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/*/auth/**", "/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/user/**").hasRole("USER")
                .requestMatchers("/ctv/**").hasRole("CTV")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/common/**").hasAnyRole("USER", "CTV", "ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(restAuthEntryPoint)   // -> 401 JSON
                .accessDeniedHandler(restAccessDeniedHandler)); // -> 403 JSON
        return http.build();
    }
}
```

## Package Organization

```
src/main/java/com/igogo/
├── auth/
│   ├── controller/{UserAuthController, CtvAuthController, AdminAuthController}.java
│   └── service/AuthServiceImpl.java        # shared auth logic
├── profile/
│   ├── controller/{UserProfileController, CtvProfileController}.java
│   └── service/ProfileServiceImpl.java     # shared profile logic
└── config/SecurityConfig.java
```

No "module registration" is needed — controllers and services are discovered by component scanning (see 07-module.md).

## Database Schema

### User Account Structure

```sql
-- Each user account carries a role
dtb_user_accounts (
    user_id     UUID REFERENCES dtb_users (id)  ON DELETE CASCADE,
    role_id     INTEGER REFERENCES mtb_roles (id) ON DELETE RESTRICT,
    sub_role_id INTEGER REFERENCES mtb_sub_roles (id) ON DELETE RESTRICT,
    status      VARCHAR(20)   -- ACTIVE / INACTIVE / WAIT_APPROVE
);
```

### Role Verification Query (repository)

```java
@Query("""
    SELECT a FROM UserAccount a
    JOIN FETCH a.role r
    WHERE a.user.id = :userId AND a.status = 'ACTIVE'
    """)
Optional<UserAccount> findActiveAccountWithRole(@Param("userId") UUID userId);
```

## Method Security & Current User

### Method-level Authorization

```java
@PreAuthorize("hasAnyRole('USER', 'CTV')")
@GetMapping("/shared-resource")
public ApiResponse<SharedResponse> getShared() { ... }

@PreAuthorize("hasRole('ADMIN') or #userId == principal.id")
@GetMapping("/{userId}")
public ApiResponse<UserResponse> get(@PathVariable UUID userId) { ... }
```

### Accessing the Current User

```java
@GetMapping("/profile")
public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success("profile.found", profileService.getUserProfile(principal.getId()));
}
```

`UserPrincipal` implements `UserDetails` and exposes `id`, `roleId`, `accountId`, and `getAuthorities()`.

## Best Practices

### 1. One Controller per Role

```java
// Good
UserOrderController   // /user/orders
CtvOrderController    // /ctv/orders
AdminOrderController  // /admin/orders

// Bad - one controller mixing role paths/checks ❌
```

### 2. Shared, Reusable Services

```java
// Good - single service, role-specific methods
@Service
public class OrderServiceImpl implements OrderService {
    public Page<OrderResponse> getUserOrders(UUID userId, Pageable p) { }
    public Page<OrderResponse> getCtvOrders(UUID ctvId, Pageable p) { }
    public Page<OrderResponse> getAllOrders(Pageable p) { }   // admin
}
// Bad - UserOrderService / CtvOrderService / AdminOrderService duplication ❌
```

### 3. Role Is Clear from the Path

`/user/profile`, `/ctv/statistics`, `/admin/settings` — not ambiguous `/profile` or `/api/v1/data`.

### 4. Authorize via Spring Security, Not Manual Checks

```java
// Good
@PreAuthorize("hasRole('CTV')")

// Bad - manual, indirect checks ❌
if (user.getCtvCode() == null) throw new ForbiddenException("user.ctvOnly");
```

### 5. Use Role Enums / `hasRole`, Not Magic Numbers

```java
// Good
@PreAuthorize("hasRole('CTV')")
// Bad
if (roleId == 2) { ... }   // ❌ what is 2?
```

## Security Considerations

### 1. Double Verification

The filter chain checks the JWT authorities; for sensitive operations the filter also re-loads the **active** role from the DB (`findActiveAccountWithRole`) so a revoked/changed role is honored immediately.

### 2. Token Invalidation on Role Change

When a user's role changes, invalidate existing tokens (e.g. token version / blocklist) and force re-authentication so the new authorities take effect.

### 3. Audit Logging

```java
log.warn("Unauthorized access: user {} with role {} tried to access {}", userId, roleId, path);
```

A custom `AccessDeniedHandler` is a good place to log 403s centrally.

### 4. Role Escalation Prevention

Never let a user change their own role; role changes go through admin-only endpoints and are audit-logged.

## Adding a New Role

```java
// 1. DB (migration): INSERT INTO mtb_roles (id, code, name) VALUES (4, 'VENDOR', 'Vendor');

// 2. Enum
public enum UserRole { USER, CTV, ADMIN, VENDOR; ... }

// 3. SecurityFilterChain
.requestMatchers("/vendor/**").hasRole("VENDOR")

// 4. Controllers
@RestController @RequestMapping("/vendor/products") @PreAuthorize("hasRole('VENDOR')")
public class VendorProductController { ... }

// 5. Auth service: vendor registration/login
```

## Testing Guidelines

### Role-Based Web Tests

```java
@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ProfileService profileService;

    @Test @WithMockUser(roles = "USER")
    void allowsUserRole() throws Exception {
        mockMvc.perform(get("/user/profile")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "CTV")
    void deniesCtvRole() throws Exception {
        mockMvc.perform(get("/user/profile")).andExpect(status().isForbidden());
    }

    @Test
    void deniesAnonymous() throws Exception {
        mockMvc.perform(get("/user/profile")).andExpect(status().isUnauthorized());
    }
}
```

## Quick Reference

| Path Pattern | Required Role | Controller | Service Method |
|-------------|---------------|------------|----------------|
| `/user/**` | `ROLE_USER` | `User*Controller` | `getUser*()` |
| `/ctv/**` | `ROLE_CTV` | `Ctv*Controller` | `getCtv*()` |
| `/admin/**` | `ROLE_ADMIN` | `Admin*Controller` | `getAll*()` |
| `/common/**` | any authenticated | `Common*Controller` | `getCommon*()` |

| Mechanism | Tool |
|-----------|------|
| Token → principal | `JwtAuthenticationFilter` (`OncePerRequestFilter`) |
| Path-based rules | `SecurityFilterChain.authorizeHttpRequests` |
| Method-level rules | `@PreAuthorize` (`@EnableMethodSecurity`) |
| Current user | `@AuthenticationPrincipal UserPrincipal` |
| 401 / 403 responses | `AuthenticationEntryPoint` / `AccessDeniedHandler` |

## Checklist for New Feature

- [ ] Create role-specific controllers under the right path prefix
- [ ] Implement a shared service with role-specific methods
- [ ] Add path rules to `SecurityFilterChain`
- [ ] Add `@PreAuthorize` to controllers/methods
- [ ] Create role-specific DTOs if needed
- [ ] Write role-based `@WebMvcTest`s (`@WithMockUser`)
- [ ] Update OpenAPI docs
- [ ] Add audit logging for sensitive operations
