/**
 * Profile tabs this edition does not show.
 *
 * The SME edition's HR data arrives as a single spreadsheet: employee master
 * data, monthly timesheets, leave entitlement and payroll. The detail tables
 * listed here have no column in that workbook and no column in
 * `EmployeeImportService`, so the only way to populate them is to type every
 * row by hand, per employee. They have sat at (0) since the system went in.
 *
 * Kept as an exclusion list rather than deleted tabs, for the same two reasons
 * as HIDDEN_SCREENS: this repo tracks the enterprise product on `upstream` and
 * deleted lines conflict on every sync, and a customer who later starts
 * tracking, say, training records needs one line removed here rather than a tab
 * rebuilt from memory.
 *
 * What is deliberately NOT here:
 *
 *  - `addresses` and `emergency` are write targets of the live self-service
 *    flow. `PersonalInfoFieldValidator` applies an approved addressLine1 / city
 *    / district / postalCode / country change to the employee's HOME address
 *    slice, and an emergency-contact change to their primary contact. Hiding
 *    those tabs would let HR approve a change they can then never see.
 *  - `certifications` drives real expiry alerting, which matters for an
 *    offshore workforce whose safety tickets expire.
 *  - `identifications` holds passports and IDs, which expire the same way.
 *
 * Presentation only — nothing is removed from the API or the database, and the
 * rows (if any exist) are still there. Companion to HIDDEN_SCREENS, which does
 * the same job for whole screens.
 */
export const HIDDEN_PROFILE_TABS: ReadonlySet<string> = new Set([
  // No dependants register in the workbook, and no benefit here is costed per
  // dependant, so nothing consumes the data.
  'dependents',
  // Education and prior employment are held in the recruitment file, not in
  // HR's register. `professionalExperienceYears` on the employment tab is the
  // one number anybody actually reports on.
  'education',
  'experience',
  // Occupational health is run by the medical provider, on their own system.
  // The tabs are gated to OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN and no
  // account here holds the OH role.
  'health',
  'vaccinations',
])
