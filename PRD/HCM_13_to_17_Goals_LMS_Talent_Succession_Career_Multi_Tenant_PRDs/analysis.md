# HCM_13–17 Bundle — Analysis & Milestone Plan (M403–M418, V228+)

Phase 1 output (prd-analyzer reuse audit + gap-checker), 2026-07-04. Gap-check verdict: **PASS — no hard stops**; all un-inventables defaulted below.

## Coverage audit (reuse-first)

| Module | Coverage | Exists (evidence) | Missing |
|---|---|---|---|
| 13 Goals/OKR | ~95% | Goals V19/V89/V219 (categories, weighting, §37.5 validation, progress trail, scoring, cascade M130), KPI V217, OKR V218, GOAL_APPROVAL workflow, Goals/OKR/KPI pages | Goal-type catalog (§13.4 — 14 business types), org_unit/legal_entity anchors on goal |
| 14 LMS | ~40% | Course/Enrollment/Quiz/Competency/Certificate V20, LearningPath M95–M101, TrainingPlan/Item, IDP V47, skill-gap M98, onboarding auto-enrol M303, passing_score default 70, valid_for_months + certificate.valid_until + ExpiryAlertScheduler | Classroom sessions + instructors + rooms + attendance, mandatory/compliance recurrence, training calendar, cost tracking, feedback, external LMS seam |
| 15 Talent | ~30% | 9-box M92 (potential on review V68), succession nominations V70/M103, skills V87/M127/M136, competencies, dev plans M399, internal postings (recruitment) | Talent profile aggregate, talent reviews, HiPo flag, EMPLOYEE talent pools (M87 pools = candidates), retention risk, career interests, talent analytics |
| 16 Succession | ~25% | SuccessionNomination + readiness enum (READY_NOW/READY_SOON/READY_LONG_TERM/UNDER_DEVELOPMENT), bench depth M94, 9-box | Critical positions, succession_plan entity + approval workflow, risk/impact-of-loss, replacement chart, emergency successor, dev-action bridge, coverage analytics |
| 17 Career | ~20% | IDP V47, skill-gap analyzer M98, dev plans M399, check-ins M400, internal postings | Career paths (steps + requirements), career profile, career interests, mentoring, internal job recommendations, career dashboard |

## Milestone plan (16 milestones)

**Phase A — Goals/OKR completion (1)**
- **M403 (V228)** goal_type catalog (PRD §13.4 seed) + optional org_unit_id/legal_entity_id anchors on performance.goal; GoalType CRUD + SPA dropdowns. Reuse GoalService/GoalsPage.

**Phase B — LMS gaps (5)**
- **M404 (V229)** learning.instructor (internal employee FK or external, qualifications, cost) + learning.training_room (location, capacity).
- **M405 (V230)** learning.training_session (course/instructor/room/schedule/capacity/status) + training_attendance (ENROLLED/ATTENDED/NO_SHOW/LATE/CANCELLED); session enrol/cancel; calendar-ish list view.
- **M406 (V231)** mandatory_training_rule (course + department/position/location scope, recurrence_months, reminder_days) + daily sweep auto-assigning/renewing enrolments; compliance widget. Reuse EnrollmentService + valid_for_months.
- **M407 (V232)** training_cost (session/course, type INSTRUCTOR/VENUE/MATERIAL/TRAVEL/OTHER, amount AZN); cost per course/department report.
- **M408 (V233)** training_feedback (ratings + comment, anonymous default) + external-LMS config seam (documented placeholder).

**Phase C — Talent (4)**
- **M409 (V234)** performance.talent_profile (hipo_flag, retention_risk LOW/MEDIUM/HIGH/CRITICAL + reason + action plan, mobility/relocation, aspirations) + career_interest rows. HR-only visibility.
- **M410 (V235)** EMPLOYEE talent pools (pool master + members). Reuse M87 UI pattern.
- **M411 (V236)** talent_review_cycle + talent_review (panel decisions: boxes, HiPo, retention action) feeding talent_profile; integrates 9-box.
- **M412 (V237)** talent analytics (9-box distribution, HiPo %, pool coverage, retention-risk summary) page. Reuse 9-box math.

**Phase D — Succession (3)**
- **M413 (V238)** critical_position (criticality, replacement_difficulty, vacancy_risk) + succession_plan (owner, dates, status DRAFT→SUBMITTED→APPROVED→ACTIVE→ARCHIVED, emergency successor) + SUCCESSION_PLAN_APPROVAL workflow (manager→HR default).
- **M414 (V239)** risk_of_loss/impact_of_loss (LOW/MEDIUM/HIGH/CRITICAL) + reason + retention action on nominations/incumbents; risk dashboard.
- **M415 (V240)** replacement chart endpoint (position → successors/readiness/risk tree) + successor→dev-plan bridge (M399); coverage analytics (positions with 0/1/2+ ready successors).

**Phase E — Career (3)**
- **M416 (V241)** staffing.career_path + career_path_step (from/to position or grade, required skills/certs/courses, typical tenure); CareerPathsPage.
- **M417 (V242)** mentoring: mentor_profile + mentoring_relationship (request → ACTIVE → COMPLETED/CANCELLED, 6-month default term); self-service request.
- **M418 (V243)** career dashboard + rule-based internal job recommendations (career_interest + skills vs posting requirements match score). Reuse InternalCareers + SkillGapAnalyzer.

## Adopted defaults (gap-check PASS)
- Succession plan approval: manager → HR via existing workflow engine; training/mentoring approvals: manager only.
- Readiness bands: READY_NOW ≤3mo, READY_SOON 3–12mo, READY_LONG_TERM 12–36mo, UNDER_DEVELOPMENT >36mo (existing enum kept).
- Risk/impact of loss + retention risk + replacement difficulty: manual HR judgment LOW/MEDIUM/HIGH/CRITICAL (no algorithmic score).
- HiPo: 9-box top cell OR manual HR designation; **employees never see own HiPo/retention-risk/succession status** (HR + executives only). Mentoring notes: mentor+mentee+HR. Career interests: employee+manager+HR.
- Exams: 70% default pass mark (existing). Certification renewal: re-enrol before expiry; 30-day alert window; recurrence via valid_for_months. Mandatory training defaults: safety/compliance 12mo, cybersecurity 6mo (per-rule config).
- Career path steps: ordered, from→to position/grade + required skills/certifications/experience/courses.

## Scope notes
Single tenant 'default' (conventions systemwide); AZ single-country; external LMS/HRIS = documented seams; recommendations rule-based (no ML). payroll_impact: false — no payroll hard-stop.
