---
feature: goals-lms-talent-succession-career
module: performance          # bundle spans performance (13,15,16), learning (14), self-service/career (17)
payroll_impact: false
status: backlog
depends_on: [HCM_12_Performance_Management_Multi_Tenant_PRD]
---

# HCM Modules 13–17 — Multi-Tenant PRDs

## Document Scope

This document contains five full product requirement documents for the following HCM modules:

1. **13. Goals / OKR Management**
2. **14. Learning Management System, LMS**
3. **15. Talent Management**
4. **16. Succession Planning**
5. **17. Career Development**

The system must support **multi-tenancy** from launch. Every feature, workflow, report, permission, configuration, and integration must be tenant-aware.

---

# Global Multi-Tenancy Requirements for All Five Modules

## 1. Tenant Isolation

Each tenant must have logically isolated data.

### Requirements

- Every record must include `tenant_id`.
- No user from one tenant can access another tenant’s data.
- Tenant-level data filtering must be enforced at API, service, database query, reporting, export, and background job levels.
- Cross-tenant joins must be blocked unless executed by controlled platform administration tools.
- Tenant-specific files, attachments, certificates, learning materials, feedback records, talent reviews, and career data must be stored separately.
- Audit logs must include tenant ID.
- Background jobs must run per tenant or include tenant-safe filtering.

## 2. Tenant-Level Configuration

Each tenant must be able to configure its own policies and terminology.

### Configurable by tenant

- Goal periods
- OKR cycles
- KPI libraries
- Rating scales
- Learning categories
- Course catalog
- Certification rules
- Talent review templates
- 9-box grid labels
- Succession readiness levels
- Risk ratings
- Career path structures
- Competency libraries
- Workflow approval rules
- Notification templates
- Report visibility
- Security roles
- Data retention rules

## 3. Tenant Branding and Localization

The system should support tenant-specific presentation and localization.

### Requirements

- Tenant logo and branding on employee portals, certificates, statements, and reports.
- Tenant-specific terminology, for example “Objective,” “Goal,” “Target,” or “KPI.”
- Multi-language support for goals, courses, forms, feedback, career paths, and notifications.
- Country/legal entity-specific learning and compliance content.
- Local date, time, currency, number, and percentage formats.

## 4. Tenant-Specific Security

Each tenant must define its own roles and permissions.

### Requirements

- Role-based access control.
- Legal entity-based access.
- Department-based access.
- Position-based access.
- Manager hierarchy access.
- HR business partner access.
- Field-level security for sensitive talent, succession, performance, and career data.
- Export/download restrictions.
- Confidential talent review and succession data restrictions.
- Segregation of duties where needed.

## 5. Tenant-Aware Workflows

Each tenant must be able to configure workflows independently.

### Workflow examples

- Goal approval workflow.
- OKR approval workflow.
- Training approval workflow.
- Certification renewal approval.
- Talent review approval.
- Succession plan approval.
- Career movement approval.
- Development plan approval.
- Internal mobility approval.

## 6. Tenant-Aware Integrations

All integrations must be tenant-safe.

### Requirements

- Tenant-specific API credentials.
- Tenant-specific webhook endpoints.
- Tenant-specific external LMS integrations.
- Tenant-specific SSO / identity provider settings.
- Tenant-specific email/SMS templates.
- Tenant-specific payroll, finance, and HR integrations.
- Tenant-specific integration logs and retry queues.

## 7. Tenant-Aware Reporting and Analytics

Reports must never leak data between tenants.

### Requirements

- Reports filtered by tenant ID.
- Tenant-level dashboards.
- Tenant-specific report definitions.
- Tenant-specific export permissions.
- Tenant-aware scheduled reports.
- Tenant-aware analytics warehouse partitions or filters.
- Platform admin analytics must use anonymized or aggregated data unless explicitly authorized.

---

# 13. Goals / OKR Management PRD

## 1. Purpose

The **Goals / OKR Management** module manages company goals, department goals, individual goals, objectives and key results, KPI tracking, goal weighting, progress updates, approvals, scoring, and goal alignment.

This module may exist as a standalone module or integrate closely with Performance Management.

## 2. Core Objectives

- Define company-level goals.
- Define department-level goals.
- Define team-level goals.
- Define individual employee goals.
- Manage OKRs.
- Track KPIs.
- Align goals across the organization.
- Cascade goals from company to employee level.
- Assign goal weights.
- Approve goals.
- Track progress.
- Score goals.
- Link goals to performance reviews.
- Provide dashboards and analytics.

## 3. Goal and OKR Setup

### Main features

- Goal cycle setup.
- OKR cycle setup.
- Goal period setup.
- Goal category setup.
- Goal type setup.
- KPI library.
- Measurement unit setup.
- Goal weighting rules.
- Scoring rules.
- Approval workflow setup.
- Visibility rules.
- Reminder rules.
- Goal template setup.

### Goal cycle examples

- Annual goals.
- Semi-annual goals.
- Quarterly OKRs.
- Monthly targets.
- Project-based goals.
- Probation-period goals.

## 4. Goal Types

### Supported goal types

