-- V205 — M371 salary increase letter template seed
-- Reuses the hr_letters.letter_template infrastructure built in M77/M139.

INSERT INTO hr_letters.letter_template
    (id, code, language, name, description, body, output_format, requires_approval, active, created_at, updated_at, created_by)
VALUES
(
    'cccc1111-2222-3333-4444-555566667701',
    'SALARY_INCREASE_LETTER', 'en',
    'Salary Increase Notification Letter',
    'Confirms an approved salary increase for an employee (M371).',
E'Dear {{employee.firstName}} {{employee.lastName}},

We are pleased to inform you that your salary has been increased.

Details:

  Employee No:    {{employee.employeeNo}}
  Position:       {{employee.positionTitle}}
  Department:     {{employee.departmentName}}

  Previous Salary:  {{custom.oldSalary}} {{custom.currency}}
  New Salary:       {{custom.newSalary}} {{custom.currency}}
  Increase:         {{custom.increasePct}}%

  Effective Date:   {{custom.effectiveDate}}
  Reason:           {{custom.reason}}

This change will be reflected in your next payroll period.

Congratulations on your continued contributions to the team.

Issued on {{today}}.

Sincerely,
Human Resources Department',
    'PDF', false, true, now(), now(), 'system'
),
(
    'cccc1111-2222-3333-4444-555566667702',
    'SALARY_INCREASE_LETTER', 'az',
    'Əmək Haqqı Artımı Bildirişi',
    'Təsdiqlənmiş əmək haqqı artımını təsdiq edir (M371).',
E'Hörmətli {{employee.firstName}} {{employee.lastName}},

Əmək haqqınızın artırıldığını sizə məlumat vermək bizim üçün məmnuniyyət doğurur.

Təfərrüatlar:

  İşçi nömrəsi:     {{employee.employeeNo}}
  Vəzifə:           {{employee.positionTitle}}
  Şöbə:             {{employee.departmentName}}

  Əvvəlki maaş:     {{custom.oldSalary}} {{custom.currency}}
  Yeni maaş:        {{custom.newSalary}} {{custom.currency}}
  Artım:            {{custom.increasePct}}%

  Qüvvəyə minmə:    {{custom.effectiveDate}}
  Səbəb:            {{custom.reason}}

Bu dəyişiklik növbəti əmək haqqı dövrünüzdə əks olunacaq.

Komandaya davamlı töhfələrinizlə təbriklər.

Tarix: {{today}}.

Hörmətlə,
İnsan Resursları Şöbəsi',
    'PDF', false, true, now(), now(), 'system'
)
ON CONFLICT (code, language) DO NOTHING;
