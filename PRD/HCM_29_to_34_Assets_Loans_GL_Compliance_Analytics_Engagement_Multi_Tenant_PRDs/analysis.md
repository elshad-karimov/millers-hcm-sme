# HCM_29–34 Bundle — Analysis & Milestone Plan

Analyzer + gap-checker both PASS (2026-07-05). Milestones **M456–M480**, migrations **V276–V291**. payroll_impact: false — CONFIRMED: no milestone executes/finalizes/reverses payroll; loans/asset-deductions CREATE PayrollDeduction rows (existing HCM_09 machinery: PayrollLoanService, SalaryAdvanceService, PayrollAdvanceLoanDeductionService hook, GLJournalService V198) and GL work is config/approval/reporting only.

## Coverage

| Module | Built already | % |
|---|---|---|
| 29 Assets | M72 employee_asset+events, M124 checkout/return, M128 depreciation, M301/302 onboarding provisioning, offboarding return M305 | ~60% |
| 30 Loans/Advances | M351 SalaryAdvance V195, M352 PayrollLoan V196 + PayrollDeduction standing instruction (source_loan_id) + two-phase recalc-safe hook | ~35% |
| 31 GL | M355 V198: GLAccountMapping (+GLAccountMappingsPage), GLJournal+lines, CostCenterAllocation, generate/export CSV | ~65% |
| 32 Compliance | M114 audit browser, M294 retention, M441 doc expiry→renewal | ~25% |
| 33 Analytics | M119 report builder V80, M161-165 dashboards V118, V24 scheduler, module dashboards | ~55% |
| 34 Engagement | M116 V78 surveys/eNPS/anonymous/aggregator/campaign scheduler, EmployeeReward | ~40% |

## Milestones

### Phase A — Assets (M456–M459)
- **M456 (V276, S)** asset_category tenant catalog (code, name, default depreciation fields?, active; seed defaults matching existing AssetType enum values) + EmployeeAsset.category_id FK (nullable, enum stays for compat) + CRUD /api/assets/categories (ASSET/HR admin) + admin page.
- **M457 (V277, M)** asset transfer: asset_transfer (asset FK, from_employee, to_employee, reason, status PENDING/APPROVED/COMPLETED/REJECTED, requested_by, approved_by/at) — approval via workflow or simple HR approve (pick simpler, audit); on COMPLETE reassign the asset + event history rows both sides. SPA transfer action + history on assets page.
- **M458 (V278, M)** damage/loss case: asset_damage_loss_case (asset FK, employee, case_type DAMAGE/LOSS, description, est_amount, deduction_proposed bool, deduction_amount, status OPEN/APPROVED/REJECTED/CLOSED, approved_by) — on APPROVE with deduction, create a PayrollDeduction row via existing service (THIN BRIDGE — grep how M351/M352 create deductions; one-off kind if supported, else standing with end conditions; non-fatal, audited). Amounts Finance/HR-confidential. SPA cases page.
- **M459 (V—none, S)** ESS assets: GET /api/self/assets (own assigned assets) + acknowledge receipt (timestamp + optional attachment) + report damage (opens a damage case with employee-visible subset). "My assets" card/tab on MyWorkspace. Custody PDF via letter engine ONLY if trivial (grep LetterEngine render; else defer, note).

