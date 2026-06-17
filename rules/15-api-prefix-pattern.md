# API Prefix Pattern Rules - iGoGo Backend

## Overview

All API endpoints MUST follow a consistent prefix pattern based on the role accessing them. This ensures separation of concerns, role-based access control, and clear API organization. In Spring Boot the prefix is declared on `@RequestMapping`, the role tag on `@Tag`, and authorization is enforced by the `SecurityFilterChain` + `@PreAuthorize` (see 11-multi-role-system.md).

## Role-Based Prefix Structure

### Basic Pattern

```
/{role-prefix}/{resource}/{action}
```

### Role Prefixes

| Role | Role Code | Prefix | Description |
|------|-----------|--------|-------------|
| Khách hàng (Customer) | USER | `/user` | APIs for customers/travelers |
| Chủ nhà (Host/Seller) | SELLER | `/seller` | APIs for homestay owners |
| Cộng tác viên (Partner) | PARTNER | `/partner` | APIs for collaborators |
| Nhân viên (Employee) | EMPLOYEE | `/employee` | APIs for platform staff |
| Nhân sự VABE (kế toán/thuế) | VABE_STAFF | `/vabe-staff` | VABE accounting staff (manager-only sub-routes via `@PreAuthorize` on sub-role) |
| Đối tác phát triển | DEVELOPMENT_PARTNER | `/dev-partner` | APIs for development partners |
| Common/Public | - | `/common` | Shared/public APIs |

## Global API Prefix

Set a single application-wide base path so all controllers sit under, e.g., `/api`:

```yaml
# application.yml
server:
  servlet:
    context-path: /api      # every endpoint is served under /api/...
```

Or, to add a prefix only to `@RestController`s (keeping actuator/swagger off it), use a `WebMvcConfigurer`:

```java
@Configuration
public class ApiPrefixConfig implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
            c -> c.isAnnotationPresent(RestController.class));
    }
}
```

> Pick one approach and keep role prefixes (`/user`, `/seller`, ...) on the controllers themselves — the global prefix is orthogonal.

## Detailed Rules

### 1. Controller Prefix Declaration

Every controller MUST declare its role prefix on `@RequestMapping`:

```java
// ✅ GOOD - role-specific prefix
@RestController
@RequestMapping("/seller/homestay-registration")
public class HomestayRegistrationController { /* /seller/homestay-registration/* */ }

// ✅ GOOD - common prefix for shared APIs
@RestController
@RequestMapping("/common/locations")
public class LocationController { /* /common/locations/* */ }

// ❌ BAD - no role prefix (ambiguous ownership)
@RestController
@RequestMapping("/homestay-registration")
public class HomestayRegistrationController { }
```

### 2. @Tag Naming Convention

`@Tag` MUST include the role prefix for clarity in Swagger UI:

```java
// ✅ GOOD
@Tag(name = "Seller - Homestay Registration")
@RequestMapping("/seller/homestay-registration")

// ✅ GOOD - sub-feature clarity
@Tag(name = "Seller - Homestay Registration - Content")

// ✅ GOOD - common APIs
@Tag(name = "Common - Locations")
@RequestMapping("/common/locations")

// ❌ BAD - no role indication
@Tag(name = "Homestay Registration")
```

### 3. Role-Specific Examples

#### Seller (Chủ nhà) APIs

```java
@Tag(name = "Seller - Homestay Registration")
@RestController
@RequestMapping("/seller/homestay-registration")
@PreAuthorize("hasRole('SELLER')")
public class HomestayRegistrationController {
    @PostMapping("/create")            // POST /seller/homestay-registration/create
    @GetMapping("/{id}/progress")      // GET  /seller/homestay-registration/{id}/progress
}

@Tag(name = "Seller - Dashboard")
@RestController
@RequestMapping("/seller/dashboard")
@PreAuthorize("hasRole('SELLER')")
public class SellerDashboardController {
    @GetMapping                        // GET /seller/dashboard
    @GetMapping("/statistics")         // GET /seller/dashboard/statistics
}
```

#### User (Khách hàng) APIs