- Company goal.
- Legal entity goal.
- Business unit goal.
- Department goal.
- Team goal.
- Position-based goal.
- Individual goal.
- Project goal.
- Development goal.
- Sales goal.
- Operational KPI.
- Compliance goal.
- Customer service goal.
- Financial goal.

## 5. Company Goals

### Main features

- Create company goals.
- Assign goal owner.
- Define goal period.
- Define strategic priority.
- Define weight.
- Define KPIs.
- Define target values.
- Define baseline values.
- Track progress.
- Link to department goals.
- Approve company goals.
- Publish company goals.
- Show visibility to employees.

### Business logic

Company goals can cascade into department goals and individual objectives.

Example:

```text
Company Goal: Increase annual revenue by 20%
Department Goal: Sales Department to increase B2B revenue by 25%
Individual Goal: Sales Manager to close 30 new enterprise accounts
```

## 6. Department Goals

### Main features

- Create department goals.
- Link to company goals.
- Assign department owner.
- Assign contributing teams.
- Assign KPI targets.
- Define weight.
- Track department progress.
- Approve department goals.
- Cascade to employees.

### Business logic

Department goals should be linked to organizational structure and must respect tenant-specific department access.

## 7. Individual Goals

### Main features

- Assign goals to employees.
- Employee self-created goals.
- Manager-created goals.
- HR-assigned goals.
- Link goals to position/job.
- Link goals to department goals.
- Define due dates.
- Define milestones.
- Define weight.
- Track progress.
- Employee comments.
- Manager comments.
- Approval workflow.
- Final scoring.

## 8. OKR Management

### Main features

- Objective creation.
- Key result creation.
- OKR owner.
- OKR contributors.
- OKR confidence score.
- OKR status.
- OKR progress.
- Weekly check-ins.
- OKR alignment.
- OKR scoring.
- OKR comments.
- OKR history.

### OKR fields

- Objective title.
- Objective description.
- Objective owner.
- Cycle.
- Key results.
- Target values.
- Current values.
- Progress percentage.
- Confidence level.
- Status.
- Alignment parent.
- Visibility.

### OKR statuses

- Draft.
- Submitted.
- Approved.
- Active.
- At risk.
- Behind.
- On track.
- Completed.
- Cancelled.
- Archived.

## 9. KPI Tracking

### Main features

- KPI definition.
- KPI owner.
- KPI target.
- KPI actual value.
- KPI unit of measure.
- KPI frequency.
- Manual KPI update.
- Automated KPI import.
- KPI threshold.
- KPI traffic light status.
- KPI history.

### KPI units

- Number.
- Percentage.
- Currency.
- Hours.
- Days.
- Ratio.
- Score.
- Count.

## 10. Goal Alignment and Cascading Objectives

### Main features

- Parent-child goal relationship.
- Cascading goals.
- Goal contribution percentage.
- Alignment map.
- Goal tree.
- Goal dependency tracking.
- Contribution tracking.
- Alignment dashboard.

### Business logic

An employee goal can contribute to multiple higher-level goals if the tenant allows many-to-many goal alignment.

## 11. Goal Weighting

### Main features

- Assign goal weight.
- Validate total weight equals 100%.
- Weight by category.
- Weight by role.
- Weight by employee grade.
- Weight by department.
- Weight override approval.

### Business logic

Example:

```text
Financial KPI: 40%
Customer KPI: 25%
Operational KPI: 20%
Development goal: 15%
Total: 100%
```

## 12. Goal Approval Workflow

### Main features

- Goal submission.
- Manager approval.
- Department head approval.
- HR approval.
- Goal revision request.
- Goal rejection.
- Approval comments.
- Approval history.
- Delegation.
- Escalation.

### Approval scenarios

- Employee submits individual goals for manager approval.
- Manager submits department goals for department head approval.
- HR submits company goal template for executive approval.

## 13. Goal Progress Updates

### Main features

- Manual progress update.
- Automatic progress update from KPI source.
- Check-ins.
- Progress notes.
- Attachments.
- Evidence upload.
- Manager feedback.
- Progress history.
- Risk flag.

## 14. Goal Scoring

### Main features

- Self-score.
- Manager score.
- Final score.
- Weighted score.
- KPI-based score.
- Manual score override.
- Score approval.
- Score history.

### Scoring methods

- Percentage achievement.
- Rating scale.
- Weighted achievement.
- Threshold-based scoring.
- Binary completion.
- Manager judgment.

## 15. Dashboards and Reports

### Dashboards

- Company goal progress.
- Department goal progress.
- Individual goal progress.
- OKR status dashboard.
- Goals at risk.
- Goals overdue.
- Goal alignment dashboard.
- KPI achievement dashboard.

### Reports

- Goal list.
- OKR list.
- Goal approval report.
- Goal progress report.
- Goal scoring report.
- Department goal achievement.
- Employee goal achievement.
- Overdue goals.
- Goals without alignment.
- Goals without updates.
- Goal audit report.

## 16. Integrations

### Integrates with

- Performance Management.
- Employee Management.
- Organizational Management.
- Position Management.
- Compensation Management.
- Learning Management.
- Analytics.
- Notification Engine.

