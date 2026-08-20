/**
 * HH:MM ⇄ decimal hours.
 *
 * The crews read and write "11:00", not "11". The API and the database keep
 * decimal hours (a BigDecimal quantity), so the conversion lives here at the
 * edge — one place, tested, rather than sprinkled through the grid.
 *
 * Parsing is deliberately forgiving of how people actually type: "8", "8:30",
 * "8.5", "0830" and "8:5" all mean something unambiguous. Anything else is
 * rejected rather than guessed, because a silently misread hour is a wrong
 * payslip.
 */

/** Decimal hours → "HH:MM". 1.5 → "01:30". */
export function toHhmm(hours: number | null | undefined): string {
  if (hours == null || !Number.isFinite(hours) || hours <= 0) return ''
  const totalMinutes = Math.round(hours * 60)
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/**
 * "HH:MM" → decimal hours, or `null` when the text cannot be read.
 *
 * An empty string is a cleared cell, not an error — it returns 0.
 */
export function parseHhmm(raw: string): number | null {
  const text = raw.trim()
  if (text === '') return 0

  // 8:30 / 08:30 / 8:5
  const colon = /^(\d{1,2}):(\d{1,2})$/.exec(text)
  if (colon) {
    const h = Number(colon[1])
    const m = Number(colon[2])
    if (m > 59) return null
    return round2(h + m / 60)
  }

  // 0830 — four digits, how a keypad user types it
  const compact = /^(\d{2})(\d{2})$/.exec(text)
  if (compact) {
    const m = Number(compact[2])
    if (m > 59) return null
    return round2(Number(compact[1]) + m / 60)
  }

  // 8 or 8.5 — plain hours
  const plain = /^\d{1,2}([.,]\d{1,2})?$/.exec(text)
  if (plain) return round2(Number(text.replace(',', '.')))

  return null
}

const round2 = (n: number) => Math.round(n * 100) / 100
