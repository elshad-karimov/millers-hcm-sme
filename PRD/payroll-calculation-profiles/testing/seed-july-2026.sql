-- ----------------------------------------------------------------------------
-- Test seed for payroll-calculation-profiles.
--
-- Creates five employees, one per scenario, with approved+locked July 2026
-- timesheets, plus a rotation excess ledger that settles in August. Everything
-- it touches is prefixed TEST- and the teardown at the bottom removes exactly
-- what this script created.
--
-- RUN THIS ON A DISPOSABLE DATABASE, not on anything holding real people.
-- It writes employees, compensation and timesheets.
--
--   psql "postgresql://hcm:hcm@localhost:5533/hcm" -f seed-july-2026.sql
--
-- Expected results are in GUIDE.md §4 and are asserted by the unit tests in
-- src/test/java/az/millers/hcm/payroll/profile/.
-- ----------------------------------------------------------------------------

BEGIN;

-- 0. Norm hours for every month this seed touches ----------------------------
-- July is seeded by V327; the rest are needed by the August settlement case.
INSERT INTO payroll.period_norm_hours (tenant_id, period_year, period_month, norm_hours)
VALUES ('default', 2026, 5, 160), ('default', 2026, 6, 168), ('default', 2026, 8, 168)
ON CONFLICT (tenant_id, period_year, period_month) DO NOTHING;

-- 1. Employees ----------------------------------------------------------------
INSERT INTO core_hr.employee
    (id, tenant_id, employee_no, first_name, last_name, hire_date, employment_status)
VALUES
    ('11111111-0000-0000-0000-000000000001', 'default', 'TEST-ONS',
     'Onshore', 'Fixed', '2020-01-01', 'ACTIVE'),
    ('11111111-0000-0000-0000-000000000002', 'default', 'TEST-ORO',
     'Onshore', 'RandomOffshore', '2020-01-01', 'ACTIVE'),
    ('11111111-0000-0000-0000-000000000003', 'default', 'TEST-ROT',
     'Offshore', 'Rotation', '2020-01-01', 'ACTIVE'),
    ('11111111-0000-0000-0000-000000000004', 'default', 'TEST-ORN',
     'Offshore', 'RandomOnshore', '2020-01-01', 'ACTIVE'),
    ('11111111-0000-0000-0000-000000000005', 'default', 'TEST-Q1',
     'Night', 'Blocker', '2020-01-01', 'ACTIVE');

-- 2. Base salary — the source of every rate ------------------------------------
INSERT INTO payroll.employee_compensation
    (employee_id, monthly_base_salary, currency, effective_from, reason)
VALUES
    ('11111111-0000-0000-0000-000000000001', 3000.00, 'AZN', '2020-01-01', 'test seed'),
    ('11111111-0000-0000-0000-000000000002', 3500.00, 'AZN', '2020-01-01', 'test seed'),
    ('11111111-0000-0000-0000-000000000003', 2210.00, 'AZN', '2020-01-01', 'test seed'),
    ('11111111-0000-0000-0000-000000000004', 2428.00, 'AZN', '2020-01-01', 'test seed'),
    ('11111111-0000-0000-0000-000000000005', 3500.00, 'AZN', '2020-01-01', 'test seed');

-- 3. Calculation profiles ------------------------------------------------------
-- The assignment is the whole point: identical hours are worth different money
-- under different contracts, so nothing is priced without one.
INSERT INTO payroll.employee_calculation_profile
    (tenant_id, employee_id, profile_code, effective_from, reason)
VALUES
    ('default', '11111111-0000-0000-0000-000000000001', 'ONSHORE_FIXED',           '2020-01-01', 'test seed'),
    ('default', '11111111-0000-0000-0000-000000000002', 'ONSHORE_RANDOM_OFFSHORE', '2020-01-01', 'test seed'),
    ('default', '11111111-0000-0000-0000-000000000003', 'OFFSHORE_ROTATION',       '2020-01-01', 'test seed'),
    ('default', '11111111-0000-0000-0000-000000000004', 'OFFSHORE_RANDOM_ONSHORE', '2020-01-01', 'test seed'),
    ('default', '11111111-0000-0000-0000-000000000005', 'ONSHORE_RANDOM_OFFSHORE', '2020-01-01', 'test seed');

-- 4. MEWA — per employee, 30% of the onshore amount ----------------------------
INSERT INTO payroll.employee_mewa_rule
    (tenant_id, employee_id, basis, rate, effective_from, reason)
VALUES ('default', '11111111-0000-0000-0000-000000000002',
        'ONSHORE_EARNING', 0.3000, '2020-01-01', 'test seed');

-- 5. July 2026 timesheets, APPROVED and LOCKED ---------------------------------
-- Payroll reads a locked attendance summary or it does not run.
INSERT INTO timesheet.timesheet
    (id, tenant_id, employee_id, period_year, period_month, status, approved_at, locked_at)
