# HCM_12 — Performance Management — Analysis & Lean Build Plan

**Module:** performance · **payroll_impact:** false (ratings feed the M363 merit matrix read-side; comp keeps its own budget/approval controls per PRD §38.7)
**Depends on:** HCM_10 Compensation (done). AZ single-country; tenant-configurable everything.

## Phase 1 outcome
- **gap-checker: PASS** — no hard stops. Scoring math (§18), approval chains (§6.3/§10.3), visibility (§31), goal-weight rule (§37.5) and PIP criteria (§20) are specified or safely defaultable.
  **Adopted AZ seed defaults:** 5-point scale (1 Unsatisfactory…5 Outstanding); section weights Goals 50/KPI 25/Competency 20/Values 5 (sum=100); score bands 0-59/60-74/75-89/90-100; goal approval Employee→Manager; goal weights sum to 100; calibration notes HR/committee-only; 360 anonymous by default; PIP manual (flag suggested at rating ≤2).
- **Existing-code audit** — ~40–50% pre-built; **EXTEND the existing `az.millers.hcm.performance` module in place** (no fork):
  - REUSE wholesale: goal cascade (M130 `GoalCascadeService`/`GoalTreeMath`), 9-box (M92 `SuccessionPlanService`), calibration board v2 (M121 targets+lock+edit-log), merit-matrix bridge (M363 reads `final_rating`), probation reviews (M73, lifecycle — separate by design), workflow engine (PERFORMANCE_REVIEW_APPROVAL seeded V7), letter engine, audit, notifications, dashboard patterns, learning competency framework (V87 `learning.competency` + `position_required_competency`).
  - **CRITICAL:** all 7 performance tables (`review_cycle, goal, performance_review, feedback, calibration_session, cycle_calibration_target, calibration_edit_log`) predate tenant_id → V215 backfill (same fix as compbenefits V206).
  - `feedback` already supports PEER/DIRECT_REPORT/MANAGER/SKIP_LEVEL/CROSS_FUNCTIONAL/EXTERNAL/SELF + ANONYMOUS → PRD §13–16 (360/peer/subordinate/project) = questionnaires + nomination over the EXISTING entity, not four new modules.
  - TRUE NEW: rating-scale master, review templates+sections, KPI library, OKR, structured competency assessment, PIP, development plans (perf-side), continuous feedback + check-ins, appeals.

## Conventions (locked)
Schema `performance` (extend); package `az.millers.hcm.performance`; existing SPA pages get new tabs/pages under the Performance nav. tenant_id `VARCHAR(64) NOT NULL DEFAULT 'default'` (String). Next Flyway = **V215**. AuditService 6-param; AccessScopeService for hierarchy; workflow seeds follow the V201/V210 pattern; roles: reuse READ_HR_PLUS_MANAGERS / WRITE_HR / WRITE_HR_ADMIN_ONLY (already used by performance controllers — no new role constants needed unless the build shows otherwise).

## Lean milestone plan — M388–M402 (15 milestones, 6 phases)
Consolidated from the analyzer's 53 (M388–M440). Each milestone = migration (where needed) + backend + SPA, boot-verified, committed; UAT Excel grown per phase.

**Phase A — Config foundations**
- **M388** (V215) **tenant_id backfill** on all 7 performance tables + **rating-scale master** (`rating_scale` + `rating_scale_value`, seed DEFAULT_5PT with labels/score bands §5.3/§18.3). Rating Scales admin tab.
- **M389** (V216) **review templates** (`perf_review_template` + `perf_template_section`: section types §5.2, weight %, required, order; applicability by legal-entity/dept/grade/employee-type) + **cycle scoping/eligibility** fields on `review_cycle` (§5.1/§10.2) + template_id on review.
- **M390** (V217) **KPI library** (`kpi` + `kpi_assignment` + `kpi_result`; achievement % = actual/target, linear + threshold scoring §7.4). KPI admin + assignment UI.
- **M391** (V218) **OKR** (`okr_objective` levels COMPANY→INDIVIDUAL + parent link; `okr_key_result` baseline/target/current/progress/confidence §5.6/§8; progress roll-up = weighted KR average). OKR page.

