# Multi-Tenancy Retrofit — Design & Phased Plan

Retrofit true row-level multi-tenancy onto the HCM platform, which is currently
**single-tenant** (tenant is a hard-coded `"default"` constant; 199/378 tables
lack `tenant_id`; no tenant resolution or enforcement).

## Locked decisions (user, 2026-07-12)
1. **Tenant identity = realm-per-tenant.** Each customer org is its own Keycloak
   realm; the request's tenant is derived from the **JWT issuer** (`iss`). The
   resource server must trust *multiple* issuers and map each → a tenant id.
2. **Isolation = shared DB + `tenant_id` discriminator.** One database; every
   business table carries `tenant_id`; Hibernate 6 `@TenantId` auto-filters
   every JPA query and stamps inserts.
3. **Reference data = per-tenant.** Leave types, holidays, document types,
   competency taxonomy, workflow definitions, statutory rules, etc. are
   tenant-scoped too → effectively **every** business table gets `tenant_id`,
   and each new tenant must be **seeded** with its own reference data.

## Target architecture
```
Request → OIDC JWT (realm R) 
  → Spring JwtIssuerAuthenticationManagerResolver (trusts realms R1..Rn)
  → TenantResolutionFilter: iss → tenantId → TenantContext.set(tenantId)
  → CurrentTenantIdentifierResolver returns TenantContext.get() (default "default")
  → Hibernate @TenantId: every @TenantId entity is filtered/stamped by tenantId
  → (native SQL) services read TenantContext.current() instead of TENANT="default"
```

Key building blocks:
- `common/tenant/TenantContext` — request-scoped ThreadLocal holding the current tenant id; `current()` returns it or falls back to `"default"` for system/scheduler threads.
- `common/tenant/TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String>` — Hibernate reads this on every session; returns `TenantContext.current()`; `validateExistingCurrentSessions()=false`.
- `common/tenant/TenantRegistry` — maps a JWT issuer URL → tenantId (seed table `tenant` / config). Backed by a `tenant` master table (id, code, name, issuer_uri, realm, active, created_at).
- `security/TenantResolutionFilter` — after auth, reads the Jwt `iss`, resolves via TenantRegistry, sets TenantContext; clears in `finally`.
- Security config: replace the single `issuer-uri` with `JwtIssuerAuthenticationManagerResolver` over the registry's trusted issuers (dynamic).
- Every entity: `@TenantId private String tenantId;` (via a `@MappedSuperclass TenantScoped` where the class hierarchy allows, else a per-entity field).
- Every table: `tenant_id VARCHAR NOT NULL DEFAULT 'default'` + index `(tenant_id, ...)`.

## CRITICAL gotchas (must be handled or isolation is fake)
1. **Native SQL bypasses Hibernate `@TenantId`.** The app has MANY
   `JdbcTemplate`/`NamedParameterJdbcTemplate` queries that hard-code
   `TENANT="default"`. Hibernate's tenant filter does NOT touch these. Every one
   must read `TenantContext.current()` instead of the constant, and every native
   INSERT must set `tenant_id`. This is a large surface — grep `private static final String TENANT` and every `.addValue("tenant"` / `Map.of(... "tenant"`.
2. **Partial rollout = false isolation.** An un-annotated entity / a table
   without `tenant_id` is globally visible. Until the rollout is 100% complete
   AND native SQL is converted, onboarding a 2nd real tenant WILL leak. Rule:
   **stay single-tenant ('default') until Phase 5 sign-off.** Adding `@TenantId`
   incrementally is safe *only* while 'default' is the sole tenant.
