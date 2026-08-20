import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert, App as AntdApp, Button, Card, Checkbox, Col, DatePicker, Descriptions, Empty,
  Input, Modal, Row, Space, Spin, Statistic, Table, Tabs, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { useSearchParams } from 'react-router-dom'
import {
  selfTimesheetApi, WORK_TYPE_LABELS,
  type DayEntryRequest, type DayView, type MonthView,
} from '../api/selfTimesheet'
import { DayEntryDrawer } from '../components/timesheet/DayEntryDrawer'
import { MonthCalendar } from '../components/timesheet/MonthCalendar'
import { TimesheetGrid, type GridChange } from '../components/timesheet/TimesheetGrid'
import { toHhmm } from '../components/timesheet/hhmm'

/**
 * My Timesheet — the employee records what they did, day by day.
 *
 * Deliberately shows no money. The employee supplies quantities (hours by work
 * type, allowance eligibility); what those are worth is payroll's business and
 * is not reachable from this screen.
 */
export function MyTimesheetPage() {
  const { message } = AntdApp.useApp()
  const [params, setParams] = useSearchParams()

  const period = useMemo(() => {
    const raw = params.get('period')
    const parsed = raw ? dayjs(raw, 'YYYY-MM', true) : null
    return parsed && parsed.isValid() ? parsed : dayjs().startOf('month')
  }, [params])

  const [data, setData] = useState<MonthView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [openDate, setOpenDate] = useState<string | null>(null)
  const [submitOpen, setSubmitOpen] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [comment, setComment] = useState('')

  const year = period.year()
  const month = period.month() + 1

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await selfTimesheetApi.month(year, month))
    } catch (err) {
      // A toast is not enough here: without it the page renders as an empty
      // month, which reads as "nothing to do today" rather than "this failed".
      setError(errorOf(err, 'Could not load your timesheet'))
      setData(null)
      message.error(errorOf(err, 'Could not load your timesheet'))
    } finally {
      setLoading(false)
    }
  }, [year, month, message])

  useEffect(() => { load() }, [load])

  const mutate = async (run: () => Promise<MonthView>, success?: string) => {
    setSaving(true)
    try {
      setData(await run())
      if (success) message.success(success)
      return true
    } catch (err) {
      message.error(errorOf(err, 'Could not save'))
      return false
    } finally {
      setSaving(false)
    }
  }

  const saveDay = async (date: string, body: DayEntryRequest) => {
    if (await mutate(() => selfTimesheetApi.saveDay(year, month, date, body), 'Day saved')) {
      setOpenDate(null)
    }
  }

  /** Grid save: every edited day in one request, so the month is atomic. */
  const saveAll = async (changes: GridChange[]) => {
    await mutate(
      () => selfTimesheetApi.saveDays(year, month,
        changes.map((c) => ({
          date: c.date,
          entry: {
            workType: c.workType,
            quantities: c.quantities,
            workLocation: c.workLocation,
            projectId: c.projectId,
          },
        }))),
      `${changes.length} day${changes.length === 1 ? '' : 's'} saved`,
    )
  }

  const submit = async () => {
    if (await mutate(() => selfTimesheetApi.submit(year, month, confirmed, comment),
                     'Timesheet submitted for approval')) {
      setSubmitOpen(false)
      setConfirmed(false)
      setComment('')
    }
  }

  const days = data?.days ?? []
  const openDay = days.find((d) => d.date === openDate) ?? null

  /**
   * Where "Add entry" lands: the first scheduled working day still empty,
   * else today if it falls in this month, else the 1st. Clicking a cell works
   * too, but a grid of cells is not an obvious way to start — the button is.
   */
  const nextDayToFill = (): string | undefined => {
    const firstMissing = days.find(
      (d) => !d.workType && d.scheduledWorkingDay && !d.holiday && !d.readOnly)
    if (firstMissing) return firstMissing.date
    const today = dayjs().format('YYYY-MM-DD')
    if (days.some((d) => d.date === today && !d.readOnly)) return today
    return days.find((d) => !d.readOnly)?.date ?? days[0]?.date
  }

  const blocking = (data?.findings ?? []).filter((f) => f.severity === 'BLOCKING')
  const warnings = (data?.findings ?? []).filter((f) => f.severity === 'WARNING')

  const entered = days.filter((d) => d.workType).length
  const missing = days.filter((d) => !d.workType && d.scheduledWorkingDay && !d.holiday).length

  /**
   * The prototype's six headline figures.
   *
   * Total = regular + payable overtime. Night and public-holiday hours are
   * re-classifications of hours already counted in regular, so adding them
   * would report a month as half as long again as it was.
   */
  const summary = useMemo(() => {
    const t = data?.totals ?? {}
    const sum = (...codes: string[]) => codes.reduce((a, c) => a + (t[c] ?? 0), 0)
    return {
      regular: sum('OFFSHORE_HOURS', 'ONSHORE_HOURS', 'QUAYSIDE_HOURS',
                   'VACATION_HOURS', 'SICK_LEAVE_HOURS',
                   'EDUCATION_VACATION_HOURS', 'UNPAID_VACATION_HOURS'),
      night: sum('OFFSHORE_NIGHT_HOURS', 'QUAYSIDE_NIGHT_HOURS'),
      ph: sum('OFFSHORE_HOLIDAY_HOURS', 'QUAYSIDE_HOLIDAY_HOURS'),
      otPayable: sum('ONSHORE_OVERTIME_HOURS'),
      allowanceDays: sum('MEAL_ALLOWANCE_DAYS'),
    }
  }, [data])

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 64 }}><Spin size="large" /></div>
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]} align="middle">
          <Col flex="auto">
            <Typography.Title level={4} style={{ margin: 0 }}>My Timesheet</Typography.Title>
            <Space size={8} style={{ marginTop: 6 }}>
              <DatePicker
                picker="month"
                allowClear={false}
                value={period}
                onChange={(v: Dayjs | null) => {
                  if (v) setParams({ period: v.format('YYYY-MM') }, { replace: true })
                }}
              />
              <StatusTag status={data?.status} />
              {data?.submittedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Submitted {dayjs(data.submittedAt).format('D MMM YYYY HH:mm')}
                </Typography.Text>
              )}
            </Space>
          </Col>
          <Col>
            <Space>
              {data?.status === 'SUBMITTED' && (
                <Button
                  onClick={() => mutate(() => selfTimesheetApi.recall(year, month),
                                        'Timesheet recalled — you can edit it again')}
                  loading={saving}
                >
                  Recall
                </Button>
              )}
              {data?.editable && !error && (
                <>
                  <Button type="primary" onClick={() => setOpenDate(nextDayToFill() ?? null)}>
                    + Add entry
                  </Button>
                  <Button
                    disabled={!data.submittable}
                    onClick={() => setSubmitOpen(true)}
                  >
                    Submit for approval
                  </Button>
                </>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={[12, 12]}>
        {[
          { k: 'Total Hours', v: toHhmm(summary.regular + summary.otPayable) || '0:00' },
          { k: 'Regular Hours', v: toHhmm(summary.regular) || '0:00' },
          { k: 'Night Hours', v: toHhmm(summary.night) || '0:00' },
          { k: 'OT (Rounded)', v: toHhmm(summary.otPayable) || '0:00' },
          { k: 'PH Hours', v: toHhmm(summary.ph) || '0:00' },
          { k: 'Allowance Days', v: String(summary.allowanceDays) },
        ].map((c) => (
          <Col xs={12} sm={8} md={4} key={c.k}>
            <Card size="small"><Statistic title={c.k} value={c.v} /></Card>
          </Col>
        ))}
      </Row>

      {error && (
        <Alert
          type="error"
          showIcon
          message="Your timesheet could not be loaded"
          description={
            <>
              <div>{error}</div>
              <Button size="small" style={{ marginTop: 8 }} onClick={load}>Try again</Button>
            </>
          }
        />
      )}

      {blocking.length > 0 && (
        <Alert
          type="error"
          showIcon
          message={`${blocking.length} ${blocking.length === 1 ? 'issue' : 'issues'} must be fixed before you can submit`}
          description={
            <ul style={{ margin: '6px 0 0 16px', padding: 0 }}>
              {blocking.slice(0, 8).map((f, i) => <li key={i}>{f.message}</li>)}
              {blocking.length > 8 && <li>…and {blocking.length - 8} more.</li>}
            </ul>
          }
        />
      )}
      {blocking.length === 0 && warnings.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${warnings.length} ${warnings.length === 1 ? 'note' : 'notes'} your manager will see`}
          description={
            <ul style={{ margin: '6px 0 0 16px', padding: 0 }}>
              {warnings.slice(0, 6).map((f, i) => <li key={i}>{f.message}</li>)}
            </ul>
          }
        />
      )}

      {data?.editable && !error && entered === 0 && days.length > 0 && (
        <Alert
          type="info"
          showIcon
          message="Nothing recorded for this month yet"
          description={
            <>
              Use <strong>+ Add entry</strong> above, or click any day in the calendar,
              to record what you worked. Pick the work type first — the form then asks
              only for the hours that apply to it.
            </>
          }
        />
      )}

      <Card>
        <Tabs
          items={[
            {
              key: 'calendar',
              label: 'Calendar',
              children: <MonthCalendar days={days} onOpen={setOpenDate} />,
            },
            {
              key: 'grid',
              label: 'Detailed grid',
              children: (
                <TimesheetGrid
                  days={days}
                  categories={data?.categories ?? []}
                  editable={data?.editable ?? false}
                  saving={saving}
                  workLocations={data?.workLocations ?? []}
                  projects={data?.projects ?? []}
                  overtimeRoundingMinutes={data?.overtimeRoundingMinutes ?? 0}
                  onSaveAll={saveAll}
                  onOpenDay={setOpenDate}
                />
              ),
            },
            {
              key: 'readonly-grid',
              label: 'Day status',
              children: <DayGrid days={days} onOpen={setOpenDate} />,
            },
            {
              key: 'summary',
              label: 'Monthly summary',
              children: <SummaryTab data={data} />,
            },
          ]}
        />
      </Card>

      <DayEntryDrawer
        open={openDate != null}
        day={openDay}
        categories={data?.categories ?? []}
        editable={data?.editable ?? false}
        saving={saving}
        onClose={() => setOpenDate(null)}
        onSave={saveDay}
        onCopyPrevious={(date) =>
          mutate(() => selfTimesheetApi.copyPrevious(year, month, date), 'Previous day copied')}
        onClear={(date) =>
          mutate(() => selfTimesheetApi.clearDay(year, month, date), 'Day cleared')
            .then(() => setOpenDate(null))}
      />

      <Modal
        open={submitOpen}
        title={`Submit ${period.format('MMMM YYYY')} timesheet`}
        okText="Submit"
        okButtonProps={{ disabled: !confirmed, loading: saving }}
        onOk={submit}
        onCancel={() => setSubmitOpen(false)}
      >
        <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Period">
            {period.startOf('month').format('D MMM')} – {period.endOf('month').format('D MMM YYYY')}
          </Descriptions.Item>
          <Descriptions.Item label="Total hours">
            {toHhmm(summary.regular + summary.otPayable) || '0:00'}
          </Descriptions.Item>
          <Descriptions.Item label="Overtime (payable)">
            {toHhmm(summary.otPayable) || '0:00'}
          </Descriptions.Item>
          <Descriptions.Item label="Days not entered">{missing}</Descriptions.Item>
          <Descriptions.Item label="Notes for your manager">{warnings.length}</Descriptions.Item>
          <Descriptions.Item label="Blocking issues">{blocking.length}</Descriptions.Item>
        </Descriptions>
        <Checkbox checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)}>
          I confirm that the submitted working time is accurate.
        </Checkbox>
        <Input.TextArea
          style={{ marginTop: 12 }}
          rows={3}
          placeholder="Comments for your manager (optional)"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>
    </Space>
  )
}

// ---------------------------------------------------------------------------

function DayGrid({ days, onOpen }: { days: DayView[]; onOpen: (date: string) => void }) {
  const columns: ColumnsType<DayView> = [
    {
      title: 'Date', dataIndex: 'date', width: 130,
      render: (v: string) => dayjs(v).format('DD MMM ddd'),
    },
    {
      title: 'Work type', dataIndex: 'workType', width: 150,
      render: (v: DayView['workType'], row) =>
        v ? WORK_TYPE_LABELS[v]
          : row.holiday ? <Tag color="gold">Holiday</Tag>
          : row.scheduledWorkingDay ? <Tag color="red">Missing</Tag>
          : <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Hours', width: 90, align: 'right',
      render: (_, row) => (baseHours(row) > 0 ? round(baseHours(row)) : '—'),
    },
    {
      title: 'Calculated', width: 260,
      render: (_, row) => {
        const derived = row.quantities.filter((q) => q.derived)
        if (derived.length === 0) return <Typography.Text type="secondary">—</Typography.Text>
        return (
          <Space size={4} wrap>
            {derived.map((q) => (
              <Tag key={q.categoryCode}>{q.categoryName}: {q.quantity}</Tag>
            ))}
          </Space>
        )
      },
    },
    {
      title: 'Attendance', width: 120, align: 'right',
      render: (_, row) =>
        row.attendanceHours == null
          ? <Typography.Text type="secondary">no record</Typography.Text>
          : `${row.attendanceHours} h`,
    },
    {
      title: 'Status', width: 110,
      render: (_, row) => {
        if (row.leaveRequestId) return <Tag color="blue">Leave</Tag>
        if (row.findings.some((f) => f.severity === 'BLOCKING')) return <Tag color="red">Fix</Tag>
        if (row.findings.some((f) => f.severity === 'WARNING')) return <Tag color="orange">Check</Tag>
        if (row.workType) return <Tag color="green">Complete</Tag>
        return <Typography.Text type="secondary">—</Typography.Text>
      },
    },
  ]

  return (
    <Table
      rowKey="date"
      size="small"
      dataSource={days}
      columns={columns}
      pagination={false}
      scroll={{ x: 900 }}
      onRow={(row) => ({ onClick: () => onOpen(row.date), style: { cursor: 'pointer' } })}
    />
  )
}

/**
 * The employee-readable version of the payroll input quantities.
 *
 * This is what payroll will price — shown so the employee can check it before
 * submitting, still without a single monetary figure.
 */
function SummaryTab({ data }: { data: MonthView | null }) {
  const totals = data?.totals ?? {}
  const codes = Object.keys(totals).filter((c) => totals[c] > 0)
  if (codes.length === 0) {
    return <Empty description="Nothing recorded yet this month" />
  }

  const nameOf = (code: string) =>
    data?.categories.find((c) => c.code === code)?.name ?? code
  const unitOf = (code: string) =>
    data?.categories.find((c) => c.code === code)?.unit === 'DAYS' ? 'days' : 'hours'
  const orderOf = (code: string) =>
    data?.categories.find((c) => c.code === code)?.displayOrder ?? 999

  return (
    <>
      <Typography.Paragraph type="secondary">
        These are the quantities your approved timesheet sends to payroll. Amounts
        are calculated by payroll from your contract and are not shown here.
      </Typography.Paragraph>
      <Table
        rowKey="code"
        size="small"
        pagination={false}
        dataSource={codes
          .sort((a, b) => orderOf(a) - orderOf(b))
          .map((code) => ({ code, name: nameOf(code), quantity: totals[code], unit: unitOf(code) }))}
        columns={[
          { title: 'Quantity', dataIndex: 'name' },
          { title: 'Total', dataIndex: 'quantity', width: 120, align: 'right',
            render: (v: number) => round(v) },
          { title: 'Unit', dataIndex: 'unit', width: 100 },
        ]}
      />
    </>
  )
}

function StatusTag({ status }: { status?: MonthView['status'] }) {
  const colors: Record<string, string> = {
    DRAFT: 'default', SUBMITTED: 'processing', PENDING_HR: 'gold',
    RETURNED: 'error', APPROVED: 'success', LOCKED: 'purple', REOPENED: 'warning',
  }
  // "PENDING_HR" tells the employee nothing; where it is in the chain does.
  const labels: Record<string, string> = {
    SUBMITTED: 'With your manager',
    PENDING_HR: 'With HR',
    RETURNED: 'Returned to you',
    APPROVED: 'Approved',
  }
  if (!status) return null
  return <Tag color={colors[status] ?? 'default'}>{labels[status] ?? status}</Tag>
}

/**
 * Hours the day actually contains. Derived quantities re-classify hours that
 * are already counted (12 offshore hours on a holiday is twelve hours, not
 * twenty-four), so they are excluded from every total on this page.
 */
function baseHours(day: DayView): number {
  return day.quantities
    .filter((q) => !q.derived && q.unit === 'HOURS')
    .reduce((sum, q) => sum + q.quantity, 0)
}

const round = (n: number) => Math.round(n * 100) / 100

function errorOf(err: unknown, fallback: string): string {
  const res = (err as { response?: { data?: { message?: string } } })?.response
  return res?.data?.message ?? fallback
}

export default MyTimesheetPage
