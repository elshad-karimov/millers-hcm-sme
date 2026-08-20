-- M150 — Employee master-data completion, driven by the customer's live
-- personnel register (Saipem AZ workforce sheet, 92 columns).
--
-- The sheet's columns were mapped against what the platform already owns.
-- Anything already modelled elsewhere is deliberately NOT duplicated here:
--
--   salary / offshore + quayside rates / hardship / lunch / transport /
--   MEWA allowances        -> payroll.employee_compensation
--                             + comp_benefits.employee_allowance
--   vacation entitlement + monthly taken days + balance
--                          -> leave_mgmt.leave_entitlement_rule,
--                             leave_balance, leave_balance_ledger
--   contract duration / start / end
--                          -> lifecycle.employment_contract
--   termination date + ground
--                          -> lifecycle.termination_request
--                             + core_hr.employee_status_history
--
-- What remains are genuine employee master-data attributes with no home.
-- All nullable; existing rows unchanged.
--
--   external_hr_id             : the customer's GHRS / legacy-HRIS number.
--                                Distinct from employee_no (ours, generated)
--                                — needed to reconcile against the source
--                                system during and after migration.
--   full_name_local            : full legal name in the local script/format
--                                ("SURNAME Name Patronymic oğlu"). Azerbaijani
--                                labour documents and state filings require the
--                                local form; first/last/middle cannot rebuild
--                                the patronymic suffix reliably.
--   position_title_local       : job title in the local language. Contracts,
--                                orders and state reporting are issued locally.
--   occupation_classification  : state occupational classifier entry
--                                ("Məşğulluq təsnifatı") — mandatory on
--                                Azerbaijani labour-contract filings.
--   position_classification    : internal grade bucket (Specialist / Manager /
--                                Worker / Director). Free-form: each tenant
--                                runs its own taxonomy.
--   work_type                  : ONSHORE / OFFSHORE / QUAYSIDE / HYBRID.
--                                Drives which compensation rate and which
--                                work-schedule pattern applies.
--   project_name               : cost-bearing project the employee is charged
--                                to. Free text here = the register's own
--                                label; timesheet.project remains the
--                                authoritative booking dimension.
--   professional_experience_years : total professional experience, used for
--                                seniority-based leave entitlement
--                                (Art. 116.1) and grading reviews.
--   job_description_status     : tracking flag for whether a signed job
--                                description is on file ("provided",
--                                "waiting from <party>", …). Compliance
--                                checklists key off this.
--
-- Approver references — all three are employee FKs, not names. The register
-- keeps them as free text; stored as references they can actually route
-- work and be hierarchy-checked.
--   timesheet_approver_id      : approves the employee's timesheets when the
--                                approver is not the line manager.
--   expense_approver_id        : approves expense claims.
--   hr_timesheet_verifier_id   : HR-side verifier who checks timesheets
--                                before payroll picks them up.
--
-- Work-schedule descriptors — the register carries these as human-readable
-- text agreed in the contract, and they are reproduced verbatim on contracts
-- and orders. They describe the agreed pattern; the attendance module
-- remains the engine that computes actual worked time.
--   work_schedule_text           : e.g. "5 days/40 hrs per week/Random Offshore trip"
--   work_time_text               : e.g. "8:00 - 17:00"
--   lunch_time_text              : e.g. "13:00 - 14:00"
--   offshore_work_schedule_text  : e.g. "12 hrs p/d"
--   summarized_period_method     : summarized working-time accounting period
--                                  (Art. 62) — e.g. "1 mnth", "EoC or FD".

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS external_hr_id                VARCHAR(40),
    ADD COLUMN IF NOT EXISTS full_name_local               VARCHAR(300),
    ADD COLUMN IF NOT EXISTS source_of_hire                VARCHAR(80),
    ADD COLUMN IF NOT EXISTS position_title_local          VARCHAR(300),
    ADD COLUMN IF NOT EXISTS occupation_classification     VARCHAR(160),
    ADD COLUMN IF NOT EXISTS position_classification       VARCHAR(60),
    ADD COLUMN IF NOT EXISTS work_type                     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS project_name                  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS professional_experience_years NUMERIC(4, 1),
    ADD COLUMN IF NOT EXISTS job_description_status        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS timesheet_approver_id         UUID,
    ADD COLUMN IF NOT EXISTS expense_approver_id           UUID,
    ADD COLUMN IF NOT EXISTS hr_timesheet_verifier_id      UUID,
    ADD COLUMN IF NOT EXISTS work_schedule_text            VARCHAR(200),
    ADD COLUMN IF NOT EXISTS work_time_text                VARCHAR(60),
    ADD COLUMN IF NOT EXISTS lunch_time_text               VARCHAR(60),
    ADD COLUMN IF NOT EXISTS offshore_work_schedule_text   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS summarized_period_method      VARCHAR(80);

-- work_type is a closed set — the compensation rate that applies (base /
-- offshore 75% / quayside 60%) is selected from it, so a typo must not be
-- storable.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_work_type;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_work_type
    CHECK (work_type IS NULL
           OR work_type IN ('ONSHORE', 'OFFSHORE', 'QUAYSIDE', 'HYBRID'));

-- Experience drives seniority leave brackets — a negative or absurd value
-- would silently inflate entitlement.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_professional_experience_years;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_professional_experience_years
    CHECK (professional_experience_years IS NULL
           OR (professional_experience_years >= 0
               AND professional_experience_years <= 70));

-- Approvers are employees. Self-approval is blocked in the service layer
-- (BadRequestException) as well, but the constraint makes it unbypassable
-- for any writer that reaches the table directly.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_approvers_not_self;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_approvers_not_self
    CHECK (timesheet_approver_id    IS DISTINCT FROM id
       AND expense_approver_id      IS DISTINCT FROM id
       AND hr_timesheet_verifier_id IS DISTINCT FROM id);

ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_timesheet_approver;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_timesheet_approver
    FOREIGN KEY (timesheet_approver_id) REFERENCES core_hr.employee (id)
    ON DELETE SET NULL;

ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_expense_approver;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_expense_approver
    FOREIGN KEY (expense_approver_id) REFERENCES core_hr.employee (id)
    ON DELETE SET NULL;

ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_hr_timesheet_verifier;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_hr_timesheet_verifier
    FOREIGN KEY (hr_timesheet_verifier_id) REFERENCES core_hr.employee (id)
    ON DELETE SET NULL;

-- Reconciliation lookups against the legacy register run by this number, and
-- it must be unique per tenant (it identifies one person in the source HRIS).
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_external_hr_id
    ON core_hr.employee (tenant_id, external_hr_id)
    WHERE external_hr_id IS NOT NULL;

-- "Who do I approve timesheets for?" — the approval inbox queries these.
CREATE INDEX IF NOT EXISTS idx_employee_timesheet_approver
    ON core_hr.employee (tenant_id, timesheet_approver_id)
    WHERE timesheet_approver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_expense_approver
    ON core_hr.employee (tenant_id, expense_approver_id)
    WHERE expense_approver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_hr_timesheet_verifier
    ON core_hr.employee (tenant_id, hr_timesheet_verifier_id)
    WHERE hr_timesheet_verifier_id IS NOT NULL;

-- Headcount + cost reports slice by project and by work type.
CREATE INDEX IF NOT EXISTS idx_employee_project_name
    ON core_hr.employee (tenant_id, project_name)
    WHERE project_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_work_type
    ON core_hr.employee (tenant_id, work_type)
    WHERE work_type IS NOT NULL;
