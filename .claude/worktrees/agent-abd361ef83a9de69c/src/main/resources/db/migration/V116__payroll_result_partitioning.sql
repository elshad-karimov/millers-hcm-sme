-- V116 — Partition payroll.payroll_result per payroll period (PRD §15.3 / §8.9.7)
--
-- Each month's payroll results land in a dedicated child partition:
-- payroll_result_YYYY_MM. This lets the DBA detach and archive historical
-- payroll data without touching the live table.
--
-- Strategy:
--   1. Add period_start DATE (first day of the payroll period) — denormalised
--      from payroll_run.period_year + period_month for partition routing.
--   2. Populate period_start for any existing rows.
--   3. Rename the non-partitioned table, recreate as PARTITION BY RANGE.
--   4. Add partitions for 2024-01 through 2027-03 + DEFAULT.
--   5. Copy existing data, drop legacy table, recreate indexes.
--
-- Unique-constraint note: PostgreSQL requires the partition key to be part of
-- every unique constraint on a partitioned table.
-- payslip_no: globally unique within a period → UNIQUE(payslip_no, period_start).
-- (run_id, employee_id): unique within a run; a run maps 1:1 to a period →
--   UNIQUE(run_id, employee_id, period_start).

-- 1. Add the denormalised period column (DEFAULT so existing rows are non-null).
ALTER TABLE payroll.payroll_result
    ADD COLUMN IF NOT EXISTS period_start DATE NOT NULL DEFAULT make_date(2020, 1, 1);

-- 2. Back-fill from the parent payroll_run.
UPDATE payroll.payroll_result r
SET    period_start = make_date(pr.period_year, pr.period_month, 1)
FROM   payroll.payroll_run pr
WHERE  r.run_id = pr.id;

-- 3. Rename legacy table.
ALTER TABLE payroll.payroll_result RENAME TO payroll_result_legacy;

-- 4. Create partitioned replacement.
CREATE TABLE payroll.payroll_result (
    id                          UUID          NOT NULL DEFAULT gen_random_uuid(),
    run_id                      UUID          NOT NULL,
    employee_id                 UUID          NOT NULL,
    payslip_no                  VARCHAR(32)   NOT NULL,
    period_start                DATE          NOT NULL,
    timesheet_id                UUID,
    base_salary                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    worked_hours                NUMERIC(9,2)  NOT NULL DEFAULT 0,
    expected_monthly_hours      NUMERIC(9,2)  NOT NULL DEFAULT 0,
    pro_ration_factor           NUMERIC(6,4)  NOT NULL DEFAULT 1,
    overtime_hours              NUMERIC(9,2)  NOT NULL DEFAULT 0,
    overtime_pay                NUMERIC(14,2) NOT NULL DEFAULT 0,
    bonus_amount                NUMERIC(14,2) NOT NULL DEFAULT 0,
    allowance_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,
    deduction_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,
    gross_amount                NUMERIC(14,2) NOT NULL DEFAULT 0,
    income_tax                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    dsmf_employee               NUMERIC(14,2) NOT NULL DEFAULT 0,
    dsmf_employer               NUMERIC(14,2) NOT NULL DEFAULT 0,
    mmi_employee                NUMERIC(14,2) NOT NULL DEFAULT 0,
    mmi_employer                NUMERIC(14,2) NOT NULL DEFAULT 0,
    unempl_employee             NUMERIC(14,2) NOT NULL DEFAULT 0,
    unempl_employer             NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_amount                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    calculation_details         JSONB,
    note                        TEXT,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id, period_start),
    UNIQUE      (payslip_no, period_start),
    UNIQUE      (run_id, employee_id, period_start)
) PARTITION BY RANGE (period_start);

-- 5. Monthly child partitions: 2024-01 through 2027-03.
DO $$
DECLARE
    m DATE := DATE '2024-01-01';
BEGIN
    WHILE m < DATE '2027-04-01' LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS payroll.payroll_result_%s
             PARTITION OF payroll.payroll_result
             FOR VALUES FROM (%L) TO (%L)',
            to_char(m, 'YYYY_MM'),
            m,
            m + INTERVAL '1 month'
        );
        m := m + INTERVAL '1 month';
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS payroll.payroll_result_default
    PARTITION OF payroll.payroll_result DEFAULT;

-- 6. Copy existing data.
INSERT INTO payroll.payroll_result (
    id, run_id, employee_id, payslip_no, period_start, timesheet_id,
    base_salary, worked_hours, expected_monthly_hours, pro_ration_factor,
    overtime_hours, overtime_pay, bonus_amount, allowance_amount, deduction_amount,
    gross_amount, income_tax, dsmf_employee, dsmf_employer, mmi_employee, mmi_employer,
    unempl_employee, unempl_employer, net_amount, calculation_details, note,
    created_at, updated_at
)
SELECT
    id, run_id, employee_id, payslip_no, period_start, timesheet_id,
    base_salary, worked_hours, expected_monthly_hours, pro_ration_factor,
    overtime_hours, overtime_pay, bonus_amount, allowance_amount, deduction_amount,
    gross_amount, income_tax, dsmf_employee, dsmf_employer, mmi_employee, mmi_employer,
    unempl_employee, unempl_employer, net_amount, calculation_details, note,
    created_at, updated_at
FROM payroll.payroll_result_legacy;

DROP TABLE payroll.payroll_result_legacy;

-- 7. Recreate indexes.
CREATE INDEX idx_pr_run      ON payroll.payroll_result (run_id);
CREATE INDEX idx_pr_employee ON payroll.payroll_result (employee_id);
CREATE INDEX idx_pr_period   ON payroll.payroll_result (period_start);