## 17. Security

### Requirements

- Employees see own goals.
- Managers see team goals.
- HR sees tenant-scoped goals.
- Executives see aggregated company goals.
- Confidential goals restricted.
- Goal scoring visibility configurable.
- Export controlled by role.

## 18. Audit Trail

Track:

- Goal creation.
- Goal update.
- Goal approval.
- Goal rejection.
- Goal progress update.
- Goal weight change.
- Goal score change.
- Goal deletion/archive.
- OKR alignment changes.

## 19. Recommended Menu

```text
Goals / OKR Management
├── Dashboard
├── Goal Cycles
├── Company Goals
├── Department Goals
├── Individual Goals
├── OKRs
├── KPI Library
├── Goal Alignment
├── Goal Approvals
├── Progress Updates
├── Goal Scoring
├── Reports
└── Settings
```

## 20. Main Data Entities

- GoalCycle
- Goal
- GoalKPI
- OKRObjective
- OKRKeyResult
- GoalAlignment
- GoalProgressUpdate
- GoalApproval
- GoalScore
- GoalComment
- GoalAttachment
- GoalAuditLog

---

# 14. Learning Management System, LMS PRD

## 1. Purpose

The **Learning Management System** module manages employee training, online courses, classroom training, certifications, mandatory training, exams, learning paths, training attendance, instructor management, training cost, and learning feedback.

## 2. Core Objectives

- Maintain course catalog.
- Assign training plans.
- Manage mandatory training.
- Deliver online courses.
- Manage classroom sessions.
- Track training attendance.
- Manage certifications.
- Conduct exams and quizzes.
- Define learning paths.
- Link training to skills and competencies.
- Track compliance training.
- Manage instructors.
- Track training costs.
- Collect training feedback.
- Integrate with external LMS platforms.

## 3. LMS Setup

### Main setup areas

- Course categories.
- Course types.
- Delivery methods.
- Training providers.
- Instructors.
- Training rooms.
- Certification types.
- Exam templates.
- Quiz templates.
- Learning path templates.
- Training approval workflows.
- Attendance rules.
- Completion rules.
- Training feedback forms.
- External LMS integration settings.

## 4. Course Catalog

### Main features

- Course code.
- Course title.
- Course description.
- Course category.
- Course type.
- Delivery method.
- Duration.
- Language.
- Difficulty level.
- Prerequisites.
- Target audience.
- Required skills.
- Related competencies.
- Certification linkage.
- Course validity.
- Course owner.
- Course status.
- Attachments.
- Course materials.

### Course statuses

- Draft.
- Active.
- Inactive.
- Retired.
- Archived.

## 5. Training Delivery Methods

### Supported methods

- Online self-paced course.
- Instructor-led classroom course.
- Virtual instructor-led course.
- Blended learning.
- On-the-job training.
- External training.
- Webinar.
- Workshop.
- Conference.
- Certification program.

## 6. Online Courses

### Main features

- Video lessons.
- Reading materials.
- Downloadable files.
- SCORM/xAPI support, if required.
- Lesson progress tracking.
- Resume learning.
- Completion rules.
- Quiz after lesson.
- Course certificate.
- Mobile learning.

## 7. Classroom Training

### Main features

- Training session creation.
- Session date/time.
- Location.
- Room.
- Instructor.
- Capacity.
- Waiting list.
- Attendance sheet.
- Training materials.
- Session cancellation.
- Session rescheduling.
- Participant communication.

## 8. Training Plans

### Main features

- Individual training plan.
- Department training plan.
- Position-based training plan.
- Job-based training plan.
- Compliance training plan.
- Annual training plan.
- New hire training plan.
- Development training plan.
- Training plan approval.
- Training plan budget.

## 9. Mandatory Training

### Main features

- Mandatory course assignment.
- Mandatory by job.
- Mandatory by position.
- Mandatory by department.
- Mandatory by location.
- Mandatory by legal entity.
- Due date.
- Reminder.
- Escalation.
- Non-compliance report.

### Examples

- Safety training.
- Data privacy training.
- Food safety training.
- Anti-harassment training.
- Cash handling training.
- Cybersecurity training.

## 10. Training Assignment and Enrollment

### Main features

- Employee self-enrollment.
- Manager assignment.
- HR assignment.
- Auto-assignment by position.
- Auto-assignment by skill gap.
- Enrollment approval.
- Waitlist.
- Cancellation.
- Rescheduling.
- Enrollment history.

## 11. Training Attendance

### Main features

- Attendance marking.
- QR attendance.
- Instructor attendance entry.
- Employee check-in.
- Attendance approval.
- No-show tracking.
- Late attendance.
- Attendance certificate eligibility.

## 12. Exams and Quizzes

### Main features

- Question bank.
- Multiple choice questions.
- True/false questions.
- Short answer questions.
- Essay questions.
- Practical assessment.
- Randomized questions.
- Passing score.
- Attempt limits.
- Time limit.
- Auto-grading.
- Manual grading.
- Exam result history.

## 13. Certifications

### Main features