**Phase B — Goals & scoring**
- **M392** (V219) **goal approval workflow** (seed GOAL_APPROVAL: employee→manager; goal gains approval status + workflow_instance_id; §37.5 weight-sum=100 validation on submit; `goal_progress_update` audit trail §6.4).
- **M393** (V220) **structured competency assessment** (`perf_competency_assessment`: review_id + learning.competency_id, required level from `position_required_competency`, self/manager/final level, gap = required − final §17.3; feeds dev plans). Review detail tab.
- **M394** (V221) **weighted scoring + override** — section scores on review (goal_score exists; add kpi_score, competency_score, values_score), overall = Σ(score×template weight) §18.2, score→band via rating scale §18.3; **manual override** (reason required, HR-gated, original preserved, audited) §18.4.

**Phase C — 360 depth**
- **M395** (V222) **360 nomination + questionnaires** — `feedback_request` (nominate/approve/decline→complete §13.2, min/max reviewers) + `feedback_questionnaire`/`feedback_question` (§13.3); responses stay on the EXISTING `feedback` entity (relationship types cover peer §14 / subordinate §15 / project §16; anonymous §13.4 already supported). Nomination UI + questionnaire admin.

**Phase D — Acknowledgement, appeals, calibration depth**
- **M396** (V223) **acknowledgement + appeals** — ack fields on review (acknowledged_at/comments/disputed §25); `performance_appeal` (SUBMITTED→UNDER_REVIEW→APPROVED/REJECTED/RETURNED→CLOSED §26; adjustment preserves original rating §37.12).
- **M397** (V224) **calibration enhancements** — `calibration_committee_member` (session access control §19.1), calibration-notes visibility (hidden from employees §19.3/§31.3), distribution-vs-target + outlier view (reuse CalibrationBoardMath + M121 targets).

**Phase E — PIP, development, continuous feedback**
- **M398** (V225) **PIP** — `performance_improvement_plan` + `pip_milestone` (statuses §20.2, outcomes §20.4, employee acknowledgement, check-in reviews §20.3; HR/manager-only visibility §31.3; letter-engine seam documented).
- **M399** (V226) **development plans** — `development_plan` + `development_action` (10 action types §21.2; links: competency gap M393, learning path M95 reuse §21.3).
- **M400** (V227) **continuous feedback + check-ins** — `continuous_feedback` (praise/recognition/improvement/private-note; visibility EMPLOYEE_VISIBLE vs MANAGER_ONLY §22.1) + `check_in` (1:1 notes, action items, follow-up, employee ack §22.2).

**Phase F — Dashboards, reports, notifications**
- **M401** performance **dashboards** (HR/Manager/Employee/Executive §27 — read-only service over existing data, AccessScope-scoped) + core **reports** (§28 top set: cycle status, rating distribution, goal/review completion, competency gaps, PIP, overdue).
- **M402** **notifications** (§29: submit/approve/due/ack reminders — reuse NotificationService, non-fatal) + **comp-bridge guard** (§37.15: merit matrix only reads APPROVED/COMPLETED final ratings — verify/extend M363 read).

## Deliberate scope notes
- Peer/subordinate/project feedback ride on questionnaires + existing relationship types (no separate modules).
- Probation reviews stay in lifecycle (M73) — surfaced on dashboards only.
- KPI external data sources (ERP/CRM/API §7.3) = manual/manager entry now; import/API is a documented seam.
- Forced distribution is guidance-vs-target (M121), not a hard block, per §19.2 "if tenant policy uses it".
- Retention policy (§33) deferred with the platform-wide retention story.

## Testing deliverable
`Performance_UAT_Test_Script.xlsx` from `gen_performance_uat.py` — front-end click-by-click cases with Pass/Fail dropdowns, grown per phase (same shape as benefits/compensation).
