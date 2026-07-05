# HCM_23–28 Bundle — Analysis & Milestone Plan

Analyzer + gap-checker both PASS (2026-07-05). Milestones **M436–M455**, migrations **V256–V275**. payroll_impact: false — expense reimbursement is a THIN bridge (payroll_bonus row like M295), never payroll execution.

## Coverage

| Module | Built already | % |
|---|---|---|
| 23 HRSD | M429 helpdesk (SLA, categories, GRIEVANCE scoping), M77/M139 letters, M138 policies | 55% |
| 24 DocMgmt | M16/M23 attachments+MinIO+ClamAV M50+thumbs M142, M169 employee_document V111 (types/expiry/restricted), M262 required docs, M292 candidate docs, M294 retention | 65% |
| 25 Workflow | Full engine: V113 parallel + SpEL conditional routing, V31 manager resolution, V33 delegation, V117 substitute_approver, M126 SLA breach+escalation, M435 bulk-act | 75% |
| 26 ER | M67 disciplinary_action (ladder, appeal fields, linked_case_id), GRIEVANCE in M429 | 30% |
| 27 EHS | M65 health, M137 vaccinations, M72 assets (PPE seam), M406 mandatory safety training | 20% |
| 28 T&E | M32 business_trip (advance fields), M104 expense_claim+items V71, M351 salary_advance | 45% |

## Milestones

### Phase A — HRSD (M436–M438)
- **M436 (V256, M)** knowledge base: selfservice.knowledge_article (code, title, summary, category, tags, body MARKDOWN/HTML, attachment, status DRAFT/PUBLISHED/ARCHIVED, version, view_count, helpful_votes). KnowledgeArticleService CRUD/publish/vote/views. /api/helpdesk/knowledge (published readable by all authenticated; mutations WRITE_HR). SPA KnowledgeBasePage (/helpdesk/knowledge: category browse + keyword search) + admin editor.
- **M437 (V257, S)** ticket comments: hr_service_request_comment (request FK, author, is_internal, body). Employee sees own non-internal comments; HR sees all; comment adds to request detail drawer both sides. POST /api/self/hr-requests/{id}/comments + HR variant.
- **M438 (V258, S/M)** agent queues: hr_agent_queue (code, name, routing_category) + queue_id on hr_service_request; auto-route by category on submit, manual reassign; queue tabs + counts in HrServiceQueuePage. Plus per-category SLA defaults (SALARY_CERT 2bd, EMPLOYMENT_LETTER 2, PAYROLL_INQUIRY 3, POLICY_QUESTION 5, GRIEVANCE 1, OTHER 3) taking precedence over priority-based SLA.

### Phase B — DocMgmt (M439–M441)
- **M439 (V259, M)** document categories + versioning: core_hr.document_category (code, name, mandatory, retention_days) + employee_document gains category_id, version INT, previous_version_id. uploadNewVersion chains versions (latest = current). SPA admin page + version history in employee documents tab.
- **M440 (V260, M)** signature requests: core_hr.signature_request (document/letter FK nullable, signers JSONB [{username,status,signed_at}], status, provider INTERNAL only now — DocuSign seam). Internal signing = authenticated click + timestamp + audit. /api/documents/signatures. SPA SignaturesPage (HR) + sign prompt for employees (own pending signatures on MyWorkspace).
- **M441 (V261, S)** expiry→renewal trigger: document_category.auto_create_renewal_request; ExpiryAlertScheduler creates HR service request (new category DOCUMENT_RENEWAL) X days before expiry, idempotent per document+expiry.

### Phase C — Workflow (M442–M444)
- **M442 (V262, M)** definition versioning: workflow_definition gains version (default 1) + effective_from/to; UNIQUE(code, version); WorkflowService.start picks version effective today; migration stamps existing rows version=1. Admin surface: definitions list showing versions (extend existing workflow admin page if present, else read-only list page).
- **M443 (V263, M)** approval groups: workflow.approval_group (code, name, member_roles JSONB or member_usernames) + approval_group_id on workflow_step; any member may act. ApprovalGroupsPage admin.
- **M444 (V264, S)** escalation AUTO_APPROVE/AUTO_REJECT: extend EscalationAction enum + SLA scheduler executes with system actor, audited "SLA_AUTO".

### Phase D — ER (M445–M447) — CONFIDENTIAL module
- **M445 (V265, L)** ER cases: schema employee_relations. er_case (case_no ER-seq, employee_id, case_type GRIEVANCE/COMPLAINT/INVESTIGATION/DISCIPLINARY_LINK, category, severity, status OPEN/UNDER_INVESTIGATION/RESOLVED/CLOSED, owner_username, is_confidential, legal_hold, linked_disciplinary_id, outcome) + er_case_note (is_internal) + er_investigation (investigator, findings, recommendation) + er_investigation_interview + er_evidence (attachment FK). Confidential case visible ONLY to owner + HR_ADMIN. Anonymous complaint option (reporter nullable). ErCasesPage + detail drawer tabs. Roles: HR_ADMIN/HR_SPECIALIST base; confidential = HR_ADMIN + owner only.
- **M446 (V266, S)** warnings: warning_record (employee, level VERBAL/FIRST_WRITTEN/SECOND_WRITTEN/FINAL, disciplinary_action FK, issued/expires_at [validity 6/12/18mo/indefinite], acknowledged flags, attachment). Issue from disciplinary action; employee acknowledges (own only); active-warnings view. WarningsTab on EmployeeDetailPage + self view.
- **M447 (V267, S)** corrective action plans: corrective_action_plan (er_case/disciplinary FK nullable, employee, action_required, responsible, due_date, status incl. OVERDUE via daily sweep, follow_up_date). CorrectiveActionsPage queue.

