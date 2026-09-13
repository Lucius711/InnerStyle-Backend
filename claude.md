# InnerStyle Backend — Development Rules

**MANDATORY**: Read this file before writing or modifying any backend code in this repo.
It documents the conventions actually used by this codebase (Java 21, Spring Boot 3.3,
Maven, PostgreSQL, Flyway). Previous versions of this file pointed at a `rules/` directory
copied from a different project (iGoGo) that does not exist here — ignore any lingering
references to it; this file is now self-contained.

## 1. What this service is

`com.innerstyle` — turns a 2D image/text prompt into a colored/posed/rigged/animated 3D
model via the MeshyAI API, plus the surrounding product: auth, membership/credits, wallet
payments (VNPay/MoMo), and paid 3D-print orders. See `README.md` for the domain walkthrough
and MeshyAI async flow (webhook + polling fallback).

## 2. Module map (package = feature, not layer-only)

```text
com.innerstyle
├── config/                 # ApiPrefixConfig (/api prefix), OpenApiConfig
├── common/
│   ├── response/            # ApiResponse<T>, ErrorResponse
│   ├── exception/            # AppException hierarchy + GlobalExceptionHandler
│   └── web/                  # ClientIpResolver, cross-cutting web helpers
├── auth/                    # users, roles, JWT, OAuth (Google/Facebook), login audit
│   ├── config/ (JwtProperties, OauthProperties, SecurityConfig)
│   ├── controller/ service/ repository/ entity/(+enums) mapper/ dto/{request,response}
│   └── security/            # JwtAuthenticationFilter, JwtService, UserPrincipal, TokenHasher
├── membership/              # subscription tiers + credit balances/ledger
├── meshy/                   # MeshyAI integration: client, tasks, webhook, polling
│   ├── client/               # MeshyClient (RestClient) + external API DTOs
│   ├── controller/            # REST + /webhooks/meshy
│   └── util/                  # SsrfGuard (validates outbound/callback URLs)
├── print/                   # paid 3D-print orders (figurine height/price tiers)
├── wallet/                  # payment orders, VNPay/MoMo gateways, credit top-ups
│   ├── gateway/               # CryptoSigner, GatewayVerification, VnpayGateway, MomoGateway
│   └── seed/                  # startup data seeding
└── redis/                   # cache, rate limiting (fail-open if Redis is down), security (token blocklist etc.)
```

Each feature package is internally layered: `controller / service (+ impl) / repository /
entity (+ enums) / mapper / dto/{request,response}`. Follow this shape for any new feature
package.

## 3. Tech stack

