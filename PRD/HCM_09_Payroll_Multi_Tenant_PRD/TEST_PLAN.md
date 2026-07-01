# HCM_09 Payroll Multi-Tenant — Test Plan (M349–M358)

A manual + API test plan covering every feature delivered in this PRD. Execute top to
bottom for a clean run, or jump to a milestone suite. Each case lists **Steps**,
**Expected**, and (where relevant) the **Endpoint** and role required.

---

## 0. Test environment & prerequisites

| Item | Value |
|------|-------|
| Backend | `http://localhost:8082` (run `mvn -q -DskipTests spring-boot:run`, JDK 25) |
| SPA | `http://localhost:5180` (`npm run dev` in `web/`, Node 20) |
| DB | PostgreSQL `localhost:5433`, db=`hcm`, user/pass=`hcm/hcm` |
| Keycloak | realm `millers-hcm` (resolved via the Vite dev server on :5180) |
| Migrations | V193–V199 must be applied (`SELECT version FROM core_hr.flyway_schema_history WHERE version>='193'`) |

### 0.1 Roles used in this plan
`SYSTEM_ADMIN`, `HR_ADMIN`, `PAYROLL_SPECIALIST`, `HR_SPECIALIST`, `AUDITOR`, `EMPLOYEE`, `DEPARTMENT_MANAGER`.

Payroll authorization constants:
- `READ_PAYROLL` = SYSTEM_ADMIN, HR_ADMIN, HR_SPECIALIST, PAYROLL_SPECIALIST, AUDITOR, FINANCE
- `WRITE_PAYROLL` = SYSTEM_ADMIN, HR_ADMIN, PAYROLL_SPECIALIST
- `WRITE_HR_ADMIN_ONLY` = SYSTEM_ADMIN, HR_ADMIN (used for advance approve/reject + loan write-off)

### 0.2 Getting a token (API testing)
```bash
# Password grant against Keycloak (substitute a real user/password from the realm)
TOKEN=$(curl -s -X POST \
  'http://localhost:5180/realms/millers-hcm/protocol/openid-connect/token' \
  -d 'grant_type=password' -d 'client_id=hcm-web' \
  -d 'username=<hr_admin_user>' -d 'password=<pw>' | jq -r .access_token)

# then, e.g.
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/payroll/components | jq
```
> Every payroll endpoint returns **401** with no token and **403** when the caller's
> role isn't permitted — both are valid test signals.

### 0.3 Baseline test data (create once)
1. A test employee **EMP-T1** (core HR), ACTIVE, with an OrgUnit that has a `cost_center_code`.
2. An **active compensation** row for EMP-T1: `monthly_base_salary = 2000.00`.
3. An **active bank account** for EMP-T1 with an IBAN (for payslip IBAN-masking test).
4. An **APPROVED timesheet** for EMP-T1 for the test period (needed for a run to include them).
5. A user mapped to EMP-T1 holding role `EMPLOYEE` (for self-service tests).

---

## Suite M349 — Salary Component Catalog + engine integration

### TC-349-01 Create an EARNING component (taxable)
- **Endpoint:** `POST /api/payroll/components` (WRITE_PAYROLL)
- **Body:** `{ "code":"MEAL", "name":"Meal Allowance", "kind":"EARNING", "calculationMethod":"FIXED_AMOUNT", "defaultAmount":50.00, "isTaxable":true, "contributionExempt":false }`
- **Expected:** 201; component listed in `GET /api/payroll/components`.

### TC-349-02 Create a non-taxable, contribution-included EARNING
- **Body:** `{ "code":"TRANSPORT", ... "defaultAmount":150.00, "isTaxable":false, "contributionExempt":false }`
- **Expected:** 201. (SPA: in the create drawer, once **Is Taxable = ON** the **Contribution Exempt** switch is disabled — confirm.)

### TC-349-03 Statutory component is protected
- **Steps:** `DELETE /api/payroll/components/{id}` for a seeded statutory component (INCOME_TAX, DSMF_EMPLOYEE, MMI_EMPLOYEE, UNEMPLOYMENT_EMPLOYEE, DSMF_EMPLOYER).
- **Expected:** 400 `COMPONENT_IS_STATUTORY`. There should be **5** statutory components seeded.

