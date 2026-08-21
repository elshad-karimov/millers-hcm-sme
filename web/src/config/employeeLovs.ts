/**
 * Controlled value lists for the employee screen.
 *
 * From the Saipem HCM LOV Catalog (v2), which standardised what the personnel
 * workbook actually contained: 13 candidate-source labels collapse to 8, job
 * description mixed statuses with notes like "Nemat's JD", and the summarized
 * method appeared as "1 mnth" / "4 mnth" / "n/a". Free text is what produced
 * that mess, so these fields are now closed lists.
 *
 * Kept here rather than in a database master because each is a small, stable
 * set the PRD calls static. The genuinely open-ended ones — Department,
 * Position, Location, Project, Employment Classification (61 source values) —
 * are master data and stay as lookups against their own tables.
 *
 * `value` is what gets stored. The stored form is the display label because
 * the workbook's own values are already the business vocabulary, and a code
 * would have to be translated back for every report HR runs today.
 */

const opts = (...values: string[]) => values.map((v) => ({ value: v, label: v }))

/** PRD §7. "Other" is retained beyond the catalogue's Male/Female by request. */
export const GENDERS = opts('Male', 'Female', 'Other')

/** PRD §7 — standardised from 13 workbook variants. */
export const CANDIDATE_SOURCES = opts(
  'Saipem / Internal',
  'AAT-Rafi',
  'Airswift',
  'External / Direct',
  'Former Saipem',
  'Former Saipem / Agency',
  'Saipem Transfer',
  'Re-hire',
)

/** PRD §7 — status only. Notes and the document itself are held separately. */
export const JOB_DESCRIPTION_STATUSES = opts(
  'Not Provided',
  'Waiting from Saipem',
  'Will Follow',
  'Provided',
  'Printed / Acknowledged',
)

/** PRD §7 — observed in the workbook. */
export const POSITION_CLASSIFICATIONS = opts('Specialist', 'Labour')

/** PRD §7 — normalised from "1 mnth" / "4 mnth" / "n/a". */
export const TIME_ACCOUNTING_METHODS = opts('Monthly', '4-Month Summarized', 'Not Applicable')

/** PRD §7 — normalised from "12 hrs p/d" / "n/s" / "n/a". */
export const OFFSHORE_DAILY_SCHEDULES = opts('12 hrs/day', 'Not Scheduled', 'Not Applicable')

/**
 * PRD §8 — the three schedules the workbook actually uses. The PRD wants a
 * Work Schedule master that derives work time, lunch time and the offshore
 * pattern; until that master exists this keeps the wording consistent, and
 * WORK_SCHEDULE_DERIVED below fills the derived fields from the choice.
 */
export const WORK_SCHEDULES = opts(
  '5 days / 40 hrs per week',
  '5 days / 40 hrs per week / Random Offshore Trip',
  '1 day ON / 1 day OFF',
)

/**
 * PRD §9 — "Work Schedule → Work/Lunch/Offshore times: derive timing values
 * from schedule master. Do not require user to type them independently."
 *
 * This is the derivation, stated in one place. It is a stand-in for the master
 * the PRD asks for: same behaviour for the user, and the day the master lands
 * this table is what it replaces.
 */
export const WORK_SCHEDULE_DERIVED: Record<
  string,
  { workTime: string; lunchTime: string; offshore: string }
> = {
  '5 days / 40 hrs per week': {
    workTime: '8:00 - 17:00',
    lunchTime: '13:00 - 14:00',
    offshore: 'Not Applicable',
  },
  '5 days / 40 hrs per week / Random Offshore Trip': {
    workTime: '8:00 - 17:00',
    lunchTime: '13:00 - 14:00',
    offshore: '12 hrs/day',
  },
  '1 day ON / 1 day OFF': {
    workTime: '12 hours',
    lunchTime: '30 minutes',
    offshore: '12 hrs/day',
  },
}