### Phase B — Loans & Advances (M460–M464) — amounts CONFIDENTIAL (payroll-grade)
- **M460 (V279, S)** loan_type catalog: code, name, max_amount NUMERIC NULL, max_multiple_of_net NUMERIC(4,1) DEFAULT 3, max_months INT DEFAULT 24, interest_rate_pct NUMERIC(5,2) DEFAULT 0 (interest-free default; SIMPLE interest seam), min_tenure_months INT DEFAULT 6, max_active_loans INT DEFAULT 1, active. CRUD HR_ADMIN. Seed 2 types (GENERAL, EMERGENCY).
- **M461 (V280, M)** loan_request: employee ESS submit (type, amount, months, purpose) → eligibility check server-side (tenure via hire date, amount vs max & vs multiple × current net-ish salary — reuse how payroll exposes monthly salary; active-loan count) → LOAN_REQUEST workflow (seed, single HR_ADMIN step per established pattern — copy V250 seed style) → on APPROVED create PayrollLoan + deduction via EXISTING PayrollLoanService.create (THIN BRIDGE, non-fatal-guarded, audited). Employee sees own; amounts hidden from non-payroll/HR roles.
- **M462 (V281, S)** installment schedule: loan_installment_schedule rows computed at approval (equal installments, projected month dates, running balance) + GET for HR/Finance + ESS "my loan statement" (own only).
- **M463 (V—none, M)** early settlement + reschedule: settle(loanId, amount) full/partial → reduce outstanding via existing PayrollLoanService methods (grep applyInstallment/writeOff), regenerate remaining schedule; reschedule(newInstallment) → recompute + audit; both Finance/HR_ADMIN, never delete history.
- **M464 (V—none, S)** loan dashboard: active loans, outstanding by department (aggregate), completion %, contract-overdue list. /api/reports/loans (HR/Finance). Small page.

### Phase C — GL (M465–M467) — Finance-only
- **M465 (V282, S)** posting approval: GLJournal gains approved_by/at, posted_by/at (migration ALTER) + status transition DRAFT→APPROVED→POSTED endpoints (Finance/HR_ADMIN roles per existing GL pages); block regenerate on POSTED (verify existing guard). SPA buttons on existing GL journal page.
- **M466 (V283, M)** reversal: reverse(journalId) creates offsetting journal (inverted debit/credit) with reversed_journal_id link (migration adds column); original marked REVERSED; chain shown in SPA.
- **M467 (V—none, S)** reconciliation report: payroll_run totals vs gl_journal totals per period, per-component discrepancy list. GET /api/reports/gl-reconciliation + small page/tab.

### Phase D — Compliance (M468–M472) — schema compliance
- **M468 (V284, M)** statutory_report_template catalog (code, name, country 'AZ', frequency MONTHLY/QUARTERLY/ANNUAL, file_format XML/CSV/XLSX, description; seed: monthly payroll tax summary, DSMF, MMI, unemployment (monthly, due 20th following), annual summary due 31 Jan). CRUD HR_ADMIN/compliance.
- **M469 (V285, M)** statutory_report_submission (template FK, period_start/end, status DRAFT/GENERATED/SUBMITTED/ACCEPTED/REJECTED, file attachment_id, generated_at/by, submitted_at, response_notes). generate() renders payroll aggregates for the period into XLSX/CSV via ReportExportService (grep it) — content = reasonable aggregate columns per template (document per-template mapping in code); status transitions audited. SPA page with generate/download/submit-mark.
- **M470 (V286, S)** compliance calendar: compliance_deadline (template FK NULL, title, due_date recurring rule simple: day_of_month + frequency, next_due computed) + daily reminder sweep (NotificationService to HR admins X days before) + dashboard widget/page listing due/overdue.
- **M471 (V287, M)** visa/work-permit tracker: extend employee document types if needed (check document_type/values) + work_authorized_until DATE on core_hr.employee (nullable) + expiry alerts via ExpiryAlertScheduler pattern + compliance page section; do NOT block hiring automatically (surface warning only — note).
- **M472 (V288, S)** privacy_request tracker (employee FK NULL, request_type ACCESS/EXPORT/DELETE/CORRECTION, description, status OPEN/IN_PROGRESS/COMPLETED/REJECTED, due_date +30d, resolution_notes) — HR_ADMIN only; page.

