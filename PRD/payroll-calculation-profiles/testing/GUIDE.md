# How to test the payroll calculation profiles

Three levels, cheapest first. Level 1 needs nothing but the repo and proves the
arithmetic. Level 2 needs Docker and proves the schema. Level 3 needs the running
stack and proves the wiring.

**Nothing described here can pay anyone.** `PayrollEngine` is untouched, every
endpoint is a GET, and no payroll run, result, payslip or GL posting is created.

---

## Level 1 — the arithmetic (30 seconds, no setup)

```bash
mvn -o test -Dtest='az.millers.hcm.payroll.profile.*Test'
```

84 tests. Every expected value comes from
`fixtures/july-2026-worked-examples.json`, which is the company's own figures —
not values chosen to make the code pass.

Run the whole suite to confirm nothing else moved:

```bash
mvn -o test
```

833 tests, including slice 3's 17. Those 17 matter: the statutory deduction
logic was extracted so both calculators share one copy, and if that extraction
had changed a single figure they would fail.

### What to look at, not just that it is green

The tests that assert a **refusal** are the ones worth reading. Open
[ProfilePayCalculatorTest.java](../../../src/test/java/az/millers/hcm/payroll/profile/ProfilePayCalculatorTest.java)
and find:

| Test | What it proves |
|---|---|
| `excessWithNightHoursRefuses` | Q1 unanswered ⇒ monthly excess pays nothing and says why |
| `rotationSettlementRefusesWithoutMultiplier` | Q2 unanswered ⇒ no settlement is paid |
| `rotationSettlementBothReadings` | the two readings of Q2 differ by 855.98 AZN on 60 hours |
| `sameHourDifferentProfile` | one offshore hour = 3,867.50 on rotation, 21.02 on an onshore contract |
| `derivedOffshoreNegative` | onshore + sick over the norm blocks instead of paying a negative |
| `profileRequired` | no profile is a hard failure, never a default |

To see a refusal turn into a payment, set the flag the way an answer would:

```java
CalculationProfile p = onshoreRandomOffshore();
p.setNightHoursSeparateFromBase(true);   // Q1 answered
```

`excessWithNightHoursOnceAnswered` already does this and reproduces the
spreadsheet's `12 + 160 + 24 − 184 = 12`.

---

## Level 2 — the schema (2 minutes)

Proves all 324 migrations apply to an empty database and that V328's constraints
reject what they should. **Do not point this at `sme-postgres`** — it creates
schemas and tables.

Docker's VM disk is currently full on this machine, so the quickest route is the
Homebrew Postgres 16 already installed:

```bash
initdb -D /tmp/hcmpg/data -U postgres --auth=trust --locale=C -E UTF8
```

```bash
pg_ctl -D /tmp/hcmpg/data -o "-p 55999 -k /tmp/hcmpg -c listen_addresses=''" -l /tmp/hcmpg/pg.log start
```

Create the schemas Flyway would create (`spring.flyway.schemas` in
`application.yml`) plus the `pgcrypto` extension, then apply every migration in
version order. Then check the seeds:

```bash
psql -h /tmp/hcmpg -p 55999 -U postgres -d hcm -c "SELECT code, excess_method, excess_multiplier, night_hours_separate_from_base, accumulator_categories FROM payroll.calculation_profile ORDER BY code;"
```

Expect four rows, with `excess_multiplier` NULL on `OFFSHORE_ROTATION` and
`night_hours_separate_from_base` NULL on all four. **Those NULLs are the
feature**, not missing seed data — they are what makes the engine refuse.
`accumulator_categories` should read `OFFSHORE_HOURS,ONSHORE_HOURS` on the
rotation profile and be empty on the rest.

Two rows that must be rejected:

```bash
psql -h /tmp/hcmpg -p 55999 -U postgres -d hcm -c "INSERT INTO payroll.calculation_profile (code,name,offshore_salary_mode,offshore_multiplier,excess_method) VALUES ('BAD','bad','HOURLY',1.75,'BALANCING_PERIOD');"
```