### TC-349-04 Cannot delete a component that is in use
- **Steps:** assign MEAL to EMP-T1 (TC-349-05), then `DELETE` MEAL.
- **Expected:** 400 `COMPONENT_IN_USE`.

### TC-349-05 Assign components to an employee
- **Endpoint:** `POST /api/payroll/employees/{EMP-T1}/component-assignments`
- **Body:** `{ "componentId":"<MEAL id>", "amountOverride":50.00, "effectiveFrom":"2026-07-01" }` and again for TRANSPORT (150.00).
- **Expected:** 201 each; `GET .../component-assignments` returns both, open-ended (`effectiveTo` null).

### TC-349-06 Re-assigning the same component auto-closes the prior
- **Steps:** POST a new MEAL assignment effective `2026-08-01`.
- **Expected:** the prior MEAL assignment's `effectiveTo` becomes `2026-07-31`; only one is active on any date.

### TC-349-07 HR_SPECIALIST sees masked amounts (salary confidentiality)
- **Steps:** as **HR_SPECIALIST**, `GET /api/payroll/components` and `.../component-assignments`.
- **Expected:** `defaultAmount`, `percentage`, `amountOverride` return **null** (masked). As HR_ADMIN/PAYROLL_SPECIALIST they return the real values.

### TC-349-08 ★ Engine taxability math (fixture M349-S1) — BLOCKING
- **Setup:** EMP-T1 base 2000, MEAL taxable 50, TRANSPORT non-taxable/contributable 150, approved timesheet for July 2026.
- **Steps:** create a REGULAR July-2026 run, `POST /runs/{id}/calculate`, open the result.
- **Expected (bases):**
  - Taxable gross (income-tax base) = **2050.00** (base + MEAL).
  - DSMF/MMI/unemployment base = **2200.00** (taxable gross + TRANSPORT).
  - Income tax computed on 2050 with the AZ 200 AZN monthly exemption.
  - TRANSPORT (150) added to **net after** statutory deductions.
  - `payroll_result_component` rows recorded for MEAL and TRANSPORT; `calculation_details` JSON contains a `components` section.

### TC-349-09 DEDUCTION component subtracts after statutory (fixture M349-S3)
- **Setup:** assign a DEDUCTION component (e.g. EQUIPMENT_RECOVER 200) to an employee, calculate.
- **Expected:** 200 is subtracted from **net after** all statutory; the DSMF base is **not** reduced by it.

### TC-349-10 Contribution-exempt earning (fixture M349-S2)
- **Setup:** a component `isTaxable=false, contributionExempt=true` (e.g. HOUSING 500).
- **Expected:** excluded from both the income-tax base **and** the DSMF/MMI base; added to net after statutory.

---

## Suite M350 — Employee holds + off-cycle runs + pre-flight

### TC-350-01 Pre-flight checklist
- **Endpoint:** `GET /api/payroll/runs/{id}/pre-flight` (READ_PAYROLL)
- **Expected:** JSON with `checklist.{noCompensation, noTimesheet, onHold, pendingAdvances, retroactiveSalaryChange}` and `summary.totalIssues`. An employee with no active compensation appears under `noCompensation`; one with no approved timesheet under `noTimesheet`.

### TC-350-02 Place an employee on hold
- **Endpoint:** `POST /api/payroll/runs/{id}/hold-employees` body `{ "holds":[{"employeeId":"<EMP-T1>","reason":"Contract dispute"}] }`
- **Expected:** hold recorded; EMP-T1 now appears under pre-flight `onHold`.

### TC-350-03 Held employee is skipped by calculation
- **Steps:** calculate the run while EMP-T1 is on hold.
- **Expected:** no `payroll_result` for EMP-T1; the run's totals exclude them.

### TC-350-04 Release hold and recalculate
- **Endpoint:** `POST /api/payroll/runs/{id}/hold-employees/{EMP-T1}/release`, then recalculate.
- **Expected:** EMP-T1 is now included.

### TC-350-05 Off-cycle run requires employee list
- **Endpoint:** `POST /api/payroll/runs` with `runType:"OFF_CYCLE"` and empty/absent `employeeIds`.
- **Expected:** 400 `OFF_CYCLE_REQUIRES_EMPLOYEES`.