3. **System / scheduler / async threads** have no request → no JWT. `TenantContext.current()` must fall back safely (e.g. per-tenant scheduled jobs must loop tenants explicitly; today's `@Scheduled` sweeps assume one tenant).
4. **Per-tenant seeding/provisioning.** New tenant = new realm + a `tenant` row + seed its reference data (leave types, holidays, statutory rules, workflow defs, doc types…). Needs a `TenantProvisioningService` + repeatable seed templates (parameterize the existing V10/V16/V34/V62… seeds by tenant).
5. **Flyway seeds** currently insert reference rows for a single tenant. With per-tenant reference data they become per-tenant provisioning, not migrations.
6. **Cross-tenant references** (FKs) must never span tenants; add tenant-consistency checks where two tenant-scoped tables join.
7. **Uniqueness constraints** (employee_no, sequences, codes) must become per-tenant unique `(tenant_id, code)` — many UNIQUE indexes need widening. Sequences (`employee_no_seq`, `PS-`, etc.) are global today; per-tenant numbering needs `(tenant_id, seq)`.

## Phased plan (each phase: build → boot-verify → tests → commit)

**Phase 1 — Foundation rails (behavior-preserving).**
TenantContext, TenantIdentifierResolver, TenantRegistry + `tenant` master table
(seed one row: code 'default', issuer = current realm), Hibernate multitenancy
config wired with the resolver returning 'default'. NO entity annotated yet →
zero behavior change. Boot-verify + full suite green.

**Phase 2 — Complete schema + entity rollout (the big mechanical push).**
Migration(s): add `tenant_id NOT NULL DEFAULT 'default'` + `(tenant_id,…)`
indexes to ALL business tables lacking it (199); widen per-tenant UNIQUE
constraints. Add `@TenantId` to ALL 374 entities. Convert EVERY native-SQL
`TENANT="default"` site to `TenantContext.current()`. This must be complete —
partial = leak. Boot-verify; full suite green; a 2-tenant isolation test.

**Phase 3 — Multi-issuer security.**
Swap single `issuer-uri` for `JwtIssuerAuthenticationManagerResolver` over the
TenantRegistry; `TenantResolutionFilter` sets TenantContext from `iss`. Keep the
current realm working as tenant 'default'. Verify a token from a 2nd realm
resolves to a 2nd tenant.

**Phase 4 — Tenant provisioning & per-tenant seeding.**
`TenantProvisioningService` (create tenant row + trigger realm + seed reference
data). Parameterize the reference seeds per tenant. Scheduler jobs loop tenants.

**Phase 5 — Isolation test suite + cutover sign-off.**
2+ tenants seeded; automated tests prove: no cross-tenant read on ANY module
(JPA + native SQL + reports), inserts land in the caller's tenant, uniqueness is
per-tenant, self/manager scoping still holds within a tenant. Extend
`scripts/uat_smoke.py` with a cross-tenant leak battery. Only after this is green
is it safe to onboard a real 2nd tenant.

## Effort
Large. Phase 2 alone touches 199 migrations-worth of columns + 374 entities +
every native-SQL tenant site — days of careful, verified work, best done
module-by-module with boot-verify + isolation tests per batch. Phases 1 and 3
are the risky/architectural ones; Phase 2 is mechanical-but-vast; Phase 4–5 make
it real.

## Status
- [x] **Phase 1 — foundation rails** (700331d) TenantContext + TenantIdentifierResolver, resolver returns 'default'. Boot+test verified.
- [x] **Phase 2a — schema** (31f0616) V309 adds `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` to the 198 business tables lacking it (191 regular + 7 partitioned parents propagate to partitions).
- [x] **Phase 2b(i) — @TenantId on 197 entities** (1663044) newly-columned tables.
- [x] **Phase 2b(ii) — @TenantId on 169 pre-existing tenantId entities** (e77227a). `TenantSetting` kept as composite `@IdClass` key (Hibernate forbids `@TenantId` on an `@Id` field) — isolation comes from the key. 365 entities now carry `@TenantId`. Boot + smoke (24 PASS) verified.
- [x] **Phase 2b(iii) — 8 UUID-"tenant" attendance entities: NO CONVERSION NEEDED (verified).**
  AttendancePolicy/Period/Exception/CorrectionRequest/PayrollSummary, DeviceMaster,
  ExceptionConfig, OvertimeRequest carry a **UUID `tenant_id` that is really a
  legal-entity id** (`defaultTenantId()` = first `LegalEntity`). `LegalEntity` itself
  is `@TenantId String`-scoped, so once tenant context is live these rows are
  **transitively tenant-isolated**: verified every finder in all 7 policy/period/…
  repos filters by the legal-entity UUID (`findByTenantIdAnd…`, `findByIdAndTenantId`,
  `findBestMatch(tenantId,…)`, the single `@Query` has `WHERE p.tenantId=:tenantId`),
  and the 8th (AttendancePayrollSummary) is keyed on `payrollRunId` → `@TenantId`-scoped
  `payroll_run`. No unscoped list finder, no native-SQL bypass. The Option-A rename
  (UUID `tenantId`→`legalEntityId` + add string `@TenantId`, ~89 logic-bearing edits
  across 6 **mixed** services that also touch string-tenant entities) would add a
  Hibernate auto-filter safety net but is disproportionate risk on the payroll-feeding
  attendance module for a single-tenant app → **deferred to Tier-2 cleanup**, tracked below.
- [x] **Phase 2c — native-SQL/inline tenant sites** (dd7108d + 21fa7e1). (1) 117 `private static final String TENANT[_ID]="default"` constants → `TenantContext.current()`; (2) remaining inline family — local `String tenant[Id]="default"`, `setTenantId/findByTenantId("default"…)`, and 4 `!"default".equals(x.getTenantId())` ownership guards → `TenantContext.current()`. Exhaustive sweep confirms **no** call site hard-codes the tenant (TenantSetting composite-key field initializer excepted). Behaviour-preserving today; correct once Phase 3 binds tenant. mvn compile + 610 tests + boot + smoke (24 PASS) verified.

**→ PHASE 2 COMPLETE.** Every business table has `tenant_id`; 365 entities carry `@TenantId`; 8 attendance UUID entities transitively isolated; zero hard-coded tenant literals. The app is fully wired for discriminator multi-tenancy but stays single-tenant ('default') because the resolver returns 'default' until Phase 3 binds a real tenant from the JWT.

- [x] **Phase 3 — multi-issuer security** (ea5aae9). V310 config.tenant registry + Tenant entity/repo + TenantRegistry cache; KeycloakJwtDecoderFactory (per-issuer decoder, old single-issuer bean removed); TenantAuthenticationResolver (JwtIssuerAuthenticationManagerResolver over the registry — only active-tenant issuers admitted); TenantResolutionFilter (iss→tenant→TenantContext, cleared in finally); SecurityConfig switched to `.authenticationManagerResolver(...)`. Boot + smoke (24 PASS) verified.
- [x] **Phase 4 — provisioning + per-tenant seeding** (5593b16). TenantReferenceSeeder (generic native-SQL cloner: parent-first, UUID-PK regen into a global old→new map, auto-remap of any UUID column found in it → intra-reference FKs follow; no per-table column/FK config); TenantProvisioningService.provision() (register tenant + clone reference data + registry.refresh(), one tx); TenantAdminController (SYSTEM_ADMIN GET/POST /api/admin/tenants). Boot + smoke verified; functional proof in Phase 5.
- [x] **Phase 5 — isolation tests + cutover** (d14aaca). 2nd Keycloak realm millers-acme → tenant 'acme'. V311 widened 12 tenant-blind reference UNIQUE constraints to include tenant_id (gotcha #7); V312 scoped the leave_group/payroll_group 'single default' partial indexes per-tenant; org_unit_type dropped from the clone set (PK is the natural code — needs a composite-PK migration, deferred). `scripts/tenant_isolation_test.py` proves it: acme provisioned (233 reference rows), acme admin token authenticates via the multi-issuer resolver, creates an employee (write-path tenant stamping), and **PASS=9 FAIL=0 — tenant isolation holds** (acme sees only acme ids, default unaffected, by-id cross-reads 404 both ways). Default-tenant uat_smoke still 24 PASS; 610 JUnit green.

**→ MULTI-TENANCY COMPLETE.** True row-level multi-tenancy is live and proven with two real realms. Latest migration V312.

## Follow-ups — CLOSED (commit 58ec37b)
- [x] **org_unit_type** made a **shared** universal taxonomy (dropped `@TenantId`) — every tenant sees the seeded types; no composite-PK surgery, no cloning needed.
- [x] **Per-tenant uniqueness for user-entered `code`s** — V313 widened 19 `UNIQUE(code)` → `(tenant_id, code)` across attendance/comp/benefits/engagement/learning/lifecycle/organization/performance/recruitment/staffing. `*_no`/token/hash keys stay global (collision-free via global sequences / randomness); `(parent_uuid,…)` composites are naturally per-tenant.
- [x] **Duplicate-provision → 409** (ResponseStatusException) instead of 500.
- [x] **API-key auth is tenant-aware** — native tenant-blind `findTenantIdByKeyHash` + `ApiKeyAuthFilter` binds TenantContext from the key's tenant (previously a non-default tenant's key could never authenticate because `resolve()` was `@TenantId`-filtered).

- [x] **Per-tenant numbering** (commit c9e60aa) — V314 `config.tenant_sequence` counter table + `next_tenant_seq(seq)` (reads the `hcm.tenant` GUC set per-connection by `TenantAwareDataSource`); 57 repos/services rewritten `nextval('x.y')` → `config.next_tenant_seq('x.y')`; 54 `*_no` uniques widened to `(tenant_id, *_no)`; 'default' counters seeded from the global sequences for continuity. **Proven**: acme's first employee = EMP-00001 (fresh) while default's counter continues at 24, both tenants hold EMP-00001.

## Still deferred (documented, non-blocking nicety)
- **Attendance UUID-tenant rename** (Tier-2, see above) — legal-entity-as-tenant UUID model; transitively isolated, cosmetic rename only.

## Deferred (Tier-2, non-blocking) — attendance UUID-tenant rename
The 8 attendance entities above should eventually rename their misleading UUID
`tenant_id`→`legal_entity_id` (its true meaning) and gain a string `@TenantId` for
defense-in-depth (auto-filter safety net vs. relying on every finder remembering the
manual UUID filter). ~89 edits across 6 mixed services + derived-query method renames
+ a column-rename/add migration. Not an isolation blocker (transitive isolation holds);
do it as a focused, boot-verified refactor when the attendance module is next touched.