Must fail on `ck_calc_profile_scheme` — a balancing profile with no scheme could
never settle. A settled accumulator missing its figures must fail on
`ck_excess_settled_complete`.

Stop and discard the cluster when you are done:

```bash
pg_ctl -D /tmp/hcmpg/data stop
```

> If you would rather use Docker, `docker rm v327c` clears a container left
> behind by an earlier run and `docker system prune` frees the VM disk.

## Level 3 — the running application

### 3.1 Start the stack

```bash
docker compose up -d postgres keycloak redis
```

The backend runs on the host, not in Compose:

```bash
mvn -o spring-boot:run
```

It connects to `jdbc:postgresql://localhost:5533/hcm` and validates tokens
against `http://localhost:5181/realms/millers-hcm` — the SPA origin, proxied by
Vite. If you are not running `web/`, override the issuer so JWKS resolves
directly:

```bash
HCM_OIDC_ISSUER=http://localhost:8190/realms/millers-hcm mvn -o spring-boot:run
```

### 3.2 Get a token

The preview is restricted to `PAYROLL_SPECIALIST`, `COMPENSATION_MANAGER`,
`HR_ADMIN` and `SYSTEM_ADMIN`. The realm's `admin` user has the last two.

```bash
TOKEN=$(curl -s -X POST "http://localhost:8190/realms/millers-hcm/protocol/openid-connect/token" -d "client_id=hcm-web" -d "grant_type=password" -d "username=admin" -d "password=Admin#Pass123!" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
```

### 3.3 Seed the scenarios

There are **no write endpoints** for profile assignment, MEWA rules or the
accumulator — see §6 — so setup is SQL:

```bash
psql "postgresql://hcm:hcm@localhost:5533/hcm" -f prd/payroll-calculation-profiles/testing/seed-july-2026.sql
```

Five employees, one per scenario, with locked July 2026 timesheets and a
rotation ledger that settles in August. The teardown is commented at the bottom
of the file and removes exactly what it created.

### 3.4 Call it

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/payroll/calculation-profiles | python3 -m json.tool
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/payroll/calculation-profiles/preview/2026/7 | python3 -m json.tool
```

The other two:

```
GET /api/payroll/calculation-profiles/preview/2026/7/{employeeId}
GET /api/payroll/calculation-profiles/excess-ledger/{employeeId}
```

---

## 3.5 Configuring it through the API instead of SQL

*(This is the path a payroll user would take; the seed script in §3.3 is the shortcut for bulk test setup.)*

Everything the seed script does with `INSERT` can be done through the
configuration API, which is what a payroll user would actually use. All of it is
audit logged with a reason.

Put an employee on a profile:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"employeeId":"11111111-0000-0000-0000-000000000003","profileCode":"OFFSHORE_ROTATION","effectiveFrom":"2020-01-01","reason":"rotation contract"}' http://localhost:8083/api/payroll/calculation-profiles/admin/assignments
```

Set a MEWA rate:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"employeeId":"11111111-0000-0000-0000-000000000002","rate":0.30,"effectiveFrom":"2020-01-01","reason":"confirmed with payroll"}' http://localhost:8083/api/payroll/calculation-profiles/admin/mewa
```

Set norm hours:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"year":2026,"month":8,"normHours":168}' http://localhost:8083/api/payroll/calculation-profiles/admin/norm-hours
```

**Answer BLOCKERS Q2** — this is the whole design in one call, and the reason is
mandatory:

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"excessMultiplier":3.5,"reason":"Emil confirmed 2 x 1.75 against the April payroll"}' http://localhost:8083/api/payroll/calculation-profiles/admin/profiles/OFFSHORE_ROTATION
```

And take it back, which returns the engine to refusing:

```bash
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "http://localhost:8083/api/payroll/calculation-profiles/admin/profiles/OFFSHORE_ROTATION/settings/excessMultiplier?reason=withdrawn%20pending%20August"
```

### The accumulator now fills itself

Lock an attendance period and the balancing accumulators post automatically:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -d 'reason=July close' http://localhost:8083/api/timesheets/control/2026/7/lock
```

