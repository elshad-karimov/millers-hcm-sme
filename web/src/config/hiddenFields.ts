/**
 * Individual form fields this edition does not show.
 *
 * The companion to HIDDEN_SCREENS in nav/modules.tsx: that one removes whole
 * screens, this one removes fields inside a screen we keep. Both exist because
 * the product is the enterprise HCM and this deployment is one customer whose
 * entire HR record is a spreadsheet — every field with no column behind it is
 * something a user has to look at and decide to skip.
 *
 * PRESENTATION ONLY, and deliberately so. A hidden field keeps its value: the
 * control stays mounted and registered with the form, so a value already stored
 * still round-trips through a save untouched. Nothing is dropped from the API,
 * the DTO or the database — un-hiding is a one-line change here, and the data
 * that was there is still there.
 *
 * Keyed by a stable screen key rather than a route, because one form usually
 * serves several routes (the employee form is both /employees/new and
 * /employees/:id/edit).
 */
export const HIDDEN_FIELDS: Readonly<Record<string, ReadonlySet<string>>> = {
  // New / Edit Employee. Nothing in the Saipem workbook maps to these, and this
  // customer has no separate use for them.
  'employee-form': new Set([
    'preferredName',   // display-only nickname
    'nativeLanguage',  // display-only
    'religion',        // optional everywhere; better not held at all
    'extension',       // internal phone extension
    'deskNumber',      // desk / seat
  ]),
}

/** Whether a field is switched off on a given screen. See {@link HIDDEN_FIELDS}. */
export const isHiddenField = (screen: string, field: string): boolean =>
  HIDDEN_FIELDS[screen]?.has(field) ?? false