```java
@Tag(name = "User - Profile")
@RestController
@RequestMapping("/user/profile")
@PreAuthorize("hasRole('USER')")
public class UserProfileController {
    @GetMapping                        // GET /user/profile
    @PutMapping                        // PUT /user/profile
}

@Tag(name = "User - Homestay Search")
@RestController
@RequestMapping("/user/homestays")
@PreAuthorize("hasRole('USER')")
public class UserHomestayController {
    @GetMapping("/search")             // GET /user/homestays/search
    @GetMapping("/{id}")               // GET /user/homestays/{id}
}
```

#### Partner / Employee APIs

```java
@Tag(name = "Partner - Dashboard")
@RestController @RequestMapping("/partner/dashboard")
@PreAuthorize("hasRole('PARTNER')")
public class PartnerDashboardController {
    @GetMapping                        // GET /partner/dashboard
    @GetMapping("/commissions")        // GET /partner/dashboard/commissions
}

@Tag(name = "Employee - Homestay Review")
@RestController @RequestMapping("/employee/homestay-review")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeHomestayReviewController {
    @GetMapping("/pending")            // GET  /employee/homestay-review/pending
    @PostMapping("/{id}/approve")      // POST /employee/homestay-review/{id}/approve
}
```

#### Common / Public APIs

```java
@Tag(name = "Common - Locations")
@RestController @RequestMapping("/common/locations")
@PreAuthorize("isAuthenticated()")    // any authenticated role
public class LocationController {
    @GetMapping("/provinces")                  // GET /common/locations/provinces
    @GetMapping("/wards/{provinceId}")         // GET /common/locations/wards/{provinceId}
}

@Tag(name = "Public - General")
@RestController @RequestMapping("/public/general")
public class PublicController {                // permitAll() in SecurityFilterChain
    @GetMapping("/about")                      // GET /public/general/about
    @GetMapping("/contact")                    // GET /public/general/contact
}
```

### 4. Authentication Routes

Auth routes also follow role prefixes:

```java
@Tag(name = "Seller - Authentication")
@RestController @RequestMapping("/seller/auth")
public class SellerAuthController {
    @PostMapping("/register")          // POST /seller/auth/register
    @PostMapping("/login")             // POST /seller/auth/login
    @PostMapping("/refresh-token")     // POST /seller/auth/refresh-token
}

@Tag(name = "User - Authentication")
@RestController @RequestMapping("/user/auth")
public class UserAuthController {
    @PostMapping("/register")          // POST /user/auth/register
    @PostMapping("/login")             // POST /user/auth/login
}

// ❌ BAD - generic /auth without role (ambiguous)
```

### 5. Sub-Resources Pattern

Keep the role prefix on nested resources:

```java
@RestController @RequestMapping("/seller/homestays")
public class SellerHomestayController {
    @GetMapping("/{homestayId}/bookings")   // GET /seller/homestays/{homestayId}/bookings
    @GetMapping("/{homestayId}/reviews")    // GET /seller/homestays/{homestayId}/reviews
}

@RestController @RequestMapping("/user/bookings")
public class UserBookingController {
    @GetMapping("/{bookingId}/reviews")     // GET /user/bookings/{bookingId}/reviews
    @PutMapping("/{bookingId}/cancel")      // PUT /user/bookings/{bookingId}/cancel
}
```

### 6. Versioning (Optional)

Place the version AFTER the role prefix:

```java
// ✅ GOOD
@RequestMapping("/seller/v1/homestay-registration")
@RequestMapping("/seller/v2/homestay-registration")

// ❌ BAD - version before role prefix
@RequestMapping("/v1/seller/homestay-registration")
```

### 7. Sub-Role Restriction

For manager-only sub-routes (e.g. `VABE_STAFF_MANAGER`), use `@PreAuthorize` with an authority/expression rather than a custom NestJS-style `@SubRoles`:

```java
@RestController @RequestMapping("/vabe-staff")
@PreAuthorize("hasRole('VABE_STAFF')")
public class VabeStaffController {

    @GetMapping("/orders")                                    // any VABE staff
    public ApiResponse<?> orders() { ... }

    @GetMapping("/admin/settings")
    @PreAuthorize("hasAuthority('SUBROLE_VABE_STAFF_MANAGER')")// manager sub-role only
    public ApiResponse<?> adminSettings() { ... }
}
```

Grant the sub-role as an extra authority (e.g. `SUBROLE_VABE_STAFF_MANAGER`) in the JWT filter when building the principal.

## Authorization (Security, not a custom guard)

Path prefixes map to roles in the `SecurityFilterChain`; method-level rules use `@PreAuthorize`. There is no hand-rolled `RoleGuard` — Spring Security does the matching:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/public/**", "/*/auth/**", "/health").permitAll()
    .requestMatchers("/user/**").hasRole("USER")
    .requestMatchers("/seller/**").hasRole("SELLER")
    .requestMatchers("/partner/**").hasRole("PARTNER")
    .requestMatchers("/employee/**").hasRole("EMPLOYEE")
    .requestMatchers("/vabe-staff/**").hasRole("VABE_STAFF")
    .requestMatchers("/dev-partner/**").hasRole("DEVELOPMENT_PARTNER")
    .requestMatchers("/common/**").authenticated()
    .anyRequest().authenticated());
```

## Swagger Organization

springdoc groups endpoints by `@Tag`, giving clear navigation:

```
Swagger UI Groups:
├── Seller - Homestay Registration
│   ├── POST /seller/homestay-registration/create
│   └── GET  /seller/homestay-registration/{id}/progress
├── Seller - Dashboard
├── User - Profile
├── Common - Locations
└── ...
```

You can also split the docs into grouped APIs per role with springdoc `GroupedOpenApi`:

```java
@Bean
public GroupedOpenApi sellerApi() {
    return GroupedOpenApi.builder().group("seller").pathsToMatch("/seller/**").build();
}
```

## Migration from Old Patterns

### Before

```java
@Tag(name = "homestay-registration")
@RestController @RequestMapping("/homestay-registration")
public class HomestayRegistrationController { }
```

### After

```java
@Tag(name = "Seller - Homestay Registration")
@RestController @RequestMapping("/seller/homestay-registration")
@PreAuthorize("hasRole('SELLER')")
public class HomestayRegistrationController { }
```

## Benefits

1. **Clear role separation** — obvious which role owns which endpoints.
2. **Better security** — `SecurityFilterChain` matches prefixes to roles.
3. **Improved DX** — clean grouping in Swagger.
4. **Easier testing** — `@WebMvcTest` + `@WithMockUser` organized by role.
5. **Simplified authorization** — path-based rules are declarative.

## Exceptions

Acceptable endpoints without a role prefix:

1. **Health/metrics:** `/health`, `/actuator/**`
2. **Root/docs:** `/`, `/v3/api-docs/**`, `/swagger-ui/**`
3. **Webhooks:** `/webhooks/**` (external integrations; secured by signature)
4. **Static assets:** `/assets/**`, `/public/**`

## Checklist for New Controllers

- [ ] Role-based prefix on `@RequestMapping`?
- [ ] `@Tag` includes the role name?
- [ ] All endpoints under the correct role prefix?
- [ ] `SecurityFilterChain` / `@PreAuthorize` enforces the matching role?
- [ ] Swagger clearly indicates the role?
- [ ] Related controllers grouped under the same prefix?

## Examples Summary

| Role | Prefix | Example Endpoint |
|------|--------|------------------|
| USER | `/user` | `/user/profile` |
| SELLER | `/seller` | `/seller/homestay-registration` |
| PARTNER | `/partner` | `/partner/dashboard` |
| EMPLOYEE | `/employee` | `/employee/homestay-review` |
| VABE_STAFF | `/vabe-staff` | `/vabe-staff/orders`, `/vabe-staff/admin/*` (manager only via `@PreAuthorize`) |
| DEVELOPMENT_PARTNER | `/dev-partner` | `/dev-partner/referrals` |
| Common | `/common` | `/common/locations` |
| Public | `/public` | `/public/about` |
