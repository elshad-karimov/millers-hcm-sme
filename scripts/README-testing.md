# Millers HCM — Testing & Regression Gates

Three layers guard every change. Layers 1–2 are automated in CI on every push;
layer 3 is the local / pre-merge integration gate (it needs the running stack).

| Layer | What it proves | How to run | Automated? |
|-------|----------------|------------|------------|
| **1. Unit + payroll math** | Service logic + AZ 2026 payroll numbers (income tax, DSMF, MMI, unemployment, OT, rounding) are correct | `mvn test` | ✅ CI (`.github/workflows/ci.yml`, every push/PR) |
| **2. Static guardrails** | No hard-delete of payroll data; reminders on payroll/employee/tenant changes | `.claude/hooks/enforce-gate.sh` | ✅ Claude Code `Stop` hook (`.claude/settings.json`) |
| **3. API confidentiality invariants** | Auth, self-service IDOR, manager hierarchy scoping, salary masking, no-payroll-exec — against the live API | `python3 scripts/uat_smoke.py` | ⛳ Local / pre-merge (needs backend + Keycloak + Vite + seed) |

## 1. Unit + payroll-math suite — `mvn test`

Pure JUnit + AssertJ, no Spring context boot, no live DB. Includes the payroll
regression pack (`StatutoryCalculatorTest`) that pins the AZ 2026 statutory math
against `prd/HCM_09_Payroll_Multi_Tenant_PRD/fixtures/`. This is the per-push CI
gate — `ci.yml` runs it with a throwaway Postgres service on JDK 21 (temurin).

```bash
JAVA_HOME=/usr/local/opt/openjdk mvn -q test          # full suite
JAVA_HOME=/usr/local/opt/openjdk mvn -q -Dtest=StatutoryCalculatorTest test   # payroll only
```

## 2. Static guardrails — the `Stop` hook

`.claude/hooks/enforce-gate.sh` runs sub-second on every Claude Code turn. It
**blocks** (exit 2) on the one unambiguous rule — an added `DELETE FROM <payroll
table>` in a `.sql`/`.java` diff (GLOBAL RULE 12: payroll is reversed/adjusted,
never physically deleted) — and otherwise prints a non-blocking reminder to run
the gate when payroll/employee/tenant code changed. Wired via `.claude/settings.json`.

## 3. API confidentiality invariants — `scripts/uat_smoke.py`

Black-box suite that logs in as each seeded persona and asserts the HCM security
invariants over real HTTP. **Prerequisites** (dev): backend `:8082`, Keycloak
`:8080`, and the **Vite proxy `:5180`** all up, and the UAT seed applied:

```bash
PGPASSWORD=hcm psql -h localhost -p 5433 -U hcm -d hcm -f scripts/seed-uat.sql   # once
python3 scripts/uat_smoke.py                                                     # exit = #failures
```

Persona logins are in [`UAT_LOGINS.md`](UAT_LOGINS.md). Passwords are read from
`keycloak/realm-millers-hcm.json` at runtime — never passed on the command line.

**Issuer gotcha:** the backend only accepts tokens whose `iss` matches its
configured issuer (`http://localhost:5180/...` in dev — the Vite proxy). The
suite mints tokens there by default. All wiring is env-overridable, so it can
point at any stack:

```bash
HCM_OIDC_ISSUER=http://keycloak:8080/realms/millers-hcm \
HCM_API_BASE=http://backend:8082 \
python3 scripts/uat_smoke.py
```

### Running layer 3 in CI (optional follow-up)

Not yet added to `ci.yml` because it needs the full stack (Keycloak with the
realm imported + the booted app + the seed), which is heavier and flakier than
the layer-1 gate. `docker-compose.yml` has every service needed. To wire it: a
job that (a) boots Postgres + Keycloak (realm import) + the app with
`HCM_OIDC_ISSUER` pointed at the Keycloak service, (b) applies `seed-uat.sql`,
(c) runs `python3 scripts/uat_smoke.py`. Validate it on a real Actions run
before making it a required check.