### Phase E — EHS (M448–M451) — mostly new; schema ehs
- **M448 (V268, L)** incidents: ehs.incident (incident_no seq, date/time, location/department, type INJURY/NEAR_MISS/UNSAFE_CONDITION/PROPERTY_DAMAGE/VEHICLE/CHEMICAL/FIRE/EQUIPMENT/VIOLENCE/ENVIRONMENTAL, severity MINOR/MODERATE/SERIOUS/CRITICAL, reporter, involved employees, description, immediate_action, investigation_required, status) + incident_witness. ANY employee can report; list scope = reporter's own + EHS_OFFICER/HR full. Photos via AttachmentUploader. EhsIncidentsPage + report form (also reachable from MyWorkspace).
- **M449 (V269, M)** injury + return-to-work: injury_report (incident FK, employee, injury_type, body_part, treatment flags, lost_time_days, insurance ref) + return_to_work_plan (clearance date, restrictions, manager/HR approve flags, status). Medical data = HR_ADMIN only reads. ReturnToWorkPage.
- **M450 (V270, M)** risk assessments + inspections: risk_assessment (location/department/job_task, hazard, likelihood 1-5 × impact 1-5 = score, HIGH ≥15, controls, review_date, status) + safety_inspection (location, date, inspector, findings JSONB, score) + inspection_finding (OK/NON_COMPLIANT, corrective_action link). RiskRegisterPage + InspectionsPage.
- **M451 (V271, S)** EHS corrective actions + PPE: ehs.corrective_action (incident/inspection/risk FK nullable, responsible, due, priority, status, evidence) + ppe_item catalog (type HELMET/GLOVES/SHOES/VEST/GOGGLES/MASK/EAR/CLOTHING/OTHER, default_expiry_months: 24/6/12/24/24/1/12/12) + ppe_assignment (employee, item, issued/expiry/returned, condition). Expiry via ExpiryAlertScheduler. PpeItemsPage + PpeAssignmentsPage.

### Phase F — T&E (M452–M455)
- **M452 (V272, M)** per-diem rules: business_trip.per_diem_rule (country, city?, grade?, trip_type?, meal/lodging/incidentals, currency AZN, effective window). Seed: Baku 60 (30/25/5), regions 50, Istanbul 80, Dubai 100, Moscow 90. PerDiemService.calculate(destination, grade, days) → breakdown; BusinessTripService pre-fills allowance. PerDiemRulesPage + breakdown on trip form.
- **M453 (V273, S)** mileage claims: mileage_claim (date, vehicle_type, from/to, distance_km, rate default 0.30 AZN/km, total, status DRAFT/SUBMITTED/APPROVED/REJECTED/PAID, approver). Manager-or-HR approve (simple status transitions + audit; workflow optional). MileageClaimsPage.
- **M454 (V274, M)** expense policy engine: expense_policy (category, grade?, max_per_transaction, max_daily, receipt_required_threshold default 20 AZN, blocked). Seeds: MEALS 50/day, ACCOMMODATION 150 dom/250 intl, TAXI 100. ExpensePolicyService.validate(line) → VALID/WARNING/EXCEPTION_REQUIRED/BLOCKED; wired into ExpenseClaimService.submit (blocked lines rejected, warnings recorded). ExpensePolicyPage admin + badges on claim form.
- **M455 (V275, M)** reimbursement batch + thin payroll bridge: reimbursement_batch (batch_no, claims JSONB or join table, total, status DRAFT/APPROVED/PAID, payment_ref). markPaid creates ONE_TIME payroll bonus rows (source EXPENSE_REIMBURSEMENT — copy M295 pay() bridge) per claim. Finance-role page ReimbursementBatchesPage.

## Roles
Employees: report incidents, own tickets/comments/claims/warnings-acknowledge/signatures. Managers: hierarchy via AccessScopeService (mileage approve, RTW manager flag). HR_SPECIALIST: HRSD queues, docs, EHS view. HR_ADMIN: confidential ER cases, medical injury data, workflow config, policies/rules admin. Reuse existing role set; ER/EHS "officer" duties map onto HR_SPECIALIST unless a dedicated role already exists (check SecurityRoles — do NOT invent new Keycloak roles without need).

## Adopted defaults (gap-check record)
1. Per-diem/mileage/expense limits = tenant-configurable tables with AZ seeds (above). 2. Advance settlement = delta vs approved claim; reimbursement/deduction via thin payroll bridge. 3. Receipts required > 20 AZN; OCR deferred. 4. Warning validity 6/12/18/indefinite; ladder VERBAL→WRITTEN→FINAL→DISMISSAL (extend M67 enum with DEMOTION_RECOMMENDATION, TRAINING_REQUIREMENT, CORRECTIVE_ACTION_PLAN). 5. ER retention 7y default, soft-delete + legal_hold blocks sweeps. 6. Incident severity MINOR(first aid)/MODERATE(1-3 lost days)/SERIOUS(>3 or hospital)/CRITICAL(fatality/permanent); reportable threshold config, regulatory submission = PDF export only. 7. Risk 5×5 matrix, HIGH ≥15. 8. Workflow gaps are ONLY versioning/groups/auto-escalation — engine already has parallel/conditional/delegation/substitution (V113/V117/V86). 9. Doc retention seeds: contracts 75y, medical 10y, disciplinary 7y, general 5y; legal_hold blocks. 10. Versioning = version chain, latest current, HR rollback. 11. Signature provider INTERNAL (click+timestamp+audit); DocuSign seam. 12. No chatbot/email-to-ticket/corporate-card import (seams). 13. SLA business days reuse M429 logic + holiday table.
