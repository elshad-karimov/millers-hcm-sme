-- M331: Attendance–payroll summary bridge
-- Per (employee, payroll_run) attendance impact breakdown for payroll approvers.
-- Computed after PayrollEngine.calculate(); wiped and recomputed on recalc.

CREATE TABLE IF NOT EXISTS attendance.attendance_payroll_summary (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    payroll_run_id           UUID NOT NULL,
    employee_id              UUID NOT NULL,
    period_year              SMALLINT NOT NULL,
    period_month             SMALLINT NOT NULL,

    -- Raw attendance aggregates for the period
    total_late_minutes       INT NOT NULL DEFAULT 0,
    total_early_minutes      INT NOT NULL DEFAULT 0,
    total_absent_days        INT NOT NULL DEFAULT 0,
    total_overtime_minutes   INT NOT NULL DEFAULT 0,

    -- Computed deduction amounts (from AttendanceDeductionBridge)
    late_deduction_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    early_leave_deduction    NUMERIC(15,2) NOT NULL DEFAULT 0,
    absence_deduction_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_deduction_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- Which policy drove the calculation (null = no policy applied)
    policy_code              VARCHAR(50),
    policy_applied           BOOLEAN NOT NULL DEFAULT FALSE,

    computed_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (payroll_run_id, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_att_payroll_summary_run
    ON attendance.attendance_payroll_summary (payroll_run_id);

CREATE INDEX IF NOT EXISTS idx_att_payroll_summary_employee
    ON attendance.attendance_payroll_summary (tenant_id, employee_id);
