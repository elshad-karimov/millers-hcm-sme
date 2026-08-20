import { useMemo, useState } from 'react'
import { Alert, Button, Checkbox, Input, InputNumber, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  SELECTABLE_WORK_TYPES,
  WORK_TYPE_LABELS,
  type CategoryOption,
  type DayView,
  type ProjectOption,
  type QuantityInput,
  type WorkType,
} from '../../api/selfTimesheet'
import { parseHhmm, toHhmm } from './hhmm'

/**
 * The month as one editable sheet, in the agreed prototype's layout.
 *
 * <p>The important idea is that the columns are GENERIC, not one per pay
 * category. "Regular Hours" means offshore hours on an offshore day, quayside
 * hours on a quayside day and vacation hours on a leave day; the row's work
 * type decides which category the number is actually stored against. Rendering
 * a column per category instead gives sixteen mostly-empty columns and a grid
 * nobody can read — the crews' own paper form has six.
 *
 * <p>Fast entry: fill one row, tick the days that match, press Copy → Selected
 * on that row. An 8-day hitch is one row and eight ticks. Edits stay local
 * until Save, which writes every changed day in a single request.
 *
 * <p>Hours are typed and shown as HH:MM; overtime is typed in actual minutes
 * and the payable figure is calculated by the server and shown read-only.
 */

export interface TimesheetGridProps {
  days: DayView[]
  categories: CategoryOption[]
  editable: boolean
  saving: boolean
  workLocations: string[]
  projects: ProjectOption[]
  overtimeRoundingMinutes: number
  onSaveAll: (changes: GridChange[]) => Promise<void>
  onOpenDay: (date: string) => void
}

export interface GridChange {
  date: string
  workType: WorkType
  quantities: QuantityInput[]
  workLocation: string | null
  projectId: string | null
}

/* ---------------- generic columns → the categories behind them -------------- */

type GroupKey = 'regular' | 'night' | 'otMinutes' | 'ph' | 'meal' | 'transport'

/**
 * Candidate categories per generic column. The row's work type picks which one
 * is live — the first entry whose {@code appliesTo} admits that type.
 */
const GROUP_CODES: Record<GroupKey, string[]> = {
  regular: ['OFFSHORE_HOURS', 'QUAYSIDE_HOURS', 'ONSHORE_HOURS',
            'VACATION_HOURS', 'SICK_LEAVE_HOURS',
            'EDUCATION_VACATION_HOURS', 'UNPAID_VACATION_HOURS'],
  night: ['OFFSHORE_NIGHT_HOURS', 'QUAYSIDE_NIGHT_HOURS'],
  otMinutes: ['ONSHORE_OVERTIME_MINUTES'],
  ph: ['OFFSHORE_HOLIDAY_HOURS', 'QUAYSIDE_HOLIDAY_HOURS'],
  meal: ['MEAL_ALLOWANCE_DAYS'],
  transport: ['TRANSPORT_ALLOWANCE_DAYS'],
}

const ALL_GROUPED = new Set(Object.values(GROUP_CODES).flat())
/** Server-owned: shown, never typed. */
const OT_PAYABLE = 'ONSHORE_OVERTIME_HOURS'

/** One row's pending state; absent from the map = untouched. */
interface RowEdit {
  workType: WorkType | null
  /** Value per generic column — resolved to a category code only at save time. */
  values: Partial<Record<GroupKey, number>>
  /**
   * Categories with no generic column (Excess, Hotel Quarantine), carried
   * through untouched so editing a row here cannot silently drop what was
   * entered in the day drawer.
   */
  extras: Record<string, number>
  workLocation: string | null
  projectId: string | null
}

const num = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const round2 = (n: number) => Math.round(n * 100) / 100