### TC-350-06 Off-cycle run for a subset
- **Steps:** create OFF_CYCLE run for July 2026 with `employeeIds:[EMP-T1]`, `description:"July spot bonus"`, calculate.
- **Expected:** only EMP-T1 is processed; the run coexists with the REGULAR July run (no unique-constraint conflict); the runs list shows a `Run Type` column with an `OFF_CYCLE` tag.

---

## Suite M351 — Salary advances

### TC-351-01 Advance within the 50% ceiling (fixture M351-S1)
- **Endpoint:** `POST /api/payroll/advances` (EMPLOYEE for self, or HR)
- **Body (HR):** `{ "employeeId":"<EMP-T1>", "requestedAmount":1000.00, "reason":"medical" }` (base 2000 → ceiling 1000)
- **Expected:** 201, status `PENDING`.

### TC-351-02 Advance over the ceiling is rejected (fixture M351-S2)
- **Steps:** request 1001 for an employee with base 2000 (ceiling 1000).
- **Expected:** 400 `ADVANCE_LIMIT_EXCEEDED`.

### TC-351-03 One-at-a-time (fixture M351-S3)
- **Steps:** with a PENDING or APPROVED advance already present, submit another.
- **Expected:** 400 `ADVANCE_PENDING_EXISTS`.

