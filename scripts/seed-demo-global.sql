-- ----------------------------------------------------------------------------
-- Global demo seed — populates the HR / recruiter / manager views with
-- enough Azerbaijani-named people, open vacancies and candidates so the
-- KPI cards and lists don't look empty in a customer pitch.
--
-- Idempotent — re-running is a no-op (every insert is keyed on a stable
-- natural key).
-- ----------------------------------------------------------------------------

BEGIN;

-- ── 1. More active employees, Azerbaijani names ────────────────────────────
INSERT INTO core_hr.employee
  (employee_no, first_name, last_name, hire_date, employment_status,
   email, phone, position_title, department_name, created_by, updated_by)
SELECT x.no, x.fn, x.ln, x.hd::date, 'ACTIVE',
       x.email, x.phone, x.title, x.dept, 'demo', 'demo'
FROM (VALUES
  ('EMP-00010'::varchar, 'Leyla'::varchar,   'Quliyeva'::varchar,    '2022-03-14'::text,
   'leyla.quliyeva@millers.az'::varchar, '+994 50 211 4488'::varchar,
   'Senior Software Engineer'::varchar, 'Engineering'::varchar),
  ('EMP-00011'::varchar, 'Tural'::varchar,   'Hüseynov'::varchar,    '2021-09-01'::text,
   'tural.huseynov@millers.az'::varchar, '+994 55 318 2270'::varchar,
   'Engineering Manager'::varchar,      'Engineering'::varchar),
  ('EMP-00012'::varchar, 'Nigar'::varchar,   'İsmayılova'::varchar,  '2023-01-23'::text,
   'nigar.ismayilova@millers.az'::varchar, '+994 70 422 9114'::varchar,
   'Product Designer'::varchar,         'Product'::varchar),
  ('EMP-00013'::varchar, 'Elnur'::varchar,   'Rzayev'::varchar,      '2020-06-18'::text,
   'elnur.rzayev@millers.az'::varchar, '+994 50 614 0033'::varchar,
   'Head of Sales'::varchar,            'Sales'::varchar),
  ('EMP-00014'::varchar, 'Aysel'::varchar,   'Bayramova'::varchar,   '2024-02-05'::text,
   'aysel.bayramova@millers.az'::varchar, '+994 55 778 8810'::varchar,
   'Finance Analyst'::varchar,          'Finance'::varchar),
  ('EMP-00015'::varchar, 'Murad'::varchar,   'Əliyev'::varchar,      '2022-11-30'::text,
   'murad.aliyev@millers.az'::varchar, '+994 70 990 1234'::varchar,
   'Data Engineer'::varchar,            'Engineering'::varchar),
  ('EMP-00016'::varchar, 'Günel'::varchar,   'Məmmədova'::varchar,   '2023-08-07'::text,
   'gunel.mammadova@millers.az'::varchar, '+994 50 224 4567'::varchar,
   'HR Business Partner'::varchar,      'People'::varchar),
  ('EMP-00017'::varchar, 'Rauf'::varchar,    'Hacıyev'::varchar,     '2024-05-13'::text,
   'rauf.haciyev@millers.az'::varchar, '+994 55 117 7301'::varchar,
   'DevOps Engineer'::varchar,          'Engineering'::varchar),
  ('EMP-00018'::varchar, 'Səbinə'::varchar,  'Nəzərova'::varchar,    '2021-11-15'::text,
   'sebine.nezerova@millers.az'::varchar, '+994 70 803 9090'::varchar,
   'Customer Success Lead'::varchar,    'Customer Success'::varchar),
  ('EMP-00019'::varchar, 'Ramin'::varchar,   'Qasımov'::varchar,     '2023-03-27'::text,
   'ramin.qasimov@millers.az'::varchar, '+994 50 339 0512'::varchar,
   'Marketing Manager'::varchar,        'Marketing'::varchar)
) AS x(no, fn, ln, hd, email, phone, title, dept)
WHERE NOT EXISTS (
  SELECT 1 FROM core_hr.employee e WHERE e.employee_no = x.no
);

