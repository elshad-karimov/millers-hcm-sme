import { useEffect, useMemo, useState } from 'react'
import {
  Alert, Button, Checkbox, Descriptions, Drawer, Divider, Input, InputNumber,
  Popconfirm, Select, Space, Tag, Typography,
} from 'antd'
import { CalculatorOutlined } from '@ant-design/icons'
import {
  enterableCategories, SELECTABLE_WORK_TYPES,
  type CategoryOption, type DayEntryRequest, type DayView, type QuantityInput, type WorkType,
} from '../../api/selfTimesheet'

/**
 * One day's entry.
 *
 * Shows only the fields the chosen work type allows — an offshore day never
 * asks about meal allowance — so the employee sees a handful of relevant boxes
 * instead of the workbook's full column list. Derived quantities (holiday rota,
 * nightshift) are displayed read-only: they are the system's conclusion about
 * what the employee already told it, not another thing to type.
 */
export function DayEntryDrawer({
  open,
  day,
  categories,
  editable,
  saving,
  onClose,
  onSave,
  onCopyPrevious,
  onClear,
}: {
  open: boolean
  day: DayView | null
  categories: CategoryOption[]
  editable: boolean
  saving: boolean
  onClose: () => void
  onSave: (date: string, body: DayEntryRequest) => void
  onCopyPrevious: (date: string) => void
  onClear: (date: string) => void
}) {
  const [workType, setWorkType] = useState<WorkType | undefined>()
  const [values, setValues] = useState<Record<string, number>>({})
  const [note, setNote] = useState('')
  const [explanation, setExplanation] = useState('')

  // Reload the form whenever a different day is opened.
  useEffect(() => {
    if (!day) return
    setWorkType(day.workType ?? undefined)
    const next: Record<string, number> = {}
    for (const q of day.quantities) {
      if (!q.derived) next[q.categoryCode] = q.quantity
    }
    setValues(next)
    setNote(day.note ?? '')
    setExplanation(day.varianceExplanation ?? '')
  }, [day])

  const fields = useMemo(() => enterableCategories(categories, workType), [categories, workType])
  const derived = (day?.quantities ?? []).filter((q) => q.derived)

  // Live variance against attendance, so the employee sees the gap they are
  // about to be asked to explain rather than discovering it after saving.
  const enteredHours = fields
    .filter((f) => f.unit === 'HOURS')
    .reduce((sum, f) => sum + (values[f.code] ?? 0), 0)
  const attendance = day?.attendanceHours ?? null
  const variance = attendance == null ? null : Number((enteredHours - attendance).toFixed(2))
  const needsExplanation = variance != null && Math.abs(variance) > 0.5

  const readOnly = !editable || (day?.readOnly ?? false)

  const submit = () => {
    if (!day || !workType) return
    const quantities: QuantityInput[] = fields
      .map((f) => ({ categoryCode: f.code, quantity: values[f.code] ?? 0 }))
      .filter((q) => q.quantity > 0)
    onSave(day.date, {
      workType,
      quantities,
      note: note || undefined,
      varianceExplanation: explanation || undefined,
    })
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={520}
      title={
        day && (
          <Space>
            <span>{new Date(day.date).toLocaleDateString(undefined, {
              weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
            })}</span>
            {day.holiday && <Tag color="gold">Public holiday</Tag>}
          </Space>
        )
      }
      extra={
        !readOnly && day && (
          <Space>
            <Button size="small" onClick={() => onCopyPrevious(day.date)} disabled={saving}>
              Copy previous day
            </Button>
            <Button type="primary" onClick={submit} loading={saving} disabled={!workType}>
              Save
            </Button>
          </Space>
        )
      }
    >
      {!day ? null : (
        <>
          {day.readOnly && day.readOnlyReason && (
            <Alert type="info" showIcon style={{ marginBottom: 16 }}
                   message={day.readOnlyReason}
                   description="Leave is maintained in Leave Management so the two can never disagree." />
          )}
          {!editable && !day.readOnly && (
            <Alert type="info" showIcon style={{ marginBottom: 16 }}
                   message="This month is no longer editable."
                   description="Recall the submission if you need to change it." />
          )}

          {day.findings.length > 0 && (
            <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }} size={8}>
              {day.findings.map((f, i) => (
                <Alert key={i} showIcon
                       type={f.severity === 'BLOCKING' ? 'error' : 'warning'}
                       message={f.message} />
              ))}
            </Space>
          )}

          <Typography.Text type="secondary" style={label}>WORK TYPE</Typography.Text>
          <Select
            style={{ width: '100%', marginBottom: 20 }}
            placeholder="What kind of day was this?"
            value={workType}
            disabled={readOnly}
            onChange={(v) => {
              setWorkType(v)
              setValues({})   // categories differ per work type; stale values would be invalid
            }}
            options={SELECTABLE_WORK_TYPES.map((t) => ({
              value: t.value,
              label: (
                <div>
                  <div>{t.label}</div>
                  <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>{t.hint}</div>
                </div>
              ),
            }))}
          />

          {workType && fields.length > 0 && (
            <>
              <Typography.Text type="secondary" style={label}>WORK DETAILS</Typography.Text>
              <Space direction="vertical" style={{ width: '100%', marginBottom: 20 }} size={14}>
                {fields.map((f) =>
                  // A one-per-day allowance is a yes/no question, not a number
                  // to type — nobody should key "1" to claim a meal.
                  f.unit === 'DAYS' && f.maxPerDay === 1 ? (
                    <Checkbox
                      key={f.code}
                      disabled={readOnly}
                      checked={(values[f.code] ?? 0) > 0}
                      onChange={(e) =>
                        setValues((v) => ({ ...v, [f.code]: e.target.checked ? 1 : 0 }))
                      }
                    >
                      {f.name} eligible
                    </Checkbox>
                  ) : (
                    <div key={f.code}>
                      <div style={{ fontSize: 13, marginBottom: 4 }}>{f.name}</div>
                      <InputNumber
                        style={{ width: '100%' }}
                        min={0}
                        max={f.maxPerDay}
                        step={f.unit === 'HOURS' ? 0.5 : 1}
                        disabled={readOnly}
                        addonAfter={f.unit === 'HOURS' ? 'hours' : 'days'}
                        value={values[f.code] ?? undefined}
                        onChange={(v) => setValues((s) => ({ ...s, [f.code]: Number(v ?? 0) }))}
                      />
                    </div>
                  ),
                )}
              </Space>
            </>
          )}

          {derived.length > 0 && (
            <>
              <Typography.Text type="secondary" style={label}>
                <CalculatorOutlined /> CALCULATED BY THE SYSTEM
              </Typography.Text>
              <Descriptions column={1} size="small" bordered style={{ marginBottom: 20 }}>
                {derived.map((q) => (
                  <Descriptions.Item key={q.categoryCode} label={q.categoryName}>
                    {q.quantity} {q.unit === 'HOURS' ? 'h' : 'days'}
                    <Tag style={{ marginLeft: 8 }}>{sourceLabel(q.derivedFrom)}</Tag>
                  </Descriptions.Item>
                ))}
              </Descriptions>
            </>
          )}

          <Divider style={{ margin: '4px 0 16px' }} />

          <Typography.Text type="secondary" style={label}>ATTENDANCE COMPARISON</Typography.Text>
          <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Attendance recorded">
              {attendance == null ? <Tag>No record</Tag> : `${attendance} h`}
            </Descriptions.Item>
            <Descriptions.Item label="You entered">{enteredHours} h</Descriptions.Item>
            {variance != null && (
              <Descriptions.Item label="Difference">
                <Tag color={Math.abs(variance) > 0.5 ? 'orange' : 'green'}>
                  {variance > 0 ? '+' : ''}{variance} h
                </Tag>
              </Descriptions.Item>
            )}
          </Descriptions>
          {attendance == null && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              No clock data for this day. That is expected offshore — your entry is
              still accepted, and your manager sees that there was no device record.
            </Typography.Text>
          )}
          {needsExplanation && (
            <Input.TextArea
              style={{ marginTop: 12 }}
              rows={2}
              disabled={readOnly}
              placeholder="Explain the difference from attendance"
              value={explanation}
              onChange={(e) => setExplanation(e.target.value)}
            />
          )}

          <Divider style={{ margin: '16px 0' }} />

          <Typography.Text type="secondary" style={label}>NOTES</Typography.Text>
          <Input.TextArea
            rows={2}
            disabled={readOnly}
            placeholder="Anything your manager should know about this day"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />

          {!readOnly && day.workType && (
            <Popconfirm title="Clear this day?" onConfirm={() => onClear(day.date)}>
              <Button danger type="text" style={{ marginTop: 20, paddingLeft: 0 }}>
                Clear this day
              </Button>
            </Popconfirm>
          )}
        </>
      )}
    </Drawer>
  )
}

function sourceLabel(source?: string | null): string {
  switch (source) {
    case 'HOLIDAY_CALENDAR': return 'from the holiday calendar'
    case 'SHIFT_SCHEDULE': return 'from your shift'
    case 'LEAVE': return 'from your leave request'
    default: return 'calculated'
  }
}

const label = { display: 'block', fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', marginBottom: 8 } as const
