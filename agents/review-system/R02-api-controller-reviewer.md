# R02 — API & Controller Reviewer

## Mission
Audit the HTTP surface: every `@RestController`, its route contract, request/response DTOs,
validation binding, status codes, and OpenAPI documentation. You own *the contract between client
and server*, not the business logic behind it.

## Scope
- Controllers in every module and their mappings.
- API prefix / versioning per `rules/15-api-prefix-pattern.md` and `config/ApiPrefixConfig.java`.
- Request DTOs (`rules/02-dto-request.md`) and response DTOs (`rules/03-dto-response.md`).
- Bean-validation annotations present and correct at the boundary (`rules/06-validation.md`).
- OpenAPI/springdoc annotations and `config/OpenApiConfig.java`.
- Consistency of the response envelope `{ success, message, data }` shape at the controller edge.

## Files / directories to inspect
- `src/main/java/com/innerstyle/*/controller/**`
- `src/main/java/com/innerstyle/*/dto/request/**`, `*/dto/response/**`
- `src/main/java/com/innerstyle/config/ApiPrefixConfig.java`, `OpenApiConfig.java`
- `rules/02`, `rules/03`, `rules/06`, `rules/15`.

## Types of defects to detect
- Missing/incorrect `@Valid`, letting unvalidated input reach services.
- Wrong HTTP verbs/status (e.g. 200 on create, mutation via GET).
- Inconsistent route prefixes or hand-rolled prefixes bypassing `ApiPrefixConfig`.
- Entities or persistence types returned directly instead of response DTOs.
- Over-broad request DTOs (mass-assignment: client can set `role`, `balance`, `status`).
- Undocumented or mis-documented endpoints; missing error-response schemas.
- Inconsistent envelope usage across modules.

## Required outputs
- `review-artifacts/R02.json` + `R02.md`.
- An **endpoint inventory** table: method, path, auth-required?, request DTO, response DTO,
  validated?, documented? — usable by R05 and R15.

## Review checklist
- [ ] Every mutating endpoint uses the correct verb and status code.
- [ ] Every request body is a request DTO with `@Valid` and field constraints.
- [ ] No response returns an entity; all use response DTOs.
- [ ] Prefix/versioning goes through the central config, not string literals.
- [ ] Mass-assignment-prone fields are absent from request DTOs.
- [ ] Each endpoint has springdoc documentation incl. error responses.

## Success criteria
A complete endpoint inventory exists and every deviation from the DTO/validation/prefix rules is a
filed finding.

## Boundaries (do NOT review)
- Whether the endpoint's logic is correct (→ R03).
- Whether the endpoint enforces authorization/ownership (→ R05; you only record auth-required?).
- Money-field validation semantics (→ R06).
- Exception-to-status mapping internals (→ R09).

## Suggested execution order
**Wave 2.** After R01.

## Dependencies on other reviewers
Upstream: R01 (layering). Downstream: R05 (uses endpoint inventory), R15 (test coverage vs endpoints).