-- ── 2. Open vacancies (so Recruitment isn't empty) ─────────────────────────
INSERT INTO recruitment.vacancy
  (vacancy_no, title, department, location, openings, description, requirements,
   salary_min, salary_max, currency, status, opening_date,
   created_by, updated_by, created_at, updated_at)
SELECT x.no, x.title, x.dept, x.loc, x.openings, x.descr, x.req,
       x.smin, x.smax, 'AZN', x.status, x.open::date,
       'admin', 'admin', x.created::timestamptz, x.created::timestamptz
FROM (VALUES
  ('VAC-1001'::varchar, 'Senior Backend Engineer'::varchar, 'Engineering'::varchar, 'Baku'::varchar, 2::int,
   'Java 21 + Spring Boot 3. Joining the HCM platform team to take payroll-cycle perf from 5 days to 3.'::text,
   'Java 21, Spring Boot 3, PostgreSQL, REST API design, AZ tax knowledge a plus.'::text,
   4500::numeric, 7500::numeric, 'OPEN'::varchar, '2026-05-01'::text, '2026-05-01T09:00:00+04:00'::text),
  ('VAC-1002'::varchar, 'Product Designer'::varchar, 'Product'::varchar, 'Baku'::varchar, 1::int,
   'Own end-to-end design for the manager self-service workspace.'::text,
   'Figma, design-systems chops, 3+ years in B2B SaaS, AZ + EN.'::text,
   3500::numeric, 5500::numeric, 'OPEN'::varchar, '2026-05-22'::text, '2026-05-22T09:00:00+04:00'::text),
  ('VAC-1003'::varchar, 'HR Business Partner'::varchar, 'People'::varchar, 'Baku'::varchar, 1::int,
   'Partner with Engineering & Product leadership on hiring plans, performance cycles and comp planning.'::text,
   '5+ years HRBP, fluent AZ + EN + RU, strong analytics, comfortable with Workday/HRIS migration.'::text,
   4000::numeric, 6000::numeric, 'OPEN'::varchar, '2026-06-02'::text, '2026-06-02T09:00:00+04:00'::text),
  ('VAC-1004'::varchar, 'Data Engineer'::varchar, 'Engineering'::varchar, 'Baku / Hybrid'::varchar, 1::int,
   'Build the streaming pipeline that feeds the executive dashboards in <5 min latency.'::text,
   'ClickHouse, dbt, Airflow, Kafka, SQL. ML adjacent a plus.'::text,
   5000::numeric, 8000::numeric, 'OPEN'::varchar, '2026-06-06'::text, '2026-06-06T09:00:00+04:00'::text)
) AS x(no, title, dept, loc, openings, descr, req, smin, smax, status, open, created)
WHERE NOT EXISTS (SELECT 1 FROM recruitment.vacancy v WHERE v.vacancy_no = x.no);

-- ── 3. Candidates for the pool (Recruitment "Talent pool" tile) ───────────
INSERT INTO recruitment.candidate
  (candidate_no, first_name, last_name, email, phone, source,
   experience_years, expected_salary, currency, skills, notes,
   pool_status, last_contacted_at, created_at, updated_at, created_by)
SELECT x.no, x.fn, x.ln, x.email, x.phone, x.src,
       x.exp, x.salary, 'AZN', x.skills, x.notes,
       x.pool_status, x.contacted::timestamptz, x.created::timestamptz, x.created::timestamptz, 'admin'
FROM (VALUES
  ('CAN-1001'::varchar, 'Cavid'::varchar,    'Quluzadə'::varchar,     'cavid.quluzade@example.com'::varchar, '+994 50 410 2010'::varchar,
   'LinkedIn'::varchar, 6.5::numeric, 6500::numeric,
   'Java, Spring Boot, Kotlin, AWS, PostgreSQL'::text,
   'Strong backend, ex-Pasha. Wants to join HCM team.'::text,
   'ACTIVE'::varchar, '2026-06-05T15:00:00+04:00'::text, '2026-05-04T09:00:00+04:00'::text),
  ('CAN-1002'::varchar, 'Lalə'::varchar,     'Süleymanova'::varchar,  'lale.suleymanova@example.com'::varchar, '+994 55 700 3411'::varchar,
   'Referral – Leyla Quliyeva'::varchar, 4.0::numeric, 4200::numeric,
   'Figma, UX research, design systems'::text,
   'Just left Kapital — looking for product role.'::text,
   'ACTIVE'::varchar, '2026-06-01T11:30:00+04:00'::text, '2026-05-12T14:20:00+04:00'::text),
  ('CAN-1003'::varchar, 'Orxan'::varchar,    'İbrahimov'::varchar,    'orxan.ibrahimov@example.com'::varchar, '+994 70 552 9989'::varchar,
   'jobs.az'::varchar, 8.0::numeric, 7000::numeric,
   'Java, Spring, Kafka, Docker, K8s'::text,
   'Strong DevOps lean. Available 1 month.'::text,
   'ACTIVE'::varchar, '2026-05-27T10:00:00+04:00'::text, '2026-05-08T08:55:00+04:00'::text),
  ('CAN-1004'::varchar, 'Pərvanə'::varchar,  'Vəliyeva'::varchar,     'pervane.veliyeva@example.com'::varchar, '+994 50 991 4017'::varchar,
   'LinkedIn'::varchar, 5.5::numeric, 5500::numeric,
   'Python, SQL, ClickHouse, dbt, Airflow'::text,
   'Excellent DE candidate. Reached out re: VAC-1004.'::text,
   'ACTIVE'::varchar, '2026-06-08T09:30:00+04:00'::text, '2026-06-06T12:00:00+04:00'::text),
  ('CAN-1005'::varchar, 'İlqar'::varchar,    'Şirinov'::varchar,      'ilqar.shirinov@example.com'::varchar, '+994 55 244 8800'::varchar,
   'jobs.az'::varchar, 3.5::numeric, 3800::numeric,
   'React, TypeScript, Ant Design, GraphQL'::text,
   'Junior-mid frontend. Need to recontact.'::text,
   'ACTIVE'::varchar, '2026-04-12T10:00:00+04:00'::text, '2026-04-12T10:00:00+04:00'::text),
  ('CAN-1006'::varchar, 'Mehriban'::varchar, 'Cəfərova'::varchar,     'mehriban.ceferova@example.com'::varchar, '+994 70 711 9119'::varchar,
   'Career fair'::varchar, 2.0::numeric, 3000::numeric,
   'HR Generalist, Recruiting, Onboarding'::text,
   'Promising HR junior. Cooled off — last contact 90+ days ago.'::text,
   'ACTIVE'::varchar, '2026-02-28T14:00:00+04:00'::text, '2026-02-15T09:00:00+04:00'::text)
) AS x(no, fn, ln, email, phone, src, exp, salary, skills, notes, pool_status, contacted, created)
WHERE NOT EXISTS (SELECT 1 FROM recruitment.candidate c WHERE c.candidate_no = x.no);

-- ── 4. Compensation rows so the new employees show up in salary reports ─
INSERT INTO payroll.employee_compensation (employee_id, monthly_base_salary, currency, effective_from, reason, created_by)
SELECT e.id, x.salary, 'AZN', e.hire_date, 'Demo seed', 'demo'
FROM core_hr.employee e
JOIN (VALUES
  ('EMP-00010', 5800::numeric), ('EMP-00011', 7200::numeric), ('EMP-00012', 4400::numeric),
  ('EMP-00013', 8500::numeric), ('EMP-00014', 3600::numeric), ('EMP-00015', 5200::numeric),
  ('EMP-00016', 5600::numeric), ('EMP-00017', 5000::numeric), ('EMP-00018', 5400::numeric),
  ('EMP-00019', 6100::numeric)
) AS x(no, salary) ON x.no = e.employee_no
WHERE NOT EXISTS (
  SELECT 1 FROM payroll.employee_compensation ec
  WHERE ec.employee_id = e.id AND ec.effective_from = e.hire_date
);

COMMIT;

-- Summary
SELECT 'active_employees' AS k, count(*) FROM core_hr.employee WHERE employment_status='ACTIVE'
UNION ALL SELECT 'employees_all',   count(*) FROM core_hr.employee
UNION ALL SELECT 'vacancies_open',  count(*) FROM recruitment.vacancy  WHERE status='OPEN'
UNION ALL SELECT 'candidates',      count(*) FROM recruitment.candidate
UNION ALL SELECT 'compensation_rows', count(*) FROM payroll.employee_compensation
;
