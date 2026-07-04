-- ----------------------------------------------------------------------------
-- M283 — Recruitment PRD §31: offer letter templates.
--
-- Reuses the M77/M139 letter engine (hr_letters.letter_template) —
-- offer letters are just templates with code OFFER_STANDARD rendered
-- against an offer-context (offer.* / candidate.* / vacancy.* keys)
-- instead of an employee-context. Seeded bilingually: AZ first-class
-- per the PRD, EN for international hires.
-- ----------------------------------------------------------------------------

INSERT INTO hr_letters.letter_template
    (id, code, language, name, description, body, output_format, requires_approval, active, created_at, updated_at, created_by)
VALUES
(
    'eeee1111-2222-3333-4444-555566667771',
    'OFFER_STANDARD', 'en',
    'Employment Offer Letter',
    'Standard offer letter rendered from an approved offer (M283).',
E'Dear {{candidate.fullName}},\n\nWe are pleased to offer you the position of {{vacancy.title}} in our {{vacancy.department}} team{{vacancy.locationSuffix}}.\n\nTerms of the offer:\n\n  Position:        {{vacancy.title}}\n  Employment type: {{vacancy.employmentType}}\n  Start date:      {{offer.startDate}}\n  Monthly salary:  {{offer.salary}} {{offer.currency}} (gross)\n  Benefits:        {{offer.benefits}}\n\nThis offer is valid until {{offer.validUntil}}. Please confirm your acceptance by replying to this letter or contacting the HR department.\n\nWe look forward to welcoming you to the team.\n\nReference: {{offer.no}} • Issued: {{today}}',
    'PDF', false, true, now(), now(), 'system'
),
(
    'eeee1111-2222-3333-4444-555566667772',
    'OFFER_STANDARD', 'az',
    'İşə Qəbul Təklifi Məktubu',
    'Təsdiqlənmiş təklifdən hazırlanan standart təklif məktubu (M283).',
E'Hörmətli {{candidate.fullName}},\n\nSizə {{vacancy.department}} komandamızda {{vacancy.title}} vəzifəsini təklif etməkdən məmnunluq duyuruq{{vacancy.locationSuffix}}.\n\nTəklifin şərtləri:\n\n  Vəzifə:           {{vacancy.title}}\n  Məşğulluq növü:   {{vacancy.employmentType}}\n  Başlama tarixi:   {{offer.startDate}}\n  Aylıq əməkhaqqı:  {{offer.salary}} {{offer.currency}} (gross)\n  Üstünlüklər:      {{offer.benefits}}\n\nBu təklif {{offer.validUntil}} tarixinədək qüvvədədir. Qəbulunuzu bu məktuba cavab verməklə və ya İR şöbəsi ilə əlaqə saxlamaqla təsdiqləyin.\n\nSizi komandamızda görməyi səbirsizliklə gözləyirik.\n\nİstinad: {{offer.no}} • Tarix: {{today}}',
    'PDF', false, true, now(), now(), 'system'
)
ON CONFLICT (code, language) DO NOTHING;