Then read the ledger. To re-post after a correction:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/payroll/calculation-profiles/admin/accumulator/post/2026/7
```

The response names every employee it posted and every one it could not, with the
reason — a period where half the profiles are unconfigured reports that rather
than looking successful.

Settle a closed period:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"employeeId":"11111111-0000-0000-0000-000000000003","periodYear":2026,"periodSeq":2,"paidInYear":2026,"paidInMonth":8,"note":"August settlement"}' http://localhost:8083/api/payroll/calculation-profiles/admin/accumulator/settle
```

Note there is no rate or multiplier in that request. Both are looked up, so a
settlement cannot be made against a hand-typed number.

---

## 4. What each scenario should produce

July 2026, norm **184 hours**. Every figure below is independently recomputed
and matches the company spreadsheets to the cent.

### TEST-ONS — `ONSHORE_FIXED`, base 3,000

A full norm month reproduces base salary exactly.

| Line | Amount |
|---|---|
| Onshore 184 h | 3,000.00 |
| **Gross** | **3,000.00** |

### TEST-ORO — `ONSHORE_RANDOM_OFFSHORE`, base 3,500

Hourly rate 19.0217391304. The excess is `96 + 136 − 184 = 48` hours, priced at
1.75 as offshore work rather than at the 2× overtime rate.

| Line | Amount |
|---|---|
| Offshore 96 h × 1.75 | 3,195.65 |
| Onshore 136 h | 2,586.96 |
| Excess 48 h × 1.75 | 1,597.83 |
| MEWA 30% of onshore | 776.09 |
| Meal 17 × 12 | 204.00 |
| Transport 17 × 10 | 170.00 |
| **Gross** | **8,530.52** |
| Income tax | 1,836.38 |
| SPF 3% | 253.37 |
| Unemployment 0.5% | 42.23 |
| Compulsory insurance | 162.23 |
| **Net** | **6,236.32** |

Contribution-exempt amount is 85.00 — meal is paid at 12 AZN/day with 5 exempt.

### TEST-ROT — `OFFSHORE_ROTATION`, base 2,210

| Line | Amount |
|---|---|
| Offshore rotation salary | 3,867.50 |

`2,210 × 1.75`, and **the 96 hours do not appear in the arithmetic** — they only
qualify the employee. Change the hours to 1 and the figure is unchanged; change
them to 0 and the line disappears with a warning about BLOCKERS Q3.

This is the case that explains January workbook row 9.

### TEST-ORN — `OFFSHORE_RANDOM_ONSHORE`, base 2,428

| Line | Amount |
|---|---|
| Offshore, derived `(184 − 8) × rate × 1.75` | 4,064.26 |
| Onshore 8 h | 105.57 |

Note the offshore quantity is **imputed from the norm**, not read from the
timesheet. Add recorded offshore hours and they are ignored, with a warning
naming BLOCKERS Q6.

### TEST-Q1 — the Q1 refusal

Same profile as TEST-ORO but with 24 offshore night hours. Expect:

* a **blocker** naming BLOCKERS Q1, and `isPayable: false`
* excess amount **0.00** — not a plausible-looking number
* a **warning** that offshore earnings fell back to treating night as a subset

The two readings differ by 24 hours here — roughly 800 AZN.

### The August rotation settlement — the Q2 refusal

```
GET /api/payroll/calculation-profiles/preview/2026/8/11111111-0000-0000-0000-000000000003
```

The ledger closes at **60 hours** (`+20 −28 +36 +32`). Expect a blocker naming
BLOCKERS Q2 and an excess of 0.00, because the rotation multiplier is
deliberately unset.

To see it pay, answer Q2 in configuration — not in code:

```sql
UPDATE payroll.calculation_profile
   SET excess_multiplier = 3.5     -- or 2.75; that is the open question
 WHERE code = 'OFFSHORE_ROTATION';
```

Re-run the same request. For TEST-ROT (base 2,210, August norm 168) the 60 hours
come to **2,762.50 at 3.50** or **2,170.54 at 2.75** — a 591.96 AZN gap on one
employee for one settlement. **That gap is exactly why it refuses by default.**