export function TimesheetGrid({
  days, categories, editable, saving, workLocations, projects,
  overtimeRoundingMinutes, onSaveAll, onOpenDay,
}: TimesheetGridProps) {
  /**
   * Read-only until you deliberately turn editing on.
   *
   * A month of pay data behind a screenful of live dropdowns invites the
   * accidental change nobody notices — a stray scroll over a Select, a
   * mis-click on the wrong row. `editable` says the month is OPEN (draft, not
   * locked); this says the person has ASKED to change it.
   */
  const [editMode, setEditMode] = useState(false)
  const canEdit = editable && editMode
  const [edits, setEdits] = useState<Record<string, RowEdit>>({})
  const [selected, setSelected] = useState<string[]>([])
  /** Raw text per hour cell while typing, so "8:" survives mid-keystroke. */
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  const byCode = useMemo(() => {
    const m = new Map<string, CategoryOption>()
    for (const c of categories) m.set(c.code, c)
    return m
  }, [categories])

  /** Which category a generic column writes to, for this work type. */
  const codeFor = (group: GroupKey, workType: WorkType | null): string | undefined => {
    if (!workType) return undefined
    return GROUP_CODES[group].find((code) => {
      const cat = byCode.get(code)
      if (!cat || cat.derived) return false
      return cat.appliesTo.length === 0 || cat.appliesTo.includes(workType)
    })
  }

  const groupOfCode = (code: string): GroupKey | undefined =>
    (Object.keys(GROUP_CODES) as GroupKey[]).find((g) => GROUP_CODES[g].includes(code))

  /** What the server currently holds for a row, with no pending edit applied. */
  const committed = (day: DayView): RowEdit => {
    const values: Partial<Record<GroupKey, number>> = {}
    const extras: Record<string, number> = {}
    for (const q of day.quantities) {
      if (q.categoryCode === OT_PAYABLE) continue // server-owned
      const group = groupOfCode(q.categoryCode)
      if (group) values[group] = q.quantity
      else if (!ALL_GROUPED.has(q.categoryCode)) extras[q.categoryCode] = q.quantity
    }
    return {
      workType: day.workType ?? null,
      values,
      extras,
      workLocation: day.workLocation ?? null,
      projectId: day.projectId ?? null,
    }
  }

  /** Committed state overlaid with any pending edit. */
  const rowState = (day: DayView): RowEdit => edits[day.date] ?? committed(day)

  const isDirty = (day: DayView) => Boolean(edits[day.date])

  /**
   * Always derive the next state from `prev` inside the updater.
   * Reading `edits` from the render closure loses an edit whenever two cells in
   * the same row change before React re-renders — type hours, tab, type more,
   * and the first value silently vanishes.
   */
  const patch = (day: DayView, change: Partial<RowEdit>) =>
    setEdits((prev) => ({ ...prev, [day.date]: { ...(prev[day.date] ?? committed(day)), ...change } }))

  const setGroup = (day: DayView, group: GroupKey, value: number | null) =>
    setEdits((prev) => {
      const base = prev[day.date] ?? committed(day)
      const values = { ...base.values }
      if (value == null || value <= 0) delete values[group]
      else values[group] = value
      return { ...prev, [day.date]: { ...base, values } }
    })

  /* ---------------- fast entry ---------------- */

  const copyToSelected = (source: DayView) => {
    const src = rowState(source)
    const targets = days.filter(
      (d) => selected.includes(d.date) && d.date !== source.date && !d.readOnly)
    if (targets.length === 0) return
    setEdits((prev) => {
      const next = { ...prev }
      for (const t of targets) {
        // Values are generic, so a copy survives a different work type: the
        // target row re-resolves "Regular Hours" to its own category.
        next[t.date] = {
          workType: src.workType,
          values: { ...src.values },
          extras: { ...rowState(t).extras },
          workLocation: src.workLocation,
          projectId: src.projectId,
        }
      }
      return next
    })
    setSelected([])
  }

  const selectableDates = days.filter((d) => !d.readOnly).map((d) => d.date)
  const selectWeekdays = () =>
    setSelected(days.filter((d) => !d.readOnly && ![0, 6].includes(dayjs(d.date).day()))
      .map((d) => d.date))

  /* ---------------- save ---------------- */

  const pending = Object.entries(edits)
  const incomplete = pending.filter(
    ([, e]) => !e.workType && Object.values(e.values).some((v) => num(v) > 0))
  const dirtyCount = pending.length

  const save = async () => {
    const changes: GridChange[] = []
    for (const [date, e] of pending) {
      if (!e.workType) continue
      const quantities: QuantityInput[] = []
      for (const group of Object.keys(e.values) as GroupKey[]) {
        const value = num(e.values[group])
        if (value <= 0) continue
        const code = codeFor(group, e.workType)
        if (code) quantities.push({ categoryCode: code, quantity: value })
      }
      for (const [code, value] of Object.entries(e.extras)) {
        if (value > 0) quantities.push({ categoryCode: code, quantity: value })
      }
      changes.push({
        date, workType: e.workType, quantities,
        workLocation: e.workLocation, projectId: e.projectId,
      })
    }
    if (changes.length === 0) return
    await onSaveAll(changes)
    setEdits({})
    setDrafts({})
  }

  /* ---------------- cells ---------------- */

  const hoursCell = (row: DayView, group: GroupKey, label: string) => {
    const state = rowState(row)
    const code = codeFor(group, state.workType)
    const value = state.values[group]
    const max = code ? byCode.get(code)?.maxPerDay ?? 24 : 24

    if (row.readOnly || !canEdit) {
      return value ? <Typography.Text type="secondary">{toHhmm(value)}</Typography.Text>
                   : <span style={{ color: '#d9d9d9' }}>0:00</span>
    }
    const key = `${row.date}|${group}`
    const draft = drafts[key]
    const shown = draft !== undefined ? draft : (value ? toHhmm(value) : '')
    const invalid = draft !== undefined && parseHhmm(draft) === null
    // Enabled before a work type is picked: the value is held generically and
    // resolved on save, so a row can be filled in any order.
    return (
      <Input
        size="small"
        placeholder="0:00"
        status={invalid ? 'error' : undefined}
        style={{ width: '100%', textAlign: 'right' }}
        title={code ? undefined : `${label} — pick a work type to file this against`}
        value={shown}
        onChange={(e) => setDrafts((p) => ({ ...p, [key]: e.target.value }))}
        onBlur={() => {
          const text = drafts[key]
          if (text === undefined) return
          const parsed = parseHhmm(text)
          if (parsed !== null) {
            setGroup(row, group, Math.min(parsed, max))
            setDrafts((p) => { const n = { ...p }; delete n[key]; return n })
          }
        }}
      />
    )
  }

  /** A per-day entitlement is yes/no, so it is a tick box — not a dropdown of two. */
  const allowanceCell = (row: DayView, group: GroupKey) => {
    const state = rowState(row)
    const on = num(state.values[group]) > 0
    return (
      <Checkbox
        checked={on}
        disabled={!canEdit || row.readOnly}
        onChange={(e) => setGroup(row, group, e.target.checked ? 1 : null)}
      />
    )
  }

  const statusOf = (row: DayView) => {
    if (isDirty(row)) return <Tag color="blue">Edited</Tag>
    if (row.findings.some((f) => f.severity === 'BLOCKING')) return <Tag color="red">Attention</Tag>
    if (row.findings.some((f) => f.severity === 'WARNING')) return <Tag color="orange">Check</Tag>
    if (row.workType) return <Tag color="green">Saved</Tag>
    return <Tag>Empty</Tag>
  }

  const columns: ColumnsType<DayView> = [
    {
      title: 'Date', fixed: 'left', width: 118,
      render: (_, row) => (
        <Space size={4}>
          <span style={{ fontWeight: isDirty(row) ? 600 : 400 }}>
            {dayjs(row.date).format('DD MMM YYYY')}
          </span>
          {row.holiday && <Tag color="gold" style={{ marginInlineEnd: 0 }}>PH</Tag>}
        </Space>
      ),
    },
    {
      title: 'Day', width: 62,
      render: (_, row) => {
        const d = dayjs(row.date)
        const weekend = [0, 6].includes(d.day())
        return <span style={{ color: weekend ? '#8c8c8c' : undefined }}>{d.format('ddd')}</span>
      },
    },
    {
      title: 'Work Type', width: 150,
      render: (_, row) => {
        const state = rowState(row)
        if (row.readOnly) {
          return <Tooltip title={row.readOnlyReason ?? 'Locked'}><Tag>{row.workType ?? 'locked'}</Tag></Tooltip>
        }
        // View mode reads as a document, not a form full of dead controls.
        if (!canEdit) {
          return state.workType
            ? WORK_TYPE_LABELS[state.workType]
            : <span style={{ color: '#d9d9d9' }}>—</span>
        }
        return (
          <Select
            size="small" style={{ width: '100%' }} placeholder="—" allowClear
            disabled={!canEdit}
            value={state.workType ?? undefined}
            onChange={(v: WorkType | undefined) => patch(row, { workType: v ?? null })}
            options={SELECTABLE_WORK_TYPES.map((t) => ({ value: t.value, label: t.label }))}
          />
        )
      },
    },
    {
      title: 'Work Location', width: 142,
      render: (_, row) => {
        const state = rowState(row)
        if (row.readOnly || !canEdit) {
          return state.workLocation ?? <span style={{ color: '#d9d9d9' }}>—</span>
        }
        return workLocations.length > 0 ? (
          <Select
            size="small" style={{ width: '100%' }} placeholder="—" allowClear showSearch
            value={state.workLocation ?? undefined}
            onChange={(v?: string) => patch(row, { workLocation: v ?? null })}
            options={workLocations.map((l) => ({ value: l, label: l }))}
          />
        ) : (
          <Input size="small" placeholder="—" value={state.workLocation ?? ''}
                 onChange={(e) => patch(row, { workLocation: e.target.value || null })} />
        )
      },
    },
    {
      title: 'Regular Hours', width: 108, align: 'right',
      render: (_, row) => hoursCell(row, 'regular', 'Regular hours'),
    },
    {
      title: 'Night Hours', width: 102, align: 'right',
      render: (_, row) => hoursCell(row, 'night', 'Night hours'),
    },
    {
      title: (
        <Tooltip title="Actual overtime worked, in whole minutes">
          <span>OT Minutes<br /><small style={{ fontWeight: 400 }}>Actual</small></span>
        </Tooltip>
      ),
      width: 98, align: 'right',
      render: (_, row) => {
        const state = rowState(row)
        if (row.readOnly || !canEdit) {
          return num(state.values.otMinutes) || <span style={{ color: '#d9d9d9' }}>0</span>
        }
        return (
          <InputNumber
            size="small" min={0} max={720} step={5} controls={false}
            style={{ width: '100%' }} placeholder="0"
            value={state.values.otMinutes ?? null}
            onChange={(v) => setGroup(row, 'otMinutes', v as number | null)}
          />
        )
      },
    },
    {
      title: (
        <Tooltip title={overtimeRoundingMinutes > 0
          ? `Payable overtime — actual minutes rounded to the nearest ${overtimeRoundingMinutes}. Calculated, not editable.`
          : 'Payable overtime. Calculated, not editable.'}>
          <span>OT Hours<br /><small style={{ fontWeight: 400 }}>Rounded</small></span>
        </Tooltip>
      ),
      width: 98, align: 'right',
      render: (_, row) => {
        // Straight from the server: rounding overtime moves money, so it is
        // never recomputed hopefully in the browser.
        const q = row.quantities.find((x) => x.categoryCode === OT_PAYABLE)
        return (
          <span style={{
            display: 'inline-block', width: '100%', padding: '1px 7px',
            background: '#eef3f8', borderRadius: 4, color: '#475569', fontWeight: 600,
          }}>
            {q && q.quantity > 0 ? toHhmm(q.quantity) : '0:00'}
          </span>
        )
      },
    },
    {
      title: (
        <Tooltip title="Public Holiday hours — hours worked on a public holiday, which attract the holiday premium. Only enterable on a date the holiday calendar marks (shown as PH).">
          <span>Public Holiday<br /><small style={{ fontWeight: 400 }}>Hours</small></span>
        </Tooltip>
      ),
      width: 112, align: 'right',
      render: (_, row) => {
        // The server blocks holiday hours on a non-holiday date
        // (HOLIDAY_ON_NON_HOLIDAY). Greying the cell says so before the save
        // fails rather than after.
        if (!row.holiday) {
          return (
            <Tooltip title="Not a public holiday">
              <span style={{ color: '#d9d9d9' }}>—</span>
            </Tooltip>
          )
        }
        return hoursCell(row, 'ph', 'Public holiday hours')
      },
    },
    { title: 'Meal', width: 78, align: 'center', render: (_, row) => allowanceCell(row, 'meal') },
    { title: 'Transport', width: 94, align: 'center', render: (_, row) => allowanceCell(row, 'transport') },
    {
      title: 'Project / Cost Code', width: 178,
      render: (_, row) => {
        const state = rowState(row)
        if (row.readOnly || !canEdit) {
          const p = projects.find((x) => x.id === state.projectId)
          return p ? p.code : <span style={{ color: '#d9d9d9' }}>—</span>
        }
        if (projects.length === 0) {
          return (
            <Tooltip title="No projects defined for this tenant yet">
              <span style={{ color: '#d9d9d9' }}>—</span>
            </Tooltip>
          )
        }
        return (
          <Select
            size="small" style={{ width: '100%' }} placeholder="—" allowClear showSearch
            optionFilterProp="label"
            value={state.projectId ?? undefined}
            onChange={(v?: string) => patch(row, { projectId: v ?? null })}
            options={projects.map((p) => ({ value: p.id, label: `${p.code} — ${p.name}` }))}
          />
        )
      },
    },
    { title: 'Status', width: 98, render: (_, row) => statusOf(row) },
    {
      title: 'Action', fixed: 'right', width: 130,
      render: (_, row) => (
        <Space size={0}>
          <Tooltip title={selected.length
            ? `Copy this row onto ${selected.length} ticked day(s)`
            : 'Tick target days first'}>
            <Button size="small" type="link"
                    disabled={!canEdit || row.readOnly || selected.length === 0}
                    onClick={() => copyToSelected(row)}>
              Copy → Selected
            </Button>
          </Tooltip>
          <Tooltip title="Notes, attendance, other categories">
            <Button size="small" type="link" onClick={() => onOpenDay(row.date)}>⋯</Button>
          </Tooltip>
        </Space>
      ),
    },
  ]

  /* ---------------- totals ---------------- */

  const totals = useMemo(() => {
    const t = { regular: 0, night: 0, ph: 0, otMinutes: 0, meal: 0, transport: 0, otPayable: 0 }
    for (const day of days) {
      const s = rowState(day)
      t.regular = round2(t.regular + num(s.values.regular))
      t.night = round2(t.night + num(s.values.night))
      t.ph = round2(t.ph + num(s.values.ph))
      t.otMinutes += num(s.values.otMinutes)
      t.meal += num(s.values.meal)
      t.transport += num(s.values.transport)
      t.otPayable = round2(t.otPayable
        + num(day.quantities.find((q) => q.categoryCode === OT_PAYABLE)?.quantity))
    }
    return t
  }, [days, edits]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space wrap>
          {!editable ? (
            <Tag color="default">
              This month is {'\u2014'} read-only (submitted, approved or locked)
            </Tag>
          ) : !editMode ? (
            <Button type="primary" onClick={() => setEditMode(true)}>Edit timesheet</Button>
          ) : (
            <Button
              onClick={() => {
                // Leaving edit mode with unsaved work would throw it away silently.
                if (dirtyCount > 0 && !window.confirm(
                    `Discard ${dirtyCount} unsaved day(s) and leave edit mode?`)) return
                setEditMode(false); setEdits({}); setDrafts({}); setSelected([])
              }}
            >
              Done editing
            </Button>
          )}
          {editMode && <Tag color="blue">Edit mode</Tag>}
        </Space>
      </Space>

      {canEdit ? (
        <Alert
          type="info"
          showIcon
          message={
            <span>
              <strong>Fast entry:</strong> fill one row, tick the days that match, then press{' '}
              <strong>Copy → Selected</strong> on that row. Hours are HH:MM; overtime is typed in
              actual minutes and the payable figure is calculated.
            </span>
          }
        />
      ) : editable ? (
        <Alert
          type="info"
          showIcon
          message="Viewing only. Press Edit timesheet to change these days."
        />
      ) : null}

      {canEdit && (
        <Space wrap>
          <Tooltip title={
            dirtyCount === 0 ? 'Nothing changed yet — edit a row first'
              : incomplete.length > 0 ? 'Some edited rows still need a work type'
              : `Save ${dirtyCount} changed day(s)`
          }>
            <Button type="primary" onClick={save} loading={saving}
                    disabled={dirtyCount === 0 || incomplete.length > 0}>
              Save {dirtyCount > 0 ? `${dirtyCount} day${dirtyCount === 1 ? '' : 's'}` : 'changes'}
            </Button>
          </Tooltip>
          {dirtyCount > 0 && (
            <Button onClick={() => { setEdits({}); setDrafts({}) }} disabled={saving}>Discard</Button>
          )}
          <Button onClick={selectWeekdays}>Select Weekdays</Button>
          <Button onClick={() => setSelected(selectableDates)}>Select All</Button>
          <Button onClick={() => setSelected([])} disabled={selected.length === 0}>Clear Selection</Button>
          {selected.length > 0 && (
            <Typography.Text type="secondary">Selected: {selected.length} days</Typography.Text>
          )}
        </Space>
      )}

      {incomplete.length > 0 && (
        <Alert type="warning" showIcon
               message={`Pick a work type for ${incomplete.map(([d]) => dayjs(d).format('DD MMM')).join(', ')}`}
               description="Hours need to know where they were worked before they can be saved." />
      )}

      <Table
        rowKey="date"
        size="small"
        dataSource={days}
        columns={columns}
        pagination={false}
        scroll={{ x: 'max-content', y: 620 }}
        rowSelection={canEdit ? {
          selectedRowKeys: selected,
          onChange: (keys) => setSelected(keys as string[]),
          getCheckboxProps: (row) => ({ disabled: row.readOnly }),
          fixed: true,
          columnWidth: 40,
        } : undefined}
        summary={() => (
          <Table.Summary fixed>
            <Table.Summary.Row style={{ fontWeight: 700, background: '#f8fafc' }}>
              {canEdit && <Table.Summary.Cell index={0} />}
              <Table.Summary.Cell index={1}>TOTAL</Table.Summary.Cell>
              <Table.Summary.Cell index={2} />
              <Table.Summary.Cell index={3} />
              <Table.Summary.Cell index={4} />
              <Table.Summary.Cell index={5} align="right">{toHhmm(totals.regular) || '0:00'}</Table.Summary.Cell>
              <Table.Summary.Cell index={6} align="right">{toHhmm(totals.night) || '0:00'}</Table.Summary.Cell>
              <Table.Summary.Cell index={7} align="right">{totals.otMinutes || 0}</Table.Summary.Cell>
              <Table.Summary.Cell index={8} align="right">{toHhmm(totals.otPayable) || '0:00'}</Table.Summary.Cell>
              <Table.Summary.Cell index={9} align="right">{toHhmm(totals.ph) || '0:00'}</Table.Summary.Cell>
              <Table.Summary.Cell index={10} align="center">{totals.meal || '—'}</Table.Summary.Cell>
              <Table.Summary.Cell index={11} align="center">{totals.transport || '—'}</Table.Summary.Cell>
              <Table.Summary.Cell index={12} />
              <Table.Summary.Cell index={13} />
              <Table.Summary.Cell index={14} />
            </Table.Summary.Row>
          </Table.Summary>
        )}
      />

      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Excess and Hotel Quarantine hours are entered per day under <strong>⋯</strong>; they are kept
        intact when a row is edited here.
      </Typography.Text>
    </Space>
  )
}