- Certification requirement.
- Certification issue date.
- Certification expiry date.
- Renewal rule.
- Certification exam.
- Certificate document.
- Certificate number.
- Issuing authority.
- Expiry alerts.
- Compliance report.

## 14. Learning Paths

### Main features

- Learning path template.
- Sequential courses.
- Optional courses.
- Mandatory courses.
- Milestones.
- Completion percentage.
- Role-based learning path.
- Career path-linked learning.
- Skill-based learning path.

## 15. Skill-Based Training

### Main features

- Link courses to skills.
- Link courses to competencies.
- Skill gap analysis.
- Recommended courses.
- Required training by skill level.
- Skill improvement tracking.

## 16. Compliance Training

### Main features

- Compliance course assignment.
- Regulatory training.
- Expiry tracking.
- Renewal reminders.
- Compliance dashboard.
- Audit report.
- Legal entity/location-specific requirements.

## 17. Training Calendar

### Main features

- Training calendar view.
- Session schedule.
- Department calendar.
- Employee calendar.
- Instructor calendar.
- Room calendar.
- Conflict detection.
- Calendar integration.

## 18. Instructor Management

### Main features

- Internal instructors.
- External instructors.
- Instructor profile.
- Instructor availability.
- Instructor qualifications.
- Instructor rating.
- Instructor cost.
- Instructor schedule.

## 19. Training Cost Tracking

### Main features

- Course cost.
- Instructor cost.
- Venue cost.
- Material cost.
- Travel cost.
- Certification fee.
- External provider cost.
- Cost by employee.
- Cost by department.
- Training budget vs actual.

## 20. Training Feedback

### Main features

- Course feedback form.
- Instructor feedback.
- Training quality rating.
- Content rating.
- Facility rating.
- Open comments.
- Anonymous feedback option.
- Feedback analytics.

## 21. External LMS Integration

### Main features

- External LMS connector.
- Course import.
- Enrollment sync.
- Completion sync.
- Certification sync.
- SSO integration.
- SCORM/xAPI/LTI support, if required.
- Integration logs.
- Retry handling.

## 22. Reports and Dashboards

### Dashboards

- Training completion rate.
- Mandatory training compliance.
- Certifications expiring soon.
- Training cost by department.
- Training attendance.
- Course ratings.
- Skill gap training progress.

### Reports

- Course catalog report.
- Training enrollment report.
- Training attendance report.
- Completion report.
- Certification report.
- Expired certification report.
- Mandatory training compliance report.
- Training cost report.
- Instructor performance report.
- Training feedback report.
- LMS audit report.

## 23. Security

### Requirements

- Employees view assigned courses.
- Managers view team training.
- HR/L&D manages catalog and assignments.
- Instructors manage assigned sessions.
- Finance views training costs.
- Compliance officers view compliance training.
- Tenant-level isolation for all content and learning records.

## 24. Audit Trail

Track:

- Course creation.
- Course update.
- Enrollment.
- Completion.
- Attendance update.
- Exam result update.
- Certification issue/renewal.
- Training cost changes.
- Feedback submission.
- External LMS sync.

## 25. Recommended Menu

```text
Learning Management System
├── Dashboard
├── Course Catalog
├── Training Plans
├── My Learning
├── Team Learning
├── Mandatory Training
├── Learning Paths
├── Training Calendar
├── Classroom Sessions
├── Online Courses
├── Exams & Quizzes
├── Certifications
├── Instructors
├── Training Costs
├── Feedback
├── Reports
└── Settings
```

## 26. Main Data Entities

- Course
- CourseCategory
- CourseMaterial
- TrainingPlan
- TrainingAssignment
- TrainingEnrollment
- TrainingSession
- TrainingAttendance
- Exam
- QuestionBank
- ExamAttempt
- Certification
- LearningPath
- LearningPathStep
- Instructor
- TrainingCost
- TrainingFeedback
- LMSIntegrationLog
- LMSAuditLog

---

# 15. Talent Management PRD

## 1. Purpose

The **Talent Management** module manages employee growth, talent profiles, skills, competencies, career interests, talent reviews, 9-box grid, high-potential employees, internal mobility, development plans, succession candidates, retention risk, and talent analytics.

## 2. Core Objectives

- Maintain talent profiles.
- Track skills and competencies.
- Capture career interests.
- Conduct talent reviews.
- Identify high-potential employees.
- Manage 9-box grid placement.
- Manage talent pools.
- Support internal mobility.
- Link development plans.
- Identify succession candidates.
- Track retention risk.
- Provide talent analytics.

## 3. Talent Profile

### Main features

- Employee talent summary.
- Skills.
- Competencies.
- Career interests.
- Performance history.
- Learning history.
- Certifications.
- Mobility preferences.
- Potential rating.
- Readiness rating.
- Retention risk.
- Manager comments.
- HR comments.
- Talent review history.

## 4. Skills Management

### Main features

- Skill library.
- Employee skills.
- Skill category.
- Skill proficiency level.
- Skill verification.
- Manager endorsement.
- Certification-linked skill.
- Skill history.
- Skill gap analysis.

## 5. Competency Management

### Main features