(The 3,994.57 / 3,138.59 pair in the fixtures is the same question at a 3,500
base and a 184 norm. The gap scales with salary; the question does not.)

### The ledger

```
GET /api/payroll/calculation-profiles/excess-ledger/11111111-0000-0000-0000-000000000003
```

Four months with actual, norm, delta and running balance. June's running balance
is **−8**: the shortfall genuinely offsets the surplus. A monthly overtime rule
would have paid May, July and August and ignored June, over-paying by 28 hours.

---

## 5. Negative tests worth running

| Try this | Expected |
|---|---|
| Preview June 2026 for TEST-ONS (DRAFT timesheet) | 400 — only approved or locked months are priced |
| Preview a month with no `period_norm_hours` row | 400 naming the missing norm |
| Delete TEST-ORO's `employee_calculation_profile` row and re-run the period | that employee appears under `skipped` with a reason; **the rest of the period still prices** |
| Call any endpoint with the `employee` or `manager` user | 403 — amounts are payroll-only |
| Call with no token | 401 |
| Add a `SOME_NEW_CATEGORY` month total | a warning that nothing prices it — never a silent zero |
| Give TEST-ONS offshore hours | a blocker: the profile has no offshore component |
| `PUT` a profile setting with no `reason` | 400 from bean validation |
| `PUT` `excessMultiplier: 50` | 400 — above 10x is a typo, not a policy |
| `POST` an assignment overlapping an existing closed range | 400 naming row order |
| `POST` a second assignment with an open end date | the previous one is closed the day before, not duplicated |
| Settle the same period twice | 400 — settling twice pays the same hours twice |
| Re-post a period after settling it | 400 — a settled payment is never rewritten |
| Call any `/admin/` endpoint as `HR_ADMIN` | 403 — reading a preview is a check, changing a multiplier moves money |

The `skipped` case matters: one employee's missing configuration must not hide
the other four. A period preview that silently dropped them would be worse than
one that failed outright.

---

## 6. What is verified, and what is not

Verified, by running it:

* **833 tests pass**, including slice 3's 17 unchanged.
* **All 324 migrations apply to an empty database** — both by hand and through
  Flyway on application startup.
* **The application boots** and serves these endpoints.
* **Every scenario in §4 reproduces through the live API**, to the cent.
* **Role enforcement holds at runtime**: `employee` and `hrspec` both get 403 on
  the preview *and* the admin API; no token gets 401.
* **Validation holds**: a settings change with no reason is 400, and a
  multiplier of 50 is 400.
* **The Q2 round trip works**: refuse → `PUT` the multiplier → it pays →
  `DELETE` it → it refuses again.
* **`seed-july-2026.sql` runs clean** and its teardown is surgical.

Not verified, and worth knowing:

1. **Nothing is wired to `PayrollEngine`.** The accumulator records hours owed
   and a settlement records an amount due, but no payroll run, payslip or GL
   posting is produced. That is the sign-off gate, not an oversight — see
   `../BLOCKERS.md`.

2. **No UI.** Deliberate: showing HR a calculated net that embeds unresolved
   questions would invite it to be trusted before it is signed off. Swagger UI
   and the configuration API mean payroll staff can still exercise it without
   SQL.

3. **Single-tenant only so far.** Every table carries `tenant_id` and the
   entities use `@TenantId`, but the live run above was against the `default`
   tenant. Cross-tenant isolation is enforced by the same mechanism as the rest
   of the system and is not separately proven for this feature.

4. **No load or volume testing.** The period preview loads every timesheet for
   a month; at a few hundred employees that is fine, and it has not been
   measured beyond that.

## 7. The one test that matters most

Take a real July 2026 payslip the company has already paid, put its employee on
the matching profile, enter its actual hours, and compare the preview line by
line against the spreadsheet.

The unit tests prove the engine reproduces the *formulas*. Only a real employee
proves it reproduces the *pay* — including whatever the spreadsheets do that
nobody has written down. That comparison, on several employees across all four
contract types, is what should gate sign-off; the six questions in
`../BLOCKERS.md` are what should gate the first live run.
