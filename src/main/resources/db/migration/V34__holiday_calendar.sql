-- Holiday calendar (PRD 8.4 / 8.5 / 8.9 — milestone 38).
--
-- Public-holiday table feeding 3 downstream consumers:
--   1. Timesheet H-code (M9 — previously weekend-only): a holiday that
--      falls on a working weekday now renders H instead of W/A.
--   2. Leave spend math (M5): when `leave_type.exclude_holidays = TRUE`,
--      the day count subtracts holiday dates.
--   3. Payroll OT multipliers (future hook): a separate "holiday OT"
--      rate when worked hours land on a holiday date. Schema is shipped
--      now; the rate-card wiring is a follow-up milestone.
--
-- Jurisdiction lives at the row level so multi-country deployments can
-- coexist in the same DB. Default `AZ` (Republic of Azerbaijan — the
-- reference jurisdiction per PRD Section 1.5).
--
-- holiday_type slices the calendar:
--   PUBLIC    — set by the Labour Code (Article 105)
--   RELIGIOUS — moveable Islamic feasts (Ramazan Bayramı, Qurban Bayramı)
--   MOURNING  — national day of mourning (e.g. 20 Jan Black January)
--   COMPANY   — company-specific closures (Christmas Eve, year-end shutdown)
--
-- `recurring` is a hint for next-year backfill jobs — a true PUBLIC
-- holiday like Republic Day repeats annually, but RELIGIOUS feasts
-- move on the lunar calendar and need explicit per-year seeding.

CREATE TABLE core_hr.holiday (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction      VARCHAR(8)   NOT NULL DEFAULT 'AZ',
    holiday_date      DATE         NOT NULL,
    name              VARCHAR(200) NOT NULL,
    holiday_type      VARCHAR(32)  NOT NULL DEFAULT 'PUBLIC',
    recurring         BOOLEAN      NOT NULL DEFAULT TRUE,
    notes             TEXT,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_holiday_type CHECK (
        holiday_type IN ('PUBLIC', 'RELIGIOUS', 'MOURNING', 'COMPANY')
    ),
    UNIQUE (jurisdiction, holiday_date)
);

CREATE INDEX idx_holiday_date         ON core_hr.holiday (holiday_date);
CREATE INDEX idx_holiday_jurisd_date  ON core_hr.holiday (jurisdiction, holiday_date)
    WHERE active = TRUE;

COMMENT ON TABLE core_hr.holiday IS
    'Public + religious + company holidays. Indexed by (jurisdiction, date). Feeds Timesheet H-code, Leave spend math (when leave_type.exclude_holidays), and payroll OT multipliers.';

-- ---------------------------------------------------------------------------
-- Seed: Republic of Azerbaijan 2026
-- ---------------------------------------------------------------------------
-- Source: Labour Code of the Republic of Azerbaijan, Article 105 (Public
-- Holidays) + Article 110 (Day of Mourning).
-- Religious feasts (Ramazan Bayramı + Qurban Bayramı) are calculated
-- from the Hijri lunar calendar each year — 2026 dates per the
-- Azerbaijan State Committee for Work with Religious Organisations.
--
-- When a holiday lands on a Saturday or Sunday, Labour Code Article 105
-- gives the following Monday off in lieu. This seed reflects the
-- declared calendar dates — the timesheet engine handles the "weekend
-- fall-on-Saturday → next Monday off" rollover separately if needed in
-- a later iteration (out of scope for M38).

INSERT INTO core_hr.holiday
    (jurisdiction, holiday_date, name, holiday_type, recurring, notes)
VALUES
    ('AZ', DATE '2026-01-01', 'New Year''s Day',                  'PUBLIC',    TRUE,  'Article 105 — Yeni il bayramı'),
    ('AZ', DATE '2026-01-02', 'New Year (second day)',            'PUBLIC',    TRUE,  'Article 105 — Yeni il bayramı'),
    ('AZ', DATE '2026-01-20', 'Day of Mourning',                  'MOURNING',  TRUE,  'Article 110 — Ümummilli hüzn günü (Black January 1990)'),
    ('AZ', DATE '2026-03-08', 'International Women''s Day',       'PUBLIC',    TRUE,  'Article 105 — Qadınlar günü'),
    -- Novruz Bayramı: 5 public days. In 2026 the religious Ramazan
    -- Bayramı overlaps the first two — both are observed simultaneously.
    ('AZ', DATE '2026-03-20', 'Novruz Bayramı (day 1)',           'PUBLIC',    TRUE,  'Article 105 — Novruz spring festival'),
    ('AZ', DATE '2026-03-21', 'Ramazan Bayramı (day 1)',          'RELIGIOUS', FALSE, 'Eid al-Fitr — 1 Shawwal 1447 AH (overlaps Novruz 2026)'),
    ('AZ', DATE '2026-03-22', 'Ramazan Bayramı (day 2)',          'RELIGIOUS', FALSE, 'Eid al-Fitr — 2 Shawwal 1447 AH (overlaps Novruz 2026)'),
    ('AZ', DATE '2026-03-23', 'Novruz Bayramı (day 4)',           'PUBLIC',    TRUE,  'Article 105 — Novruz spring festival'),
    ('AZ', DATE '2026-03-24', 'Novruz Bayramı (day 5)',           'PUBLIC',    TRUE,  'Article 105 — Novruz spring festival'),
    ('AZ', DATE '2026-05-09', 'Day of Victory over Fascism',      'PUBLIC',    TRUE,  'Article 105 — Faşizm üzərində Qələbə günü'),
    ('AZ', DATE '2026-05-27', 'Qurban Bayramı (day 1)',           'RELIGIOUS', FALSE, 'Eid al-Adha — 10 Dhul-Hijjah 1447 AH'),
    ('AZ', DATE '2026-05-28', 'Republic Day',                     'PUBLIC',    TRUE,  'Article 105 — Cümhuriyyət günü (1918 declaration)'),
    -- Qurban Bayramı day 2 (29 May) collides with the weekend in 2026.
    ('AZ', DATE '2026-05-29', 'Qurban Bayramı (day 2)',           'RELIGIOUS', FALSE, 'Eid al-Adha — 11 Dhul-Hijjah 1447 AH'),
    ('AZ', DATE '2026-06-15', 'Day of National Salvation',        'PUBLIC',    TRUE,  'Article 105 — Milli Qurtuluş günü (1993)'),
    ('AZ', DATE '2026-06-26', 'Armed Forces Day',                 'PUBLIC',    TRUE,  'Article 105 — Silahlı Qüvvələr günü'),
    ('AZ', DATE '2026-10-18', 'Day of Restoration of Independence', 'PUBLIC',  TRUE,  'Article 105 — Müstəqilliyin Bərpası günü (1991)'),
    ('AZ', DATE '2026-11-08', 'Victory Day',                      'PUBLIC',    TRUE,  'Article 105 — Zəfər günü (2020 Karabakh War)'),
    ('AZ', DATE '2026-11-09', 'State Flag Day',                   'PUBLIC',    TRUE,  'Article 105 — Dövlət Bayrağı günü'),
    ('AZ', DATE '2026-11-12', 'Constitution Day',                 'PUBLIC',    TRUE,  'Article 105 — Konstitusiya günü (1995)'),
    ('AZ', DATE '2026-11-17', 'Day of National Revival',          'PUBLIC',    TRUE,  'Article 105 — Milli Dirçəliş günü'),
    ('AZ', DATE '2026-12-31', 'International Solidarity Day of Azerbaijanis', 'PUBLIC', TRUE, 'Article 105 — Dünya azərbaycanlılarının həmrəylik günü');