- Competency library.
- Competency model.
- Role-based competencies.
- Leadership competencies.
- Technical competencies.
- Competency assessment.
- Competency score.
- Competency gap.

## 6. Career Interests

### Main features

- Preferred job roles.
- Preferred departments.
- Preferred locations.
- Mobility willingness.
- Relocation preference.
- Career aspirations.
- Development interests.
- Internal opportunity interest.

## 7. Talent Reviews

### Main features

- Talent review cycle.
- Review population.
- Review panel.
- Manager assessment.
- HR assessment.
- Talent calibration.
- Review comments.
- Review decisions.
- Action plans.
- Confidential notes.

## 8. 9-Box Grid

### Main features

- Performance axis.
- Potential axis.
- Grid configuration.
- Employee placement.
- Calibration adjustment.
- Movement history.
- Grid comments.
- Export/reporting.

### Common categories

- High performance / high potential.
- High performance / medium potential.
- High performance / low potential.
- Medium performance / high potential.
- Medium performance / medium potential.
- Medium performance / low potential.
- Low performance / high potential.
- Low performance / medium potential.
- Low performance / low potential.

## 9. High-Potential Employees

### Main features

- High-potential flag.
- Potential rating.
- Readiness level.
- Leadership potential.
- Critical skill potential.
- Development actions.
- Retention plan.
- Executive visibility.
- Confidential access control.

## 10. Talent Pools

### Main features

- Talent pool creation.
- Talent pool category.
- Employee assignment.
- Candidate assignment.
- Pool owner.
- Pool purpose.
- Pool criteria.
- Pool review cycle.
- Pool history.

### Talent pool examples

- Leadership pipeline.
- Future store managers.
- Finance leadership.
- Technical experts.
- High-potential employees.
- Critical skills pool.
- Graduate talent.

## 11. Internal Mobility

### Main features

- Internal opportunity matching.
- Employee mobility preferences.
- Manager visibility rules.
- Internal application.
- Skill matching.
- Role readiness.
- Mobility approval.
- Transfer recommendation.

## 12. Development Plans

### Main features

- Development goals.
- Development actions.
- Learning recommendations.
- Mentoring actions.
- Stretch assignments.
- Target competencies.
- Progress tracking.
- Manager review.
- HR review.

## 13. Succession Candidate Linkage

### Main features

- Mark employee as succession candidate.
- Link employee to critical position.
- Readiness level.
- Development actions.
- Emergency successor flag.
- Succession plan history.

## 14. Retention Risk

### Main features

- Risk of loss rating.
- Impact of loss rating.
- Retention risk reason.
- Retention action plan.
- Manager input.
- HR input.
- Confidential risk notes.
- Retention dashboard.

## 15. Talent Analytics

### Dashboards

- Talent distribution.
- 9-box distribution.
- High-potential employees.
- Critical talent risk.
- Skill gaps.
- Internal mobility pipeline.
- Talent pool health.
- Development plan progress.

### Reports

- Talent profile report.
- Skill inventory report.
- Competency gap report.
- Talent review report.
- 9-box report.
- High-potential report.
- Retention risk report.
- Internal mobility report.
- Talent pool report.
- Talent audit report.

## 16. Integrations

### Integrates with

- Employee Management.
- Performance Management.
- Goals / OKR Management.
- LMS.
- Succession Planning.
- Career Development.
- Recruitment.
- Compensation.
- Analytics.

## 17. Security

### Requirements

- Talent data restricted by role.
- Potential ratings confidential.
- Retention risk confidential.
- Succession-related talent data restricted.
- Managers view only team talent data unless granted.
- HR and executives have tenant-scoped access.

## 18. Audit Trail

Track:

- Talent profile updates.
- Skill updates.
- Competency updates.
- Talent review changes.
- 9-box placement changes.
- High-potential flag changes.
- Retention risk updates.
- Talent pool assignment changes.

## 19. Recommended Menu

```text
Talent Management
├── Dashboard
├── Talent Profiles
├── Skills
├── Competencies
├── Career Interests
├── Talent Reviews
├── 9-Box Grid
├── High-Potential Employees
├── Talent Pools
├── Internal Mobility
├── Development Plans
├── Retention Risk
├── Reports
└── Settings
```

## 20. Main Data Entities

- TalentProfile
- EmployeeSkill
- EmployeeCompetency
- CareerInterest
- TalentReview
- TalentReviewPanel
- NineBoxPlacement
- HighPotentialRecord
- TalentPool
- TalentPoolMember
- InternalMobilityRecord
- DevelopmentPlan
- RetentionRiskRecord
- TalentAuditLog

---

# 16. Succession Planning PRD

## 1. Purpose

The **Succession Planning** module prepares replacements for critical positions and key roles by identifying successors, readiness levels, risk of loss, impact of loss, talent pools, leadership pipelines, replacement charts, and emergency replacement plans.

## 2. Core Objectives

- Identify critical positions.
- Nominate successors.
- Track readiness levels.
- Assess risk of loss.
- Assess impact of loss.
- Map talent pools to key roles.
- Create replacement charts.
- Approve succession plans.
- Manage leadership pipeline.
- Create emergency replacement plans.
- Track development actions for successors.

