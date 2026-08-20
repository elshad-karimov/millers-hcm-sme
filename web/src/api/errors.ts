/**
 * Turns an API failure into something a user can act on.
 *
 * The backend (ApiExceptionHandler) answers every failure with the same shape:
 *
 *   { status, error, message, code?, traceId?, fieldErrors? }
 *
 * so the only thing the UI has to do is not throw that away. Two cases were
 * being lost before, and both showed up as a bare "Save failed":
 *
 *   • a 500, whose default Spring body carries no `message` at all — those now
 *     carry a traceId instead, which is the one thing support needs
 *   • a 400, whose `message` is just "Validation failed" while the useful part
 *     sits in `fieldErrors`
 */

interface ApiErrorBody {
  message?: string
  traceId?: string
  fieldErrors?: Record<string, string>
}

interface ApiError {
  response?: { data?: ApiErrorBody; status?: number }
}

export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as ApiError)?.response?.data
  if (!data) return fallback

  const fields = Object.entries(data.fieldErrors ?? {})
  if (fields.length > 0) {
    // The generic "Validation failed" adds nothing next to the actual fields.
    return fields.map(([field, msg]) => `${label(field)}: ${msg}`).join('; ')
  }

  return data.message ?? fallback
}

/**
 * How long the toast should stay up, in seconds. A reference code is useless if
 * it vanishes before it can be written down.
 */
export function apiErrorDuration(err: unknown): number {
  return (err as ApiError)?.response?.data?.traceId ? 12 : 5
}

/** `personalEmail` → `Personal email`, so the toast names the field as the form does. */
function label(field: string): string {
  const spaced = field.replace(/([a-z])([A-Z])/g, '$1 $2')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}