VALUES
    ('22222222-0000-0000-0000-000000000001', 'default', '11111111-0000-0000-0000-000000000001', 2026, 7, 'LOCKED', now(), now()),
    ('22222222-0000-0000-0000-000000000002', 'default', '11111111-0000-0000-0000-000000000002', 2026, 7, 'LOCKED', now(), now()),
    ('22222222-0000-0000-0000-000000000003', 'default', '11111111-0000-0000-0000-000000000003', 2026, 7, 'LOCKED', now(), now()),
    ('22222222-0000-0000-0000-000000000004', 'default', '11111111-0000-0000-0000-000000000004', 2026, 7, 'LOCKED', now(), now()),
    ('22222222-0000-0000-0000-000000000005', 'default', '11111111-0000-0000-0000-000000000005', 2026, 7, 'LOCKED', now(), now()),
    -- August, for the rotation settlement case.
    ('22222222-0000-0000-0000-000000000013', 'default', '11111111-0000-0000-0000-000000000003', 2026, 8, 'LOCKED', now(), now()),
    -- A draft month: the preview must refuse to price it.
    ('22222222-0000-0000-0000-000000000011', 'default', '11111111-0000-0000-0000-000000000001', 2026, 6, 'DRAFT', NULL, NULL);

INSERT INTO timesheet.timesheet_month_total (tenant_id, timesheet_id, category_code, quantity)
VALUES
    -- TEST-ONS: a full norm month reproduces base salary
    ('default', '22222222-0000-0000-0000-000000000001', 'ONSHORE_HOURS', 184),

    -- TEST-ORO: offshore trip + onshore + allowances + 48 excess hours + MEWA
    ('default', '22222222-0000-0000-0000-000000000002', 'OFFSHORE_HOURS', 96),
    ('default', '22222222-0000-0000-0000-000000000002', 'ONSHORE_HOURS', 136),
    ('default', '22222222-0000-0000-0000-000000000002', 'MEAL_ALLOWANCE_DAYS', 17),
    ('default', '22222222-0000-0000-0000-000000000002', 'TRANSPORT_ALLOWANCE_DAYS', 17),

    -- TEST-ROT: hours do not scale the pay, they only qualify it
    ('default', '22222222-0000-0000-0000-000000000003', 'OFFSHORE_HOURS', 96),
    ('default', '22222222-0000-0000-0000-000000000013', 'OFFSHORE_HOURS', 200),

    -- TEST-ORN: offshore hours are derived from the norm, not recorded
    ('default', '22222222-0000-0000-0000-000000000004', 'ONSHORE_HOURS', 8),

    -- TEST-Q1: night hours make the monthly excess unresolvable
    ('default', '22222222-0000-0000-0000-000000000005', 'OFFSHORE_HOURS', 12),
    ('default', '22222222-0000-0000-0000-000000000005', 'ONSHORE_HOURS', 160),
    ('default', '22222222-0000-0000-0000-000000000005', 'OFFSHORE_NIGHT_HOURS', 24),

    ('default', '22222222-0000-0000-0000-000000000011', 'ONSHORE_HOURS', 100);

-- 6. The rotation excess ledger, May–Aug 2026 -----------------------------------
-- Seeded directly because nothing in the running application writes to it yet:
-- ExcessAccumulatorService.recordMonth has no production caller. See GUIDE.md §6.
-- June is deliberately below norm, so a naive monthly-overtime rule would
-- over-pay this employee by 28 hours.
INSERT INTO payroll.excess_accumulator
    (id, tenant_id, employee_id, scheme_code, period_year, period_seq,
     period_start, period_end, settlement_year, settlement_month, status, balance_hours)
VALUES ('33333333-0000-0000-0000-000000000001', 'default',
        '11111111-0000-0000-0000-000000000003', 'OFFSHORE_4_MONTH', 2026, 2,
        '2026-05-01', '2026-08-31', 2026, 8, 'OPEN', 60);

INSERT INTO payroll.excess_accumulator_month
    (tenant_id, accumulator_id, employee_id, period_year, period_month,
     actual_hours, norm_hours, delta_hours, running_balance)
VALUES
    ('default', '33333333-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000003', 2026, 5, 180, 160,  20,  20),
    ('default', '33333333-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000003', 2026, 6, 140, 168, -28,  -8),
    ('default', '33333333-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000003', 2026, 7, 220, 184,  36,  28),
    ('default', '33333333-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000003', 2026, 8, 200, 168,  32,  60);

COMMIT;

-- ----------------------------------------------------------------------------
-- TEARDOWN — removes exactly what this script created, nothing else.
-- ----------------------------------------------------------------------------
-- BEGIN;
-- DELETE FROM payroll.excess_accumulator_month WHERE accumulator_id = '33333333-0000-0000-0000-000000000001';
-- DELETE FROM payroll.excess_accumulator       WHERE id = '33333333-0000-0000-0000-000000000001';
-- DELETE FROM timesheet.timesheet_month_total  WHERE timesheet_id::text LIKE '22222222-0000-%';
-- DELETE FROM timesheet.timesheet              WHERE id::text LIKE '22222222-0000-%';
-- DELETE FROM payroll.employee_mewa_rule            WHERE employee_id::text LIKE '11111111-0000-%';
-- DELETE FROM payroll.employee_calculation_profile  WHERE employee_id::text LIKE '11111111-0000-%';
-- DELETE FROM payroll.employee_compensation         WHERE employee_id::text LIKE '11111111-0000-%';
-- DELETE FROM core_hr.employee                      WHERE id::text LIKE '11111111-0000-%';
-- DELETE FROM payroll.period_norm_hours WHERE period_year = 2026 AND period_month IN (5,6,8);
-- COMMIT;