## 3. Critical Position Identification

### Main features

- Critical position flag.
- Key role flag.
- Business impact score.
- Replacement difficulty.
- Vacancy risk.
- Succession required flag.
- Position criticality reason.
- Critical position approval.

### Critical position examples

- CEO.
- CFO.
- Finance Director.
- Chief Accountant.
- IT Security Manager.
- Plant Manager.
- Store Manager.
- Production Supervisor.
- Key Sales Manager.

## 4. Succession Plan

### Main features

- Succession plan name.
- Critical position.
- Plan owner.
- Plan effective date.
- Plan review date.
- Successor list.
- Readiness status.
- Development actions.
- Emergency successor.
- Approval workflow.
- Plan status.
- Confidential notes.

### Plan statuses

- Draft.
- Submitted.
- Under review.
- Approved.
- Active.
- Needs update.
- Archived.

## 5. Successor Nomination

### Main features

- Nominate internal successor.
- Nominate external candidate, if allowed.
- Nominate emergency successor.
- Nominate multiple successors.
- Rank successors.
- Add readiness level.
- Add development needs.
- Add manager comments.
- Add HR comments.
- Approval workflow.

## 6. Readiness Levels

### Main features

- Ready now.
- Ready in less than 1 year.
- Ready in 1–2 years.
- Ready in 3+ years.
- Emergency only.
- Not ready.
- Readiness comments.
- Readiness history.

## 7. Risk of Loss and Impact of Loss

### Main features

- Risk of loss rating.
- Impact of loss rating.
- Risk reason.
- Impact reason.
- Retention action plan.
- Confidential visibility.
- Risk history.

### Rating examples

- Low.
- Medium.
- High.
- Critical.

## 8. Talent Pool Mapping

### Main features

- Link talent pool to critical role.
- Link high-potential pool.
- Link leadership pool.
- Link skill-based pool.
- Track pool readiness.
- Track pool coverage.

## 9. Replacement Chart

### Main features

- Visual replacement chart.
- Position hierarchy.
- Current incumbent.
- Successor candidates.
- Readiness level.
- Risk indicators.
- Vacancy risk.
- Export to PDF.
- Access-based visibility.

## 10. Succession Plan Approval

### Main features

- Multi-level approval.
- HR approval.
- Manager approval.
- Executive approval.
- Talent committee approval.
- Approval comments.
- Rejection reason.
- Return for correction.
- Approval history.

## 11. Leadership Pipeline

### Main features

- Leadership role levels.
- Leadership competency model.
- Pipeline candidates.
- Development actions.
- Leadership readiness.
- Training assignment.
- Mentoring assignment.
- Progress tracking.

## 12. Emergency Replacement Plan

### Main features

- Emergency successor.
- Temporary replacement.
- Acting assignment.
- Delegated authority.
- Emergency contact.
- Critical responsibility list.
- Handover documents.
- Activation workflow.

## 13. Development Actions for Successors

### Main features

- Required training.
- Mentoring.
- Coaching.
- Stretch assignment.
- Job rotation.
- Competency development.
- Certification requirement.
- Progress tracking.
- Manager review.
- HR review.

## 14. Succession Coverage Analytics

### Dashboards

- Critical positions without successors.
- Successors by readiness.
- High-risk roles.
- Leadership pipeline coverage.
- Emergency replacement coverage.
- Successor development progress.

### Reports

- Critical position report.
- Succession plan report.
- Successor readiness report.
- Risk of loss report.
- Impact of loss report.
- Replacement chart report.
- Emergency successor report.
- Succession audit report.

## 15. Integrations

### Integrates with

- Position Management.
- Employee Management.
- Talent Management.
- Performance Management.
- LMS.
- Career Development.
- Compensation.
- Recruitment.

## 16. Security

### Requirements

- Succession data is highly confidential.
- Employees should not see their own succession nomination unless tenant policy allows it.
- Managers see only authorized plans.
- HR and executives see tenant-scoped succession data.
- Risk and readiness data must be field-level secured.

## 17. Audit Trail

Track:

- Critical position flag changes.
- Successor nominations.
- Readiness changes.
- Risk changes.
- Plan approval.
- Plan rejection.
- Replacement chart updates.
- Emergency plan activation.

## 18. Recommended Menu

```text
Succession Planning
├── Dashboard
├── Critical Positions
├── Succession Plans
├── Successor Nominations
├── Readiness Review
├── Risk of Loss
├── Talent Pool Mapping
├── Replacement Chart
├── Leadership Pipeline
├── Emergency Replacement Plans
├── Development Actions
├── Reports
└── Settings
```

## 19. Main Data Entities

- CriticalPosition
- SuccessionPlan
- SuccessionCandidate
- SuccessorReadiness
- RiskOfLossRecord
- ImpactOfLossRecord
- ReplacementChart
- LeadershipPipeline
- EmergencyReplacementPlan
- SuccessorDevelopmentAction
- SuccessionApproval
- SuccessionAuditLog

---

# 17. Career Development PRD

## 1. Purpose