- Java 21, Spring Boot 3.3.5, Maven
- Spring Web MVC, Spring Security (JWT, stateless), Spring Data JPA, Flyway, PostgreSQL
- Spring Data Redis (cache + rate limiting), Spring Mail + Resend HTTP API (transactional email)
- Lombok, MapStruct, springdoc-openapi (`/swagger-ui.html`), Bean Validation
- `spring-dotenv` loads `.env` into `${...}` placeholders — never hardcode secrets in `application.yml`
- Tests: JUnit 5, Mockito, AssertJ, Testcontainers (see `support/AbstractIntegrationTest`,
  `EnabledIfDockerAvailable` — integration tests skip gracefully when Docker isn't available)

## 4. Naming & structure conventions

- Java files: PascalCase matching the declared type. Suffixes: `*Controller`, `*Service` /
  `*ServiceImpl` (interface in `service/`, impl in `service/impl/`), `*Repository`,
  `*Request`/`*Response` (prefer `record`), `*Exception`, `*Mapper` (MapStruct,
  `componentModel = "spring"`).
- Variables/methods: camelCase. Constants: `UPPER_SNAKE_CASE`. Booleans prefixed
  `is/has/should/can`.
- DB tables/columns: snake_case. Entities map camelCase fields via `@Column(name = "...")`.
- Constructor injection only (`@RequiredArgsConstructor`), never field `@Autowired`.
- No raw `Object` / `Map<String, Object>` as a stand-in for a real type. No magic
  numbers/strings — use enums or named constants.
- Records for request/response DTOs; validate with Jakarta Bean Validation
  (`@NotBlank`, `@Email`, `@Size`, …). Never return JPA entities from a controller — map to
  a response DTO.

## 5. Database schema & migrations

- **`mtb_` prefix** = master/reference data (e.g. `mtb_roles`). FK → `mtb_*` uses
  `ON DELETE RESTRICT`. Master rows are seeded via idempotent `INSERT ... ON CONFLICT (code)
  DO UPDATE` inside a migration (small, static, id + code + name).
- **`dtb_` prefix** = transactional/operational data (e.g. `dtb_users`, `dtb_meshy_tasks`,
  `dtb_payment_orders`). FK → `dtb_*` uses `ON DELETE CASCADE` unless the relation is
  intentionally optional (`SET NULL`, nullable column).
- Primary keys: `UUID` (`@GeneratedValue @UuidGenerator`) for `dtb_*` entities that are
  externally referenced/exposed; `Integer IDENTITY` for small `mtb_*` master tables.
- Flyway migrations live in `src/main/resources/db/migration/`, named
  `V<yyyyMMddHHmmss>__<snake_case_description>.sql`. Generate the timestamp with
  `date -u +%Y%m%d%H%M%S`. Migrations are immutable once merged — write a new forward
  migration to change or revert something already applied. `hibernate.ddl-auto: validate`
  — Flyway, not Hibernate, owns the schema.
- Never seed application/business content in a migration. The only allowed exception is
  small, static, FK-required master data (as above).

## 6. API surface

- Global prefix: every `@RestController` is served under `/api/**` via
  `ApiPrefixConfig` (`WebMvcConfigurer#configurePathMatch`). Don't add `/api` manually on
  `@RequestMapping` — it's applied automatically.
- Role/resource prefix on top of that, e.g. `/user/auth/**` → served at
  `/api/user/auth/**`. Current roles: `USER`, `ADMIN`, `STAFF` (seeded master data in
  `mtb_roles`, users can hold multiple roles via `dtb_user_roles`). Public/shared endpoints
  live under `/common/**` (e.g. `/common/3d/**`).
- Authorization is enforced in `auth/config/SecurityConfig` (path-based
  `authorizeHttpRequests`) and `@PreAuthorize`/`hasRole(...)` at the method level — not
  hand-rolled guards. CORS is owned **solely** by `SecurityConfig#corsConfigurationSource()`
  (curated origin allow-list via `APP_CORS_ALLOWED_ORIGIN_PATTERNS`); do not add a second
  CORS config (`addCorsMappings`) — a prior duplicate silently widened the allow-list
  (see BUG-001 note in `ApiPrefixConfig`).
- Auth: stateless JWT (`JwtAuthenticationFilter` + `JwtService`), access token in the
  response body, long-lived refresh token delivered as an **HttpOnly, path-scoped cookie**
  (`refresh_token`, scoped to `/api/user/auth`) — never expose the refresh token to
  JS-readable storage.

## 7. Response & error format

- Success envelope: `ApiResponse<T> { success, message, data }` via
  `ApiResponse.success(messageCode, data)`.
- Error envelope: `ErrorResponse` produced solely by `GlobalExceptionHandler`
  (`@RestControllerAdvice`) — controllers/services should not build error responses by hand.
- **No server-side i18n / `MessageSource`.** `message` / error codes are stable,
  machine-readable strings (e.g. `auth.registered`, `meshy.task.notFound`,
  `validation.email.required`) returned **verbatim**; the frontend translates them. Do not
  add `messages*.properties` or resolve messages server-side — this is an intentional
  departure from generic Spring i18n examples.
- Throw a specific `AppException` subtype from `common/exception`
  (`BadRequestException` 400, `UnauthorizedException` 401, `ForbiddenException` 403,
  `ResourceNotFoundException` 404, `ConflictException` 409, `UpstreamServiceException` /
  `UpstreamRequestException` for MeshyAI/payment-gateway failures, `EmailDeliveryException`
  for mail sending) with a message-code string, e.g.
  `throw new ResourceNotFoundException("meshy.task.notFound");`. Let it bubble to the
  advice — don't catch-and-format in controllers.

## 8. Service layer

- `@Service @RequiredArgsConstructor`, interface in `service/`, impl in `service/impl/`.
- `@Transactional` on service methods (`readOnly = true` for reads); never open
  transactions in controllers or repositories.
- External integrations (MeshyAI, VNPay, MoMo, Resend) go through a dedicated
  `client`/`gateway` package with their own DTOs — never call `RestClient`/HTTP directly
  from a controller or a domain service.
- Outbound URLs supplied by users/webhooks (image URLs, callback URLs) MUST be validated
  with `meshy.util.SsrfGuard` (or an equivalent check) before being fetched — this is a
  known hardening point in this codebase, not optional.
- Payment gateway callbacks (VNPay/MoMo IPN) must be signature-verified via
  `GatewayVerification`/`CryptoSigner` before any state change; treat these endpoints as
  hostile input.

## 9. Repository layer

- Spring Data JPA interfaces extending `JpaRepository<Entity, IdType>` (+
  `JpaSpecificationExecutor` for dynamic filters). Derived queries / JPQL `@Query` with
  bound `:params` — never string-concatenated SQL. Return `Optional<T>` for single results,
  `existsBy*`/`countBy*` for checks, never fetch-then-null-check.

## 10. Testing

- `./mvnw test` (unit + slice), `./mvnw verify` (+ integration, needs Docker for
  Testcontainers — guarded by `EnabledIfDockerAvailable`, so it skips cleanly without
  Docker rather than failing).
- Mirror `src/main/java` package structure under `src/test/java`. Unit tests: JUnit 5 +
  Mockito + AssertJ (`@Mock`/`@InjectMocks`, arrange-act-assert). Integration tests extend
  `support.AbstractIntegrationTest` (Testcontainers Postgres).
- Test business logic, validation, error paths, and edge cases in services; don't test
  Lombok-generated code or trivial derived-query repositories.

## 11. Configuration & secrets

- All tunables come from `application.yml` bound to `@ConfigurationProperties`, sourced
  from env vars with sane dev defaults (`${VAR:default}`) — see the `app.*` tree
  (`app.meshy`, `app.jwt`, `app.auth`, `app.oauth`, `app.payment`, `app.print`,
  `app.rate-limit`, `app.cors`, `app.resend`).
- Secrets live in `.env` (git-ignored), loaded by `spring-dotenv`. Never commit real
  secrets; `.env.example` / `.env.deploy.example` document the required keys.
- Redis-backed features (cache, rate limiting) must fail open if Redis is unreachable —
  don't let a Redis outage take down auth/API availability.

## 12. Enforcement

- Apply these rules to every backend task (controller, service, DTO, migration, entity).
- If a request conflicts with a rule (e.g. asks to add server-side i18n, or to bypass
  `SsrfGuard`/webhook signature verification), flag it and confirm before proceeding.
- Format/lint before considering work done: `./mvnw spotless:apply` then
  `./mvnw clean compile` — fix warnings rather than suppressing them.
