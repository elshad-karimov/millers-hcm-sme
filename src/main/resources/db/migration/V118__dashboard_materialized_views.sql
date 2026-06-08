-- V118 — Dashboard materialized views (M177 / PRD §15.2)
--
-- PRD §15.2: "Use materialized views for dashboards."
-- These views pre-aggregate the most expensive dashboard queries.
-- They are refreshed by DashboardMvRefreshJob (nightly + on-demand).
-- Each view includes a populated_at column so the API can show data freshness.

CREATE SCHEMA IF NOT EXISTS reporting;

-- ─── 1. Monthly headcount trend ───────────────────────────────────────────────
-- Hires, terminations and running headcount (end-of-month) for the last 24 m.

CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_headcount_monthly AS
WITH months AS (
    SELECT generate_series(
               date_trunc('month', now()) - INTERVAL '23 months',
               date_trunc('month', now()),
               INTERVAL '1 month')::date AS month_start
),
hires AS (
    SELECT date_trunc('month', hire_date)::date AS m, count(*) AS cnt
    FROM   core_hr.employee
    WHERE  hire_date IS NOT NULL
    GROUP  BY 1
),
terms AS (
    SELECT date_trunc('month', effective_date)::date AS m, count(*) AS cnt
    FROM   lifecycle.termination_request
    WHERE  status = 'APPROVED'
    GROUP  BY 1
)
SELECT
    mo.month_start,
    COALESCE(h.cnt, 0)  AS new_hires,
    COALESCE(t.cnt, 0)  AS terminations,
    COALESCE(h.cnt, 0) - COALESCE(t.cnt, 0) AS net_change,
    now()               AS populated_at
FROM   months mo
LEFT JOIN hires h ON h.m = mo.month_start
LEFT JOIN terms  t ON t.m = mo.month_start
ORDER  BY mo.month_start
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_headcount_month
    ON reporting.mv_headcount_monthly (month_start);

-- ─── 2. 12-month turnover summary ────────────────────────────────────────────
-- Turnover rate per month = terminations / avg_headcount.

CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_turnover_monthly AS
WITH months AS (
    SELECT generate_series(
               date_trunc('month', now()) - INTERVAL '11 months',
               date_trunc('month', now()),
               INTERVAL '1 month')::date AS month_start
),
terms AS (
    SELECT date_trunc('month', effective_date)::date AS m,
           reason_code,
           count(*) AS cnt
    FROM   lifecycle.termination_request
    WHERE  status = 'APPROVED'
    GROUP  BY 1, 2
),
total_terms AS (
    SELECT m, sum(cnt) AS total FROM terms GROUP BY 1
),
headcount AS (
    SELECT (SELECT count(*)
            FROM core_hr.employee e
            WHERE e.hire_date <= (mo.month_start + INTERVAL '1 month - 1 day')::date
              AND NOT EXISTS (
                  SELECT 1 FROM lifecycle.termination_request t
                  WHERE t.employee_id = e.id
                    AND t.status = 'APPROVED'
                    AND t.effective_date <= (mo.month_start + INTERVAL '1 month - 1 day')::date
              )) AS cnt,
           mo.month_start
    FROM (SELECT generate_series(
                     date_trunc('month', now()) - INTERVAL '11 months',
                     date_trunc('month', now()),
                     INTERVAL '1 month')::date) AS mo(month_start)
)
SELECT
    mo.month_start,
    COALESCE(tt.total, 0)  AS terminations,
    hc.cnt                  AS end_headcount,
    CASE WHEN hc.cnt > 0
         THEN round(COALESCE(tt.total, 0)::numeric / hc.cnt * 100, 2)
         ELSE 0
    END                     AS turnover_pct,
    now()                   AS populated_at
FROM   months mo
LEFT JOIN total_terms tt ON tt.m = mo.month_start
LEFT JOIN headcount   hc ON hc.month_start = mo.month_start
ORDER  BY mo.month_start
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_turnover_month
    ON reporting.mv_turnover_monthly (month_start);

-- ─── 3. Monthly attendance summary ───────────────────────────────────────────
-- Aggregated attendance stats per month (late, absent, overtime minutes).

CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_attendance_monthly AS
SELECT
    date_trunc('month', work_date)::date             AS month_start,
    count(*)                                          AS total_days,
    sum(CASE WHEN status = 'PRESENT'  THEN 1 ELSE 0 END) AS present_days,
    sum(CASE WHEN status = 'ABSENT'   THEN 1 ELSE 0 END) AS absent_days,
    sum(CASE WHEN status = 'PARTIAL'  THEN 1 ELSE 0 END) AS partial_days,
    sum(late_minutes)                                 AS total_late_minutes,
    sum(early_minutes)                                AS total_early_minutes,
    sum(overtime_minutes)                             AS total_overtime_minutes,
    sum(worked_minutes)                               AS total_worked_minutes,
    count(DISTINCT employee_id)                       AS employee_count,
    now()                                             AS populated_at
FROM   attendance.daily_summary
WHERE  work_date >= date_trunc('month', now()) - INTERVAL '23 months'
GROUP  BY 1
ORDER  BY 1
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_attendance_month
    ON reporting.mv_attendance_monthly (month_start);

-- ─── 4. Leave balance totals (current snapshot) ──────────────────────────────
-- Total leave-balance liability by leave type across all active employees.

CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_leave_balance_totals AS
SELECT
    lt.code              AS leave_type_code,
    lt.name              AS leave_type_name,
    count(lb.id)         AS employee_count,
    sum(lb.balance_days) AS total_days,
    sum(lb.reserved_days) AS reserved_days,
    sum(lb.balance_days - lb.reserved_days) AS available_days,
    now()                AS populated_at
FROM   leave_mgmt.leave_balance lb
JOIN   leave_mgmt.leave_type lt ON lt.id = lb.leave_type_id
JOIN   core_hr.employee e ON e.id = lb.employee_id
WHERE  e.employment_status NOT IN ('TERMINATED', 'RETIRED')
GROUP  BY lt.code, lt.name
ORDER  BY lt.code
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_leave_balance_type
    ON reporting.mv_leave_balance_totals (leave_type_code);