The **Career Development** module helps employees and managers plan career growth through career paths, career interests, skill gap analysis, development goals, mentoring, internal job recommendations, learning recommendations, competency development, career profiles, and manager development discussions.

## 2. Core Objectives

- Define career paths.
- Maintain career profiles.
- Capture career interests.
- Analyze skill gaps.
- Create development goals.
- Recommend learning.
- Recommend internal jobs.
- Support mentoring.
- Support manager development discussions.
- Track competency development.
- Link career growth to talent and succession.

## 3. Career Profile

### Main features

- Employee career summary.
- Current role.
- Career aspirations.
- Preferred roles.
- Preferred departments.
- Preferred locations.
- Mobility preferences.
- Skills.
- Competencies.
- Learning history.
- Certifications.
- Development goals.
- Manager comments.
- HR comments.

## 4. Career Paths

### Main features

- Career path library.
- Role progression.
- Vertical career path.
- Horizontal career path.
- Expert career path.
- Management career path.
- Job family path.
- Required skills.
- Required competencies.
- Required training.
- Required experience.
- Target positions.

### Examples

```text
Junior Accountant → Accountant → Senior Accountant → Chief Accountant → Finance Manager
```

```text
Sales Representative → Senior Sales Representative → Sales Supervisor → Sales Manager
```

## 5. Career Interests

### Main features

- Employee career goals.
- Desired next role.
- Desired job family.
- Desired location.
- Relocation willingness.
- Remote/hybrid preference.
- Leadership interest.
- Technical expert interest.
- Development preferences.
- Career timeline.

## 6. Skill Gap Analysis

### Main features

- Compare employee skills to target role.
- Compare employee competencies to target role.
- Identify missing skills.
- Identify missing certifications.
- Identify missing training.
- Identify experience gaps.
- Recommend development actions.
- Track gap closure.

## 7. Development Goals

### Main features

- Development goal creation.
- Goal category.
- Target skill.
- Target competency.
- Target role.
- Due date.
- Progress tracking.
- Manager review.
- HR review.
- Completion evidence.
- Link to learning.

## 8. Mentoring

### Main features

- Mentor profile.
- Mentee profile.
- Mentor matching.
- Mentoring request.
- Mentoring approval.
- Mentoring plan.
- Meeting schedule.
- Mentoring notes.
- Progress tracking.
- Mentoring feedback.

## 9. Internal Job Recommendations

### Main features

- Recommend jobs based on skills.
- Recommend jobs based on career interests.
- Recommend jobs based on career path.
- Recommend jobs based on location preference.
- Recommend jobs based on readiness.
- Link to internal recruitment portal.
- Track internal applications.

## 10. Learning Recommendations

### Main features

- Recommend courses for target role.
- Recommend courses for skill gaps.
- Recommend certifications.
- Recommend learning paths.
- Recommend compliance training.
- Track completion.

## 11. Competency Development

### Main features

- Target competency.
- Current competency level.
- Required competency level.
- Development activity.
- Assessment date.
- Manager feedback.
- Progress history.

## 12. Manager Development Discussions

### Main features

- Discussion scheduling.
- Discussion agenda.
- Employee career interests.
- Manager notes.
- Development actions.
- Follow-up tasks.
- Discussion history.
- Confidential notes.

## 13. Career Mobility

### Main features

- Internal mobility profile.
- Mobility readiness.
- Location preference.
- Department preference.
- Job family preference.
- Transfer interest.
- Promotion interest.
- Project assignment interest.

## 14. Career Development Dashboard

### Dashboards

- Employees with career profiles.
- Development goals in progress.
- Skill gaps by department.
- Career path readiness.
- Mentoring participation.
- Internal mobility interest.
- Learning recommendations completed.

### Reports

- Career profile report.
- Career interest report.
- Skill gap report.
- Development goal report.
- Mentoring report.
- Internal recommendation report.
- Career path readiness report.
- Career development audit report.

## 15. Integrations

### Integrates with

- Employee Management.
- Position Management.
- Job and Grade Structure.
- Goals / OKR Management.
- Performance Management.
- LMS.
- Talent Management.
- Succession Planning.
- Recruitment.

## 16. Security

### Requirements

- Employees manage own career profile.
- Managers view team career development.
- HR views tenant-scoped career data.
- Confidential career notes restricted.
- Internal mobility visibility configurable.
- Career interests may be hidden from current manager if tenant policy allows.

## 17. Audit Trail

Track:

- Career profile updates.
- Career interest changes.
- Career path assignment.
- Development goal updates.
- Mentoring assignment.
- Manager discussion notes.
- Internal job recommendation actions.

## 18. Recommended Menu

```text
Career Development
├── Dashboard
├── Career Profiles
├── Career Paths
├── Career Interests
├── Skill Gap Analysis
├── Development Goals
├── Mentoring
├── Internal Job Recommendations
├── Learning Recommendations
├── Competency Development
├── Manager Discussions
├── Reports
└── Settings
```

## 19. Main Data Entities