### TC-351-04 Employee self-service can only request for themselves
- **Steps:** as **EMPLOYEE** (mapped to EMP-T1), `POST /api/payroll/advances` with a body `employeeId` of a **different** employee.
- **Expected:** the advance is created for **EMP-T1** (the body's employeeId is ignored — derived from the token). Confirm the persisted row's employee is EMP-T1.

### TC-351-05 Approve authority is HR_ADMIN only
- **Endpoint:** `POST /api/payroll/advances/{id}/approve`
- **Expected:** **403** for PAYROLL_SPECIALIST; **200** for HR_ADMIN with `{ "approvedAmount":800.00, "repaymentYear":2026, "repaymentMonth":8 }`. Same for `.../reject` (HR_ADMIN only).

### TC-351-06 Approved advance is recovered in the repayment period (fixture M351-S4)
- **Steps:** approve 800 for repayment 2026-08; calculate the **August** run for EMP-T1.
- **Expected:** net reduced by **800**; a `SALARY_ADVANCE` payroll_deduction row exists for Aug with `source_advance_id`; advance status → `DEDUCTED`.

### TC-351-07 Cancel before deduction; cannot cancel after
- **Steps:** cancel an APPROVED (not-yet-deducted) advance → status `CANCELLED`. Then try to cancel a `DEDUCTED` advance.
- **Expected:** second attempt → 400 `ADVANCE_ALREADY_DEDUCTED`.

### TC-351-08 Employee cannot cancel another employee's advance (security)
- **Steps:** as EMPLOYEE (EMP-T1), `POST /api/payroll/advances/{id}/cancel` for an advance belonging to a **different** employee.
- **Expected:** **403 Forbidden** (ownership verified).

### TC-351-09 Termination recovery + write-off (fixtures M351-S5/S6)
- **S5:** employee has APPROVED advance 800, terminal gross payout 1500 → final net **700**, advance `DEDUCTED`.
- **S6:** advance 800, payout 500 → final net **0**, write-off **300** recorded (audit-logged, requires HR acknowledgement).

---

## Suite M352 — Payroll loans

### TC-352-01 Create a loan; term is derived (fixture M352-S1)
- **Endpoint:** `POST /api/payroll/loans` (WRITE_PAYROLL)
- **Body:** `{ "employeeId":"<EMP-T1>", "principalAmount":3000.00, "monthlyInstallment":500.00, "startDeductionYear":2026, "startDeductionMonth":7 }`
- **Expected:** 201; `termMonths = 6`; status `ACTIVE`; outstanding 3000.

### TC-352-02 Installments deduct each period; partial last (fixture M352-S2)
- **Steps:** create a loan principal 1100, installment 300; calculate months Jul–Oct.
- **Expected:** deductions 300, 300, 300, **200** (min(installment, outstanding)); status → `FULLY_REPAID` after the 4th; outstanding 0.

### TC-352-03 ★ Idempotent recalculation — no double-deduct (fixture M352-S3) — BLOCKING
- **Steps:** calculate the July run (loan installment applied), then **recalculate** the same July run.
- **Expected:** exactly **one** LOAN_INSTALLMENT deduction row for July; the amount deducted from net is identical on both calculations; the outstanding balance is reduced **once**. (This proves the two-phase ensure-then-sum hook.)

### TC-352-04 Multi-month loan keeps deducting
- **Steps:** with a 6-month loan, calculate Jul, then Aug, then Sep runs.
- **Expected:** an installment is deducted in **each** month (not just the first) — confirms per-(loan,period) idempotency.

### TC-352-05 Cancel only when nothing repaid
- **Steps:** `POST /api/payroll/loans/{id}/cancel` on a loan with `amountRepaid=0` → CANCELLED. Try on a loan with repayments → rejected.

### TC-352-06 Write-off is HR_ADMIN only
- **Endpoint:** `POST /api/payroll/loans/{id}/write-off` body `{ "reason":"hardship" }`
- **Expected:** **403** for PAYROLL_SPECIALIST; **200** + status `WRITTEN_OFF` + audit row for HR_ADMIN.

### TC-352-07 Termination recovery (fixtures M352-S4/S5)
- **S4:** outstanding 2000, payout 2500 → net 500, loan `FULLY_REPAID`.
- **S5:** outstanding 3000, payout 1000 → net 0, write-off 2000, loan `WRITTEN_OFF`.

---

## Suite M353 — Variance + YTD

### TC-353-01 Period-over-period variance
- **Endpoint:** `GET /api/payroll/reports/variance?currentRunId=<Jun>&priorRunId=<May>` (both REGULAR)
- **Expected:** per-employee `priorGross, currentGross, grossDelta, grossDeltaPct, netDelta, flags`; summary counts. New employee → `NEW_EMPLOYEE` + prior 0; missing this period → `EMPLOYEE_ABSENT` + current 0; >10% gross change counted in `highVarianceCount`.

### TC-353-02 Variance rejects incompatible runs
- **Steps:** pass an OFF_CYCLE run id as one of the two.
- **Expected:** 400 (both must be REGULAR).

### TC-353-03 YTD summary
- **Endpoint:** `GET /api/payroll/reports/ytd?year=2026[&employeeId=<EMP-T1>]`
- **Expected:** per-employee totals aggregated across **PAID REGULAR** runs only; `monthsCount` reflects the number of paid months; OFF_CYCLE runs excluded; no paid runs → zeros, not an error.

---

## Suite M354 — Year-end + AZ annual tax certificate

### TC-354-01 Generate annual summaries
- **Endpoint:** `POST /api/payroll/year-end/generate-summary?year=2026`
- **Expected:** an `annual_payroll_summary` row per employee with ≥1 PAID REGULAR run in 2026; re-running replaces (idempotent, unique per employee+year).

### TC-354-02 Generate certificates + content
- **Endpoint:** `POST /api/payroll/year-end/certificates/generate?year=2026`, then `GET /api/payroll/year-end/certificates?year=2026`
- **Expected:** each cert has `annualGross`, `exemptAmount = 200 × monthsCount`, `taxableGross`, `totalTaxWithheld`, status `GENERATED`; **national_id masked (last 4)** in the list response.

### TC-354-03 Download certificate PDF
- **Endpoint:** `GET /api/payroll/year-end/certificates/{id}/download`
- **Expected:** a PDF streams; opening it shows employee name, employee VÖEN (full national_id **inside the document**), employer VÖEN, annual gross, exempt, taxable, tax withheld, period 2026-01-01…2026-12-31.

### TC-354-04 Employee downloads own certificate
- **Endpoint:** `GET /api/payroll/employees/me/year-end/certificate?year=2026` as EMPLOYEE
- **Expected:** returns/download the caller's own certificate only; no way to fetch another employee's.

---

## Suite M355 — Cost center allocation + GL journal

### TC-355-01 Allocations must sum to 100%
- **Endpoint:** `POST /api/payroll/employees/{EMP-T1}/cost-allocations`
- **Body (bad):** `{ "allocations":[{"costCenterCode":"CC-ENG","allocationPct":70}], "effectiveFrom":"2026-07-01" }`
- **Expected:** 400 `ALLOCATION_SUM_INVALID`.
- **Body (good):** ENG 60 + PRODUCT 40 → 201.

### TC-355-02 Default cost center fallback
- **Steps:** for an employee with **no** allocation, generate the GL journal.
- **Expected:** 100% falls back to the employee's OrgUnit `cost_center_code`, or `DEFAULT` if none.

### TC-355-03 GL journal generated on APPROVED and is balanced
- **Steps:** take a run to APPROVED, then `POST /api/payroll/runs/{id}/gl-journal/generate`; `GET .../gl-journal`.
- **Expected:** journal with debit lines (salary expense split by cost center) and credit lines (net pay + income tax + DSMF/MMI/unemployment payable); **totalDebit == totalCredit** (balanced indicator green in the SPA GL tab).

### TC-355-04 Generate only on APPROVED/PAID
- **Steps:** attempt generation on a DRAFT run.
- **Expected:** 400 `RUN_STATUS_INVALID`.

### TC-355-05 Missing GL mapping is non-fatal
- **Steps:** generate with no `gl_account_mapping` rows configured.
- **Expected:** journal still generates using a fallback account (e.g. `9999`), with a logged warning — it does **not** fail the whole journal.

### TC-355-06 POSTED journal cannot be overwritten
- **Steps:** mark a journal POSTED (or simulate), then re-generate.
- **Expected:** 400 `JOURNAL_ALREADY_POSTED` (accounting record preserved).

### TC-355-07 CSV export
- **Endpoint:** `GET /api/payroll/runs/{id}/gl-journal/export`
- **Expected:** a CSV of all journal lines downloads.

---

## Suite M356 — PDF payslips

### TC-356-01 Payslips generate only after PAID
- **Steps:** `POST /api/payroll/runs/{id}/generate-payslips` on a run that is **not** PAID.
- **Expected:** 400. On a PAID run → `{ "generated": N }` and PDFs stored in MinIO.

### TC-356-02 Payslip content + IBAN masking
- **Steps:** download a payslip (`GET /api/payroll/runs/{id}/payslips/{employeeId}/download`).
- **Expected:** header, pay period, payslip no, earnings table (base + each component + bonuses), statutory block (income tax, DSMF, MMI, unemployment), DEDUCTION components + advance/loan deductions, net pay, employer contributions (informational), **IBAN masked to last 4** (e.g. `****6789`).

### TC-356-03 Employee downloads own payslips only
- **Endpoint:** `GET /api/payroll/employees/me/payslips` then `.../me/payslips/{runId}/download` (EMPLOYEE)
- **Expected:** lists/downloads own payslips only. Attempting another employee's runId → **403**.

### TC-356-04 Email delivery
- **Endpoint:** `POST /api/payroll/runs/{id}/send-payslips`
- **Expected:** `{ "sent": N, "failed": M }`; employees without a work email are skipped (counted in `failed`), not fatal. Check MailHog for delivered messages with the PDF attached.

---

## Suite M357 — Control board

### TC-357-01 Dashboard
- **Endpoint:** `GET /api/payroll/control-board` (READ_PAYROLL)
- **Expected:** current run status, headcount, total gross/net/tax, month-over-month gross variance %, outstanding loan balance, pending advance count. With no open run, a graceful "no run" state (SPA shows a Create Run empty state).

---

## Suite M358 — Reports suite

### TC-358-01 Period summary
- `GET /api/payroll/reports/period-summary?runId=<id>` → per-run totals + per-employee breakdown with component lines.

### TC-358-02 Employer cost
- `GET /api/payroll/reports/employer-cost?runId=<id>` → per-employee gross + employer DSMF + employer MMI + employer unemployment.

### TC-358-03 Loan & advance status
- `GET /api/payroll/reports/loan-advance-status` → all ACTIVE loans (principal, installment, outstanding, expected payoff) + PENDING/APPROVED advances.

### TC-358-04 Bank reconciliation
- `GET /api/payroll/reports/bank-reconciliation?runId=<id>` → payroll net total vs bank-file total + delta + balanced flag. With no bank file, the response surfaces that clearly (no crash).

---

## Cross-cutting suites

### X-1 Authorization matrix (run each as several roles)
| Action | Expect ALLOW | Expect DENY (403) |
|--------|-------------|-------------------|
| Read components/reports | HR_ADMIN, PAYROLL_SPECIALIST, HR_SPECIALIST, AUDITOR | EMPLOYEE |
| Write component / assignment | HR_ADMIN, PAYROLL_SPECIALIST | HR_SPECIALIST, EMPLOYEE |
| Advance **approve/reject** | HR_ADMIN, SYSTEM_ADMIN | **PAYROLL_SPECIALIST**, HR_SPECIALIST, EMPLOYEE |
| Loan **write-off** | HR_ADMIN, SYSTEM_ADMIN | **PAYROLL_SPECIALIST**, EMPLOYEE |
| Own payslip / advance / cert | EMPLOYEE (own) | EMPLOYEE (another's) → 403 |
| Any payroll endpoint, no token | — | 401 |

### X-2 Salary masking
As HR_SPECIALIST, confirm all salary **amount** fields (`defaultAmount`, `percentage`, `amountOverride`) are null across component + assignment responses; as HR_ADMIN they are populated.

### X-3 Audit trail
After exercising the suites, verify audit rows exist (audit-log browser or `audit.audit_log`) for: component created/updated/deactivated, assignment created/closed, advance requested/approved/rejected/deducted/cancelled, loan created/cancelled/written-off/status-changed, hold set/released, GL journal generated, certificate generated, payslip generated. Each carries actor + timestamp.

### X-4 No physical deletes of payroll records
Confirm advances/loans move by **status transition** only (PENDING→…→DEDUCTED/CANCELLED, ACTIVE→FULLY_REPAID/WRITTEN_OFF) — rows are never physically removed. GL journals: a POSTED journal is never overwritten.

### X-5 Tenant consistency
All new payroll rows carry `tenant_id = 'default'`; period-scoped advance/loan queries include the tenant filter.

---

## E2E-1 — Golden-path monthly payroll cycle

1. Ensure EMP-T1 has base 2000, MEAL(50) + TRANSPORT(150) assignments, an approved July timesheet, an APPROVED advance (800, repay Aug), and an ACTIVE loan (installment 500, start Jul).
2. **Create** REGULAR run July 2026 → **Pre-flight** (fix any warnings) → **Calculate**.
3. Verify EMP-T1 result: taxable gross 2050, DSMF base 2200, TRANSPORT added post-statutory, **loan 500 deducted**, net floored at ≥ 0.
4. **Recalculate** → confirm identical net (idempotent; loan not double-deducted).
5. **Submit → Approve** (3-step PAYROLL_APPROVAL: HR_SPECIALIST → HR_ADMIN → SYSTEM_ADMIN).
6. On APPROVED: **generate GL journal** → debits == credits.
7. **Mark PAID** → **generate payslips** → **email payslips** (check MailHog).
8. **Create August run** → confirm the **advance 800** is now deducted, advance status `DEDUCTED`.
9. **Year-end** (after a full year of PAID runs or a representative subset): generate summaries + certificates; download a cert PDF.
10. **Reports:** period-summary, employer-cost, variance (Jul vs Aug), YTD, loan-advance-status, bank-reconciliation.

**Pass criteria:** every step behaves as expected, the run reaches PAID with a balanced GL journal, payslips are downloadable with a masked IBAN, and recalculation is deterministic.

---

## Sign-off

| Suite | Result | Tester | Date |
|-------|--------|--------|------|
| M349 components + engine | ☐ | | |
| M350 holds + off-cycle | ☐ | | |
| M351 advances | ☐ | | |
| M352 loans | ☐ | | |
| M353 variance/YTD | ☐ | | |
| M354 year-end/cert | ☐ | | |
| M355 cost center/GL | ☐ | | |
| M356 payslips | ☐ | | |
| M357 control board | ☐ | | |
| M358 reports | ☐ | | |
| X-1..X-5 cross-cutting | ☐ | | |
| E2E-1 golden path | ☐ | | |

**SHIP / DON'T SHIP:** ____________   Notes: ____________________________________
