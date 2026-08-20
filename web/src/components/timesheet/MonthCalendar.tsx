import type { CSSProperties } from 'react'
import { Tag, Tooltip } from 'antd'
import type { DayView } from '../../api/selfTimesheet'
import { WORK_TYPE_LABELS } from '../../api/selfTimesheet'

/**
 * The month at a glance.
 *
 * The point of this view is finding the gaps: a missing day on a 31-cell grid
 * is obvious in a way that a missing row in a 31-row table is not, and a
 * missing day is what blocks submission at the end of the month.
 */
export function MonthCalendar({
  days,
  onOpen,
}: {
  days: DayView[]
  onOpen: (date: string) => void
}) {
  // An empty month means the month never loaded — a real month always has a
  // cell per date. Saying so beats rendering nothing, which looks like a
  // finished timesheet with no days in it.
  if (days.length === 0) {
    return (
      <div style={{ padding: '32px 0', textAlign: 'center', color: 'rgba(0,0,0,0.45)' }}>
        No days to show for this month yet.
      </div>
    )
  }

  // Lead the grid with blanks so the 1st lands under its real weekday.
  const first = new Date(days[0].date)
  const leading = (first.getDay() + 6) % 7   // Monday-first

  return (
    <div>
      <div style={grid}>
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => (
          <div key={d} style={headerCell}>{d}</div>
        ))}
        {Array.from({ length: leading }, (_, i) => <div key={`blank-${i}`} />)}
        {days.map((day) => {
          const state = stateOf(day)
          return (
            <Tooltip key={day.date} title={tooltip(day)}>
              <div
                role="button"
                tabIndex={0}
                onClick={() => onOpen(day.date)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') onOpen(day.date)
                }}
                style={{ ...cell, borderColor: state.border, background: state.background }}
              >
                <div style={dayNumber}>{new Date(day.date).getDate()}</div>
                {day.workType && (
                  <div style={typeLabel}>{WORK_TYPE_LABELS[day.workType]}</div>
                )}
                {hoursOf(day) > 0 && <div style={hoursLabel}>{hoursOf(day)} h</div>}
                {state.badge && (
                  <Tag color={state.badgeColor} style={badge}>{state.badge}</Tag>
                )}
              </div>
            </Tooltip>
          )
        })}
      </div>

      <div style={legend}>
        {[
          ['Complete', '#f6ffed', '#b7eb8f'],
          ['Missing', '#fff2f0', '#ffccc7'],
          ['Leave', '#e6f4ff', '#91caff'],
          ['Holiday', '#fffbe6', '#ffe58f'],
          ['Not required', '#fafafa', '#f0f0f0'],
        ].map(([text, bg, border]) => (
          <span key={text} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 12, height: 12, borderRadius: 3, background: bg, border: `1px solid ${border}` }} />
            <span style={{ fontSize: 12, color: 'rgba(0,0,0,0.55)' }}>{text}</span>
          </span>
        ))}
      </div>
    </div>
  )
}

function hoursOf(day: DayView): number {
  // Derived quantities re-classify hours already counted, so summing them too
  // would double the day — the same trap the server-side validator has.
  return day.quantities
    .filter((q) => !q.derived && q.unit === 'HOURS')
    .reduce((sum, q) => sum + q.quantity, 0)
}

function stateOf(day: DayView) {
  const blocking = day.findings.some((f) => f.severity === 'BLOCKING')
  const warning = day.findings.some((f) => f.severity === 'WARNING')

  if (day.leaveRequestId) {
    return { background: '#e6f4ff', border: '#91caff', badge: 'Leave', badgeColor: 'blue' }
  }
  if (blocking) {
    return { background: '#fff2f0', border: '#ff7875', badge: 'Fix', badgeColor: 'red' }
  }
  if (day.workType) {
    return {
      background: '#f6ffed', border: '#b7eb8f',
      badge: warning ? 'Check' : null, badgeColor: 'orange',
    }
  }
  if (day.holiday) {
    return { background: '#fffbe6', border: '#ffe58f', badge: 'Holiday', badgeColor: 'gold' }
  }
  if (day.scheduledWorkingDay) {
    return { background: '#fff2f0', border: '#ffccc7', badge: 'Missing', badgeColor: 'red' }
  }
  return { background: '#fafafa', border: '#f0f0f0', badge: null, badgeColor: undefined }
}

function tooltip(day: DayView): string {
  if (day.readOnlyReason) return day.readOnlyReason
  if (day.findings.length > 0) return day.findings.map((f) => f.message).join(' ')
  if (day.workType) return `${WORK_TYPE_LABELS[day.workType]} — ${hoursOf(day)} h`
  if (day.holiday) return 'Public holiday'
  if (day.scheduledWorkingDay) return 'No entry yet for this working day'
  return 'Not a scheduled working day'
}

const grid: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
  gap: 8,
}
const headerCell: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: '0.06em',
  color: 'rgba(0,0,0,0.45)',
  paddingBottom: 4,
  textAlign: 'center',
}
const cell: CSSProperties = {
  position: 'relative',
  minHeight: 84,
  padding: '8px 8px 6px',
  borderRadius: 8,
  border: '1px solid',
  cursor: 'pointer',
  overflow: 'hidden',
}
const dayNumber: CSSProperties = { fontSize: 14, fontWeight: 600, lineHeight: 1.1 }
const typeLabel: CSSProperties = {
  fontSize: 11, color: 'rgba(0,0,0,0.65)', marginTop: 4,
  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
}
const hoursLabel: CSSProperties = { fontSize: 12, fontWeight: 600, marginTop: 2 }
const badge: CSSProperties = { position: 'absolute', right: 4, bottom: 4, margin: 0, fontSize: 10, lineHeight: '16px' }
const legend: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 16, marginTop: 16 }