- CareerProfile
- CareerPath
- CareerPathStep
- CareerInterest
- SkillGapAnalysis
- DevelopmentGoal
- MentoringProgram
- MentorProfile
- MentoringRelationship
- InternalJobRecommendation
- LearningRecommendation
- CompetencyDevelopmentRecord
- CareerDiscussion
- CareerAuditLog

---

# Shared Technical and Architecture Requirements

## 1. API Requirements

All APIs must be tenant-aware and must enforce authorization.

### API rules

- Every request must resolve tenant context.
- API must reject missing or invalid tenant context.
- API must filter all queries by tenant ID.
- APIs must support pagination, filtering, sorting, and export permissions.
- APIs must support audit logging for sensitive changes.
- Bulk APIs must validate tenant ownership of all referenced records.

## 2. Background Jobs

### Jobs required

- Goal reminder jobs.
- OKR progress reminder jobs.
- Training due reminder jobs.
- Certification expiry jobs.
- Talent review reminder jobs.
- Succession review reminder jobs.
- Career development follow-up jobs.
- External LMS sync jobs.
- Analytics refresh jobs.

### Multi-tenant rules

- Jobs must process one tenant at a time or use strict tenant partitioning.
- Failed tenant job must not stop other tenants.
- Job logs must include tenant ID.
- Tenant-specific schedule/time zone must be supported.

## 3. Notification Requirements

### Notification channels

- In-app notification.
- Email.
- SMS.
- Push notification.
- Microsoft Teams/Slack integration, if configured.

### Multi-tenant rules

- Tenant-specific templates.
- Tenant-specific sender identity.
- Tenant-specific language.
- Tenant-specific escalation rules.
- Tenant-specific notification preferences.

## 4. Document and Attachment Requirements

### Supported attachments

- Course materials.
- Certificates.
- Evidence files.
- Goal proof documents.
- Talent review documents.
- Succession documents.
- Career discussion documents.

### Multi-tenant rules

- Files must be stored under tenant-specific storage paths or buckets.
- File access must validate tenant and role.
- Download events must be audited for sensitive files.

## 5. Reporting Requirements

### Report engine must support

- Tenant filters.
- Legal entity filters.
- Department filters.
- Manager hierarchy filters.
- Date range filters.
- Export to Excel/PDF.
- Scheduled reports.
- Dashboard widgets.
- Drill-down analytics.

## 6. Compliance and Audit Requirements

### Audit coverage

- Configuration changes.
- Workflow approvals.
- Goal scoring changes.
- Course completion changes.
- Certification updates.
- Talent review changes.
- Succession nominations.
- Career profile changes.
- Sensitive field access, where required.

## 7. Recommended Cross-Module Integrations

### Employee Management

Provides employee profile, employment status, manager, department, position, grade, legal entity, and work location.

### Organizational Management

Provides legal entity, business unit, department, branch, manager hierarchy, and security scope.

### Position Management

Provides jobs, positions, critical roles, role requirements, competencies, career paths, and succession positions.

### Performance Management

Provides performance ratings, review history, competencies, development plans, and goal scores.

### Payroll and Compensation

Uses results for merit increase, incentive planning, talent rewards, and training cost reporting.

### Recruitment

Uses career mobility, internal job recommendations, talent pools, and succession pipeline.

### Notification Engine

Sends reminders, approvals, alerts, and task notifications.

### Document Management

Stores certificates, learning materials, review documents, and evidence.

---

# Final Launch Scope Summary

These five modules must launch as full enterprise-grade, multi-tenant HCM capabilities:

## 13. Goals / OKR Management

- Company goals.
- Department goals.
- Individual goals.
- OKRs.
- KPI tracking.
- Goal alignment.
- Goal progress updates.
- Goal weighting.
- Goal approval.
- Goal scoring.
- Cascading objectives.

## 14. Learning Management System

- Course catalog.
- Training plans.
- Mandatory training.
- Online courses.
- Classroom training.
- Certifications.
- Training attendance.
- Exams and quizzes.
- Learning paths.
- Skill-based training.
- Compliance training.
- Training calendar.
- Instructor management.
- Training cost tracking.
- Training feedback.
- External LMS integration.

## 15. Talent Management

- Talent profiles.
- Skills.
- Competencies.
- Career interests.
- Talent reviews.
- 9-box grid.
- High-potential employees.
- Career development.
- Internal mobility.
- Talent pools.
- Development plans.
- Succession candidates.
- Retention risk.
- Talent analytics.

## 16. Succession Planning

- Critical position identification.
- Successor nomination.
- Readiness levels.
- Risk of loss.
- Impact of loss.
- Talent pool mapping.
- Replacement chart.
- Succession plan approval.
- Leadership pipeline.
- Emergency replacement plan.
- Development actions for successors.

## 17. Career Development

- Career paths.
- Career interests.
- Skill gap analysis.
- Development goals.
- Mentoring.
- Internal job recommendations.
- Learning recommendations.
- Competency development.
- Career profile.
- Manager development discussions.

The most important design rule:

**All goals, learning records, talent reviews, succession plans, and career development records must be tenant-isolated, workflow-controlled, audit-tracked, and integrated with employee, organization, position, performance, learning, recruitment, compensation, and reporting modules.**