### Phase E — Analytics (M473–M476)
- **M473 (V289, S)** kpi_definition catalog (code, name, category, description, unit, target_value NUMERIC NULL, active; seed ~10 from existing DashboardService formulas: headcount, turnover, absence rate, avg tenure, cost/hire, training completion, eNPS...). CRUD HR_ADMIN; used by M475 display.
- **M474 (V290, M)** saved dashboards: dashboard_layout (owner_username, name, shared bool, widgets JSONB [{kpiCode|chartType, position}]). CRUD + render page pulling KPI values from a KpiValueService that maps code→existing service calls (implement ~8 codes, unknown codes render "n/a"). Simple grid (no drag-drop needed — position index).
- **M475 (V—none, S)** executive analytics page: consolidated cards (headcount trend, turnover YTD, payroll cost trend, engagement/eNPS, compliance deadlines status) reusing existing endpoints + KpiValueService; EXECUTIVE/HR_ADMIN roles.
- **M476 (V291, M)** attrition risk heuristic: nightly compute attrition_risk (employee_id UNIQUE per tenant, score 0-100, factors VARCHAR) — heuristic: +30 tenure<12mo, +25 no promotion/salary change in 24mo, +25 low engagement (latest eNPS detractor or low survey avg if linkable — else skip factor), +20 manager changed recently (if cheap) — document weights in code; HR-only list + flag column on an HR employee view. NEVER shown to employee/manager below HR.

### Phase F — Engagement (M477–M480)
- **M477 (V292, S)** pulse_schedule (template FK, cron-ish: frequency WEEKLY/BIWEEKLY/MONTHLY, day, active, last_run) — scheduler creates SurveyCampaign per schedule (reuse campaign machinery); admin UI on surveys page.
- **M478 (V293, M)** recognition/kudos: recognition (from_employee, to_employee, value_tag TEAMWORK/INNOVATION/EXCELLENCE/CUSTOMER_FOCUS/LEADERSHIP, message VARCHAR(1000), visibility PUBLIC/PRIVATE, status ACTIVE/HIDDEN [HR moderation]) — peer-to-peer (any employee sends; not to self); public wall (recent PUBLIC, names via JDBC projection) on MyWorkspace/engagement page; HR can hide. Distinct from HR-granted EmployeeReward.
- **M479 (V294, M)** engagement_action_plan (campaign FK NULL, org_unit NULL, owner_username, title, description, status DRAFT/ACTIVE/COMPLETED, due_date) + child action items (description, responsible, done bool). HR_ENGAGEMENT/HR_ADMIN; page with progress.
- **M480 (V—none, S)** participation analytics + anonymity guard: response rate by department/location for a campaign; ENFORCE min-responses threshold (default 5, config via existing tenant_setting key engagement_min_group_responses) before ANY group breakdown (server-side); simple sentiment tagging (positive/neutral/negative keyword lists) on open-text answers, aggregate-only.

## Roles
Employees: own assets/acknowledge/damage-report, loan requests + own statement, recognition send/wall, surveys. Managers: team asset visibility (no values), NO loan amounts. HR_ADMIN: everything except Finance-gated. Finance (existing finance-ish roles — grep what GL pages use and reuse EXACTLY): GL post/reverse/reconciliation, loan settle/write-off, damage deduction approve. Compliance/privacy: HR_ADMIN. Attrition risk + anonymity: HR-only, threshold-guarded.

## Adopted defaults (gap-check record, 19 items)
Interest-free loans default (config INTEREST_FREE/FIXED_FEE/SIMPLE seam); max 3× net monthly, 24 months, 1 active/type, tenure ≥6mo; advances ≤50% salary (existing); equal installments; early settlement no penalty; write-off = Finance+HR dual (existing service+workflow); GL mapping table exists (UI extend), cost-center aggregate posting, reversal = inverted linked journal, IFRS-style 6-digit seeds; AZ statutory = XML/CSV/XLSX exports (no gov API), monthly due 20th, annual 31 Jan; depreciation methods stay M128; disposal keeps existing close+audit (+workflow only if cheap); KPI formulas reuse DashboardService definitions; anonymity threshold 5 (tenant-configurable); eNPS standard formula; pulse frequency tenant-configurable. Migration numbers above are indicative — allocate sequentially from V276, note actuals.
