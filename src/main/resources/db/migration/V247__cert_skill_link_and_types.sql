-- M421: Cert→skill auto-link + skill types

-- Add skill_type column to competency
ALTER TABLE learning.competency
ADD COLUMN skill_type VARCHAR(20);

COMMENT ON COLUMN learning.competency.skill_type IS 'M421: HARD_SKILL/SOFT_SKILL/TECHNICAL/BEHAVIORAL/LANGUAGE/TOOL/COMPLIANCE';

-- Add competency link to employee_certification
ALTER TABLE core_hr.employee_certification
ADD COLUMN competency_id UUID;

ALTER TABLE core_hr.employee_certification
ADD CONSTRAINT fk_employee_certification_competency
    FOREIGN KEY (competency_id) REFERENCES learning.competency(id);

CREATE INDEX idx_employee_certification_competency ON core_hr.employee_certification(competency_id);

COMMENT ON COLUMN core_hr.employee_certification.competency_id IS 'M421: Optional link to auto-award competency on cert verify';
