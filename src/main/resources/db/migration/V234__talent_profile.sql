-- HCM_15 Talent — M409 (Phase C.1)
-- Talent profile (PRD 15 §3/§9/§14) + career interests (§6). HIGHLY SENSITIVE:
-- HiPo flag, retention risk and succession-related fields are HR/executive-only;
-- the employee NEVER sees their own values (gap-check visibility default).
-- One profile per employee; career interests are employee-editable rows.

CREATE TABLE performance.talent_profile (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             varchar(64) NOT NULL DEFAULT 'default',
    employee_id           uuid NOT NULL,
    hipo_flag             boolean NOT NULL DEFAULT false,
    hipo_source           varchar(20),
    retention_risk        varchar(10),
    risk_reason           varchar(1000),
    retention_action_plan varchar(2000),
    mobility_willingness  varchar(20),
    relocation_preference varchar(300),
    career_aspirations    varchar(2000),
    notes                 varchar(2000),
    updated_by            varchar(80),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT talent_profile_employee_unique UNIQUE (tenant_id, employee_id),
    CONSTRAINT talent_profile_hipo_source_check CHECK (hipo_source IS NULL OR hipo_source IN
        ('NINE_BOX','MANUAL')),
    CONSTRAINT talent_profile_risk_check CHECK (retention_risk IS NULL OR retention_risk IN
        ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT talent_profile_mobility_check CHECK (mobility_willingness IS NULL OR
        mobility_willingness IN ('NONE','INTERNAL','RELOCATION','INTERNATIONAL'))
);

CREATE TABLE performance.career_interest (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    employee_id       uuid NOT NULL,
    target_role       varchar(200) NOT NULL,
    target_department varchar(200),
    target_location   varchar(200),
    timeline          varchar(20),
    note              varchar(1000),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT career_interest_timeline_check CHECK (timeline IS NULL OR timeline IN
        ('UNDER_1_YEAR','ONE_TO_TWO_YEARS','TWO_TO_FIVE_YEARS','LONG_TERM'))
);
CREATE INDEX career_interest_employee_idx ON performance.career_interest (tenant_id, employee_id);

COMMENT ON TABLE performance.talent_profile IS
    'HCM_15 M409 — HR-only talent profile: HiPo, retention risk, mobility (PRD 15 §3/§9/§14). Never shown to the employee.';
COMMENT ON TABLE performance.career_interest IS
    'HCM_15 M409 — employee-editable career interests (PRD 15 §6 / 17 §5).';
