import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  InputNumber,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  reportsApi,
  type AttendanceEmployeeRow,
  type AttendanceReport,
  type AttritionReport,
  type HeadcountReport,
  type LabeledCount,
  type LeaveAtRiskRow,
  type LeaveReport,
  type LeaveTypeUsage,
  type MandatoryCourseRow,
  type NonCompliantEmployee,
  type PayrollPeriodRow,
  type PayrollSummaryReport,
  type PerformanceReport,
  type RecruitmentReport,
  type TrainingReport,
  type VacancyFunnelRow,
} from '../api/reports'
import { performanceApi, type ReviewCycle } from '../api/performance'
import { reportSchedulingApi, type ReportType } from '../api/reportScheduling'
import {
  dashboardApi,
  type ExecutiveDashboard,
  type HeadcountTrend,
  type ManagerTeamDashboard,
  type PayrollMonthPoint,
  type PayrollTrend,
  type TeamMemberRow,
  type TurnoverDashboard,
  type UpcomingLeaveRow,
} from '../api/dashboard'
import { useAuth } from '../auth/AuthContext'

// ── Chart colour palette ───────────────────────────────────────────────────────
const C = {
  purple:  '#5B3FE5',
  green:   '#52c41a',
  red:     '#ff4d4f',
  blue:    '#1677ff',
  orange:  '#fa8c16',
  teal:    '#13c2c2',
  gold:    '#faad14',
  magenta: '#eb2f96',
}
const PIE_COLORS = [C.purple, C.blue, C.green, C.orange, C.red, C.teal, C.gold, C.magenta]

// ── Axis tick formatter ────────────────────────────────────────────────────────
const shortMonth = (m: string) => m.slice(5) // "2026-03" → "03"
// Recharts Tooltip formatter – value may be any numeric type or undefined
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const fmt2 = (v: any) => (typeof v === 'number' ? v.toFixed(2) : String(v ?? ''))

// ----------------------------------------------------------------------------
// ExportButtons
// ----------------------------------------------------------------------------
interface ExportParams { year?: number; from?: string; to?: string; cycleId?: string }
function ExportButtons({ type, params }: { type: ReportType; params?: ExportParams }) {
  const { message } = AntdApp.useApp()
  const [busy, setBusy] = useState<'PDF' | 'XLSX' | null>(null)
  const onClick = async (fmt: 'PDF' | 'XLSX') => {
    setBusy(fmt)
    try { await reportSchedulingApi.exportDownload(type, fmt, params) }
    catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Export failed',
      )
    } finally { setBusy(null) }
  }
  return (
    <Space>
      <Button icon={<FilePdfOutlined />} loading={busy === 'PDF'} onClick={() => onClick('PDF')}>PDF</Button>
      <Button icon={<FileExcelOutlined />} loading={busy === 'XLSX'} onClick={() => onClick('XLSX')}>Excel</Button>
    </Space>
  )
}

// ----------------------------------------------------------------------------
// MiniBars – legacy horizontal progress bars (kept for existing tabs)
// ----------------------------------------------------------------------------
function MiniBars({ rows }: { rows: LabeledCount[] }) {
  const max = useMemo(() => Math.max(1, ...rows.map(r => r.count)), [rows])
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No data" />
  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      {rows.map(r => (
        <Row key={r.label} gutter={8} align="middle">
          <Col span={6}><Typography.Text style={{ fontSize: 12 }}>{r.label}</Typography.Text></Col>
          <Col span={14}>
            <Progress percent={Math.round((r.count / max) * 100)} showInfo={false} strokeColor={C.purple} />
          </Col>
          <Col span={4} style={{ textAlign: 'right' }}><Typography.Text strong>{r.count}</Typography.Text></Col>
        </Row>
      ))}
    </Space>
  )
}

// ============================================================================
//  Headcount
// ============================================================================
function HeadcountPanel() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<HeadcountReport | null>(null)
  const [trend, setTrend] = useState<HeadcountTrend | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([reportsApi.headcount(), dashboardApi.headcountTrend(12)])
      .then(([d, t]) => { setData(d); setTrend(t) })
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />
  if (!data) return null

  // merge trend series into a single array for Recharts
  const trendRows = (trend?.headcount ?? []).map((p, i) => ({
    month: shortMonth(p.month),
    headcount: p.value,
    hires:   trend?.hires[i]?.value   ?? 0,
    leavers: trend?.leavers[i]?.value ?? 0,
  }))

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="end"><ExportButtons type="HEADCOUNT" /></Row>
      <Row gutter={16}>
        <Col span={4}><Card><Statistic title="Total"          value={data.totalEmployees} /></Card></Col>
        <Col span={4}><Card><Statistic title="Active"         value={data.activeEmployees}         valueStyle={{ color: C.green }} /></Card></Col>
        <Col span={4}><Card><Statistic title="On probation"   value={data.onProbation} /></Card></Col>
        <Col span={4}><Card><Statistic title="On leave / BT"  value={data.onLeave} /></Card></Col>
        <Col span={4}><Card><Statistic title="Terminated YTD" value={data.terminatedYearToDate}     valueStyle={{ color: C.red }} /></Card></Col>
        <Col span={4}><Card><Statistic title="Attrition"      value={data.attritionRatePercent}    suffix="%" precision={2} /></Card></Col>
      </Row>

      {trendRows.length > 0 && (
        <Card title="12-month headcount trend" size="small">
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={trendRows} margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="month" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="headcount" name="Active"  stroke={C.purple} strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="hires"     name="Hires"   stroke={C.green}  strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="leavers"   name="Leavers" stroke={C.red}    strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      )}

      <Row gutter={16}>
        <Col xs={24} md={12}><Card title="By status"     size="small"><MiniBars rows={data.byStatus} /></Card></Col>
        <Col xs={24} md={12}><Card title="By department" size="small"><MiniBars rows={data.byDepartment} /></Card></Col>
      </Row>
      <Row gutter={16}>
        <Col xs={24} md={12}><Card title="Hires (last 12 months)"   size="small"><MiniBars rows={data.hiresLast12Months} /></Card></Col>
        <Col xs={24} md={12}><Card title="Leavers (last 12 months)" size="small"><MiniBars rows={data.leaversLast12Months} /></Card></Col>
      </Row>
    </Space>
  )
}

// ============================================================================
//  Attrition
// ============================================================================
function AttritionPanel() {
  const { message } = AntdApp.useApp()
  const [year, setYear] = useState<number>(dayjs().year())
  const [data, setData] = useState<AttritionReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    reportsApi.attrition(year)
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [year, message])

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Year</Typography.Text>
          <InputNumber min={2020} max={2099} value={year} onChange={v => v && setYear(Number(v))} />
        </Space>
        <ExportButtons type="ATTRITION" params={{ year }} />
      </Row>
      {loading ? <Spin /> : data && (
        <>
          <Row gutter={16}>
            <Col span={6}><Card><Statistic title="Terminations"  value={data.terminationsTotal} valueStyle={{ color: C.red }} /></Card></Col>
            <Col span={6}><Card><Statistic title="Attrition rate" value={data.attritionRatePercent} suffix="%" precision={2} /></Card></Col>
            <Col span={12}><Card><Statistic title={`Total settlement payout (${data.currency})`} value={data.totalSettlementPayout} precision={2} /></Card></Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} md={12}><Card title="By reason"  size="small"><MiniBars rows={data.byReason} /></Card></Col>
            <Col xs={24} md={12}><Card title="By month"   size="small"><MiniBars rows={data.byMonth} /></Card></Col>
          </Row>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  Payroll
// ============================================================================
function PayrollPanel() {
  const { message } = AntdApp.useApp()
  const [year, setYear] = useState<number>(dayjs().year())
  const [data,  setData]  = useState<PayrollSummaryReport | null>(null)
  const [trend, setTrend] = useState<PayrollTrend | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([reportsApi.payrollSummary(year), dashboardApi.payrollTrend(year)])
      .then(([d, t]) => { setData(d); setTrend(t) })
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [year, message])

  const columns: ColumnsType<PayrollPeriodRow> = [
    { title: 'Run #',      dataIndex: 'runNo',    width: 110 },
    { title: 'Period',     render: (_, r) => `${r.year}-${String(r.month).padStart(2, '0')}`, width: 100 },
    { title: 'Status',     dataIndex: 'status',   width: 110, render: (s: string) => <Tag>{s}</Tag> },
    { title: 'Employees',  dataIndex: 'employeeCount', width: 100, align: 'right' },
    { title: 'Gross',      render: (_, r) => `${r.totalGross} ${r.currency}`, align: 'right' },
    { title: 'Tax',        dataIndex: 'totalTax',        align: 'right' },
    { title: 'Deductions', dataIndex: 'totalDeductions', align: 'right' },
    { title: 'Net',        render: (_, r) => <Typography.Text strong>{`${r.totalNet} ${r.currency}`}</Typography.Text>, align: 'right' },
  ]

  const trendRows = (trend?.monthly ?? []).map((p: PayrollMonthPoint) => ({
    month: shortMonth(p.month),
    Gross: p.gross,
    Net:   p.net,
    Tax:   p.tax,
  }))

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Year</Typography.Text>
          <InputNumber min={2020} max={2099} value={year} onChange={v => v && setYear(Number(v))} />
        </Space>
        <ExportButtons type="PAYROLL" params={{ year }} />
      </Row>
      {loading ? <Spin /> : data && (
        <>
          <Row gutter={16}>
            <Col span={6}><Card><Statistic title="Gross YTD"            value={data.totalGrossYtd}      precision={2} /></Card></Col>
            <Col span={6}><Card><Statistic title="Net YTD"              value={data.totalNetYtd}        precision={2} valueStyle={{ color: C.green }} /></Card></Col>
            <Col span={6}><Card><Statistic title="Tax YTD"              value={data.totalTaxYtd}        precision={2} /></Card></Col>
            <Col span={6}><Card><Statistic title="Other deductions YTD" value={data.totalDeductionsYtd} precision={2} /></Card></Col>
          </Row>

          {trendRows.length > 0 && (
            <Card title={`Monthly payroll cost — ${year}`} size="small">
              <ResponsiveContainer width="100%" height={280}>
                <AreaChart data={trendRows} margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="gGross" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor={C.purple} stopOpacity={0.3} />
                      <stop offset="95%" stopColor={C.purple} stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="gNet" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor={C.green} stopOpacity={0.3} />
                      <stop offset="95%" stopColor={C.green} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="month" />
                  <YAxis tickFormatter={v => (v / 1000).toFixed(0) + 'k'} />
                  <Tooltip formatter={fmt2} />
                  <Legend />
                  <Area type="monotone" dataKey="Gross" stroke={C.purple} fill="url(#gGross)" strokeWidth={2} />
                  <Area type="monotone" dataKey="Net"   stroke={C.green}  fill="url(#gNet)"   strokeWidth={2} />
                  <Area type="monotone" dataKey="Tax"   stroke={C.red}    fill="none"          strokeWidth={1.5} strokeDasharray="4 2" />
                </AreaChart>
              </ResponsiveContainer>
            </Card>
          )}

          <Card title={`Runs in ${year}`} size="small">
            <Table size="small" rowKey="runId" columns={columns} dataSource={data.runs} pagination={false} />
          </Card>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  Leave usage
// ============================================================================
function LeavePanel() {
  const { message } = AntdApp.useApp()
  const [year, setYear] = useState<number>(dayjs().year())
  const [data, setData] = useState<LeaveReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    reportsApi.leave(year)
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [year, message])

  const usageCols: ColumnsType<LeaveTypeUsage> = [
    { title: 'Code',        dataIndex: 'leaveTypeCode', width: 120 },
    { title: 'Name',        dataIndex: 'leaveTypeName' },
    { title: 'Employees',   dataIndex: 'employeeCount',  width: 100, align: 'right' },
    { title: 'Entitlement', dataIndex: 'entitlementSum', align: 'right' },
    { title: 'Used',        dataIndex: 'usedSum',        align: 'right' },
    { title: 'Reserved',    dataIndex: 'reservedSum',    align: 'right' },
    { title: 'Remaining',   dataIndex: 'remainingSum',   align: 'right' },
  ]
  const riskCols: ColumnsType<LeaveAtRiskRow> = [
    { title: 'Employee',    render: (_, r) => `${r.employeeNo} — ${r.fullName}` },
    { title: 'Leave',       dataIndex: 'leaveTypeCode', width: 110 },
    { title: 'Remaining',   dataIndex: 'remaining',     width: 120, align: 'right' },
    { title: 'Entitlement', dataIndex: 'entitlement',   width: 120, align: 'right' },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Year</Typography.Text>
          <InputNumber min={2020} max={2099} value={year} onChange={v => v && setYear(Number(v))} />
        </Space>
        <ExportButtons type="LEAVE" params={{ year }} />
      </Row>
      {loading ? <Spin /> : data && (
        <>
          <Card title="Usage by leave type" size="small">
            <Table size="small" rowKey="leaveTypeCode" columns={usageCols} dataSource={data.byType} pagination={false} />
          </Card>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Card title="Overdrawn balances (negative remaining)" size="small">
                {data.overdrawn.length === 0
                  ? <Typography.Text type="secondary">None — all balances are healthy.</Typography.Text>
                  : <Table size="small" rowKey="employeeId" columns={riskCols} dataSource={data.overdrawn} pagination={false} />}
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card title="High annual-leave balance (≥80% unused)" size="small">
                {data.highBalanceAtRisk.length === 0
                  ? <Typography.Text type="secondary">No employees in this band.</Typography.Text>
                  : <Table size="small" rowKey="employeeId" columns={riskCols} dataSource={data.highBalanceAtRisk} pagination={false} />}
              </Card>
            </Col>
          </Row>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  Attendance
// ============================================================================
function AttendancePanel() {
  const { message } = AntdApp.useApp()
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().startOf('month'), dayjs().endOf('month'),
  ])
  const [data, setData] = useState<AttendanceReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    reportsApi.attendance(range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD'))
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [range, message])

  const cols: ColumnsType<AttendanceEmployeeRow> = [
    { title: 'Employee',    render: (_, r) => `${r.employeeNo} — ${r.fullName}` },
    { title: 'Worked (h)',  dataIndex: 'workedHours',     width: 100, align: 'right' },
    { title: 'Work days',   dataIndex: 'workDays',        width: 90,  align: 'right' },
    { title: 'Late (min)',  dataIndex: 'lateMinutes',     width: 100, align: 'right' },
    { title: 'Early (min)', dataIndex: 'earlyMinutes',    width: 100, align: 'right' },
    { title: 'OT (min)',    dataIndex: 'overtimeMinutes', width: 100, align: 'right' },
    { title: 'Absent days', dataIndex: 'absentDays',      width: 100, align: 'right' },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Period</Typography.Text>
          <DatePicker.RangePicker
            value={range}
            onChange={v => { if (v && v[0] && v[1]) setRange([v[0], v[1]]) }}
            allowClear={false}
          />
        </Space>
        <ExportButtons type="ATTENDANCE" params={{ from: range[0].format('YYYY-MM-DD'), to: range[1].format('YYYY-MM-DD') }} />
      </Row>
      {loading ? <Spin /> : data && (
        <>
          <Row gutter={16}>
            <Col span={5}><Card><Statistic title="Worked hours"      value={data.totalWorkedHours}    precision={2} /></Card></Col>
            <Col span={5}><Card><Statistic title="Late (min)"        value={data.totalLateMinutes}    valueStyle={{ color: C.red }} /></Card></Col>
            <Col span={5}><Card><Statistic title="Early-leaves (min)" value={data.totalEarlyMinutes} /></Card></Col>
            <Col span={5}><Card><Statistic title="Overtime (min)"    value={data.totalOvertimeMinutes} valueStyle={{ color: C.green }} /></Card></Col>
            <Col span={4}><Card><Statistic title="Absent days"       value={data.totalAbsentDays}    valueStyle={{ color: C.red }} /></Card></Col>
          </Row>
          <Card title="By employee" size="small">
            <Table size="small" rowKey="employeeId" columns={cols} dataSource={data.byEmployee} pagination={false} />
          </Card>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  Training compliance
// ============================================================================
function TrainingPanel() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<TrainingReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    reportsApi.training()
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  const courseCols: ColumnsType<MandatoryCourseRow> = [
    { title: 'Course #',   dataIndex: 'courseNo',    width: 110 },
    { title: 'Code',       dataIndex: 'code',        width: 160 },
    { title: 'Title',      dataIndex: 'title' },
    { title: 'Enrolled',   dataIndex: 'enrolled',    width: 90,  align: 'right' },
    { title: 'Passed',     dataIndex: 'passed',      width: 90,  align: 'right' },
    { title: 'Failed',     dataIndex: 'failed',      width: 90,  align: 'right' },
    { title: 'In progress', dataIndex: 'inProgress',  width: 110, align: 'right' },
    { title: 'Completion', dataIndex: 'completionRatePercent', width: 160,
      render: (v: number) => <Progress percent={Number(v)} size="small" /> },
  ]
  const nonCols: ColumnsType<NonCompliantEmployee> = [
    { title: 'Employee', render: (_, r) => `${r.employeeNo} — ${r.fullName}` },
    { title: 'Course',   dataIndex: 'courseTitle' },
    { title: 'Status',   dataIndex: 'reason', width: 150,
      render: (s: string) => <Tag color="orange">{s.replace(/_/g, ' ')}</Tag> },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="end"><ExportButtons type="TRAINING" /></Row>
      {loading ? <Spin /> : data && (
        <>
          <Row gutter={16}>
            <Col span={6}><Card><Statistic title="Mandatory courses"  value={data.mandatoryCourses} /></Card></Col>
            <Col span={6}><Card><Statistic title="Active employees"   value={data.employeesActive} /></Card></Col>
            <Col span={12}>
              <Card>
                <Statistic title="Overall compliance" value={data.overallCompliancePercent} suffix="%" precision={2}
                  valueStyle={{ color: data.overallCompliancePercent >= 80 ? C.green : C.red }} />
                <Progress percent={Number(data.overallCompliancePercent)}
                  status={data.overallCompliancePercent >= 80 ? 'success' : 'exception'} showInfo={false} />
              </Card>
            </Col>
          </Row>
          <Card title="Per mandatory course" size="small">
            <Table size="small" rowKey="courseId" columns={courseCols} dataSource={data.perCourse} pagination={false} />
          </Card>
          <Card title="Non-compliant employees" size="small">
            {data.nonCompliant.length === 0
              ? <Typography.Text type="secondary">Everyone is compliant.</Typography.Text>
              : <Table size="small" rowKey={(r) => `${r.employeeId}-${r.courseId}`} columns={nonCols} dataSource={data.nonCompliant} pagination={{ pageSize: 20 }} />}
          </Card>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  Performance distribution
// ============================================================================
function PerformancePanel() {
  const { message } = AntdApp.useApp()
  const [cycles,  setCycles]  = useState<ReviewCycle[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [data,    setData]    = useState<PerformanceReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => { performanceApi.cycles().then(setCycles) }, [])
  useEffect(() => {
    setLoading(true)
    reportsApi.performance(cycleId)
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [cycleId, message])

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Cycle</Typography.Text>
          <Select allowClear placeholder="All cycles" style={{ width: 320 }}
            options={cycles.map(c => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
            value={cycleId} onChange={setCycleId} />
        </Space>
        <ExportButtons type="PERFORMANCE" params={cycleId ? { cycleId } : undefined} />
      </Row>
      {loading ? <Spin /> : data && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {data.cycles.length === 0 && <Empty description="No review cycles found" />}
          {data.cycles.map(c => (
            <Card key={c.cycleId} title={`${c.cycleCode} — ${c.cycleName}`} size="small">
              <Row gutter={16}>
                <Col span={6}><Statistic title="Reviews total"       value={c.reviewsTotal} /></Col>
                <Col span={6}><Statistic title="Completed"           value={c.reviewsCompleted} valueStyle={{ color: C.green }} /></Col>
                <Col span={6}><Statistic title="Average final rating" value={c.averageFinalRating ?? 0} precision={2} suffix=" / 5" /></Col>
              </Row>
              <Row gutter={16} style={{ marginTop: 16 }}>
                <Col xs={24} md={12}>
                  <Typography.Text strong>Rating distribution</Typography.Text>
                  <MiniBars rows={c.distribution.map(b => ({ label: String(b.bucket), count: b.count }))} />
                </Col>
                <Col xs={24} md={12}>
                  <Typography.Text strong>Recommendation breakdown</Typography.Text>
                  <MiniBars rows={c.recommendationBreakdown} />
                </Col>
              </Row>
            </Card>
          ))}
        </Space>
      )}
    </Space>
  )
}

// ============================================================================
//  Recruitment funnel
// ============================================================================
function RecruitmentPanel() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<RecruitmentReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    reportsApi.recruitment()
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  const cols: ColumnsType<VacancyFunnelRow> = [
    { title: 'Vacancy #',          dataIndex: 'vacancyNo',           width: 110 },
    { title: 'Title',              dataIndex: 'title' },
    { title: 'Status',             dataIndex: 'status',              width: 110, render: (s: string) => <Tag>{s}</Tag> },
    { title: 'Openings',           dataIndex: 'openings',            width: 100, align: 'right' },
    { title: 'Applications',       dataIndex: 'applicationsTotal',   width: 120, align: 'right' },
    { title: 'Hires',              dataIndex: 'hires',               width: 80,  align: 'right' },
    { title: 'Avg time-to-hire (d)', dataIndex: 'averageTimeToHireDays', width: 160, align: 'right',
      render: (v: number | null) => v == null ? '—' : v },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="end"><ExportButtons type="RECRUITMENT" /></Row>
      {loading ? <Spin /> : data && (
        <>
          <Row gutter={16}>
            <Col span={5}><Card><Statistic title="Vacancies open"           value={data.vacanciesOpen} /></Card></Col>
            <Col span={5}><Card><Statistic title="Vacancies filled"         value={data.vacanciesFilled} valueStyle={{ color: C.green }} /></Card></Col>
            <Col span={5}><Card><Statistic title="Candidate pool"           value={data.candidatesPool} /></Card></Col>
            <Col span={5}><Card><Statistic title="Applications in progress" value={data.applicationsInProgress} /></Card></Col>
            <Col span={4}><Card><Statistic title="Avg time-to-hire (d)"     value={data.averageTimeToHireDays} precision={2} /></Card></Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} md={10}>
              <Card title="Active applications by stage" size="small">
                <MiniBars rows={data.applicationsByStage} />
              </Card>
            </Col>
            <Col xs={24} md={14}>
              <Card title="Per vacancy" size="small">
                <Table size="small" rowKey="vacancyId" columns={cols} dataSource={data.byVacancy} pagination={false} />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </Space>
  )
}

// ============================================================================
//  NEW: Turnover Dashboard
// ============================================================================
function TurnoverPanel() {
  const { message } = AntdApp.useApp()
  const [year, setYear] = useState<number>(dayjs().year())
  const [data, setData] = useState<TurnoverDashboard | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    dashboardApi.turnover(year)
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [year, message])

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Space>
          <Typography.Text>Year</Typography.Text>
          <InputNumber min={2020} max={2099} value={year} onChange={v => v && setYear(Number(v))} />
        </Space>
      </Row>
      {loading ? <Spin /> : data ? (
        <>
          <Row gutter={16}>
            <Col span={6}><Card><Statistic title="Terminations YTD"  value={data.terminationsYtd}      valueStyle={{ color: C.red }} /></Card></Col>
            <Col span={6}><Card><Statistic title="Attrition rate"    value={data.attritionRatePercent} suffix="%" precision={2} /></Card></Col>
            <Col span={6}><Card><Statistic title="Avg tenure (months)" value={data.avgTenureMonths}    precision={1} /></Card></Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={16}>
              <Card title="Monthly attrition rate (%)" size="small">
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart
                    data={data.monthlyAttritionRate.map(p => ({ month: shortMonth(p.month), rate: p.value }))}
                    margin={{ top: 8, right: 24, left: 0, bottom: 0 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="month" />
                    <YAxis unit="%" />
                    <Tooltip formatter={fmt2} />
                    <Line type="monotone" dataKey="rate" name="Attrition %" stroke={C.red} strokeWidth={2} dot={{ r: 3 }} />
                  </LineChart>
                </ResponsiveContainer>
              </Card>
            </Col>
            <Col xs={24} md={8}>
              <Card title="Terminations by reason" size="small">
                {data.byReason.length === 0
                  ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No terminations" />
                  : (
                    <ResponsiveContainer width="100%" height={260}>
                      <PieChart>
                        <Pie data={data.byReason} dataKey="value" nameKey="label"
                          cx="50%" cy="50%" outerRadius={90} label={({ name, percent }: { name?: string; percent?: number }) =>
                            `${name ?? ''} ${((percent ?? 0) * 100).toFixed(0)}%`
                          }>
                          {data.byReason.map((_, i) => (
                            <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip />
                      </PieChart>
                    </ResponsiveContainer>
                  )}
              </Card>
            </Col>
          </Row>
        </>
      ) : null}
    </Space>
  )
}

// ============================================================================
//  NEW: Manager Team Dashboard
// ============================================================================
function ManagerTeamPanel() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<ManagerTeamDashboard | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    dashboardApi.managerTeam()
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  const memberCols: ColumnsType<TeamMemberRow> = [
    { title: 'Employee #',  dataIndex: 'employeeNo',       width: 120 },
    { title: 'First name',  dataIndex: 'firstName' },
    { title: 'Last name',   dataIndex: 'lastName' },
    { title: 'Position',    dataIndex: 'positionTitle' },
    { title: 'Status',      dataIndex: 'employmentStatus', width: 130,
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s.replace(/_/g, ' ')}</Tag> },
  ]
  const leaveCols: ColumnsType<UpcomingLeaveRow> = [
    { title: 'Employee',   dataIndex: 'employeeName' },
    { title: 'Leave type', dataIndex: 'leaveTypeCode', width: 140 },
    { title: 'From',       dataIndex: 'startDate',     width: 110 },
    { title: 'To',         dataIndex: 'endDate',       width: 110 },
    { title: 'Days',       dataIndex: 'totalDays',     width: 80, align: 'right' },
  ]

  if (loading) return <Spin />
  if (!data) return null

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col span={6}><Card><Statistic title="Team size"         value={data.teamSize} /></Card></Col>
        <Col span={6}><Card><Statistic title="Pending approvals" value={data.pendingApprovals} valueStyle={data.pendingApprovals > 0 ? { color: C.orange } : undefined} /></Card></Col>
        <Col xs={24} md={12}>
          <Card title="Status breakdown" size="small" style={{ height: '100%' }}>
            {data.statusBreakdown.length === 0
              ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No data" />
              : (
                <ResponsiveContainer width="100%" height={120}>
                  <BarChart layout="vertical"
                    data={data.statusBreakdown.map(s => ({ name: s.label.replace(/_/g, ' '), count: s.value }))}
                    margin={{ top: 0, right: 24, left: 60, bottom: 0 }}
                  >
                    <XAxis type="number" hide />
                    <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={90} />
                    <Tooltip />
                    <Bar dataKey="count" fill={C.purple} radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
          </Card>
        </Col>
      </Row>

      <Card title="Team members" size="small">
        {data.members.length === 0
          ? <Typography.Text type="secondary">No direct reports.</Typography.Text>
          : <Table size="small" rowKey="employeeNo" columns={memberCols} dataSource={data.members} pagination={{ pageSize: 20 }} />}
      </Card>

      <Card title="Upcoming leave (next 30 days)" size="small">
        {data.upcomingLeave.length === 0
          ? <Typography.Text type="secondary">No upcoming leave in the next 30 days.</Typography.Text>
          : <Table size="small" rowKey={(r) => `${r.employeeName}-${r.startDate}`} columns={leaveCols} dataSource={data.upcomingLeave} pagination={false} />}
      </Card>
    </Space>
  )
}

// ============================================================================
//  NEW: Executive Dashboard
// ============================================================================
function ExecutivePanel() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<ExecutiveDashboard | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    dashboardApi.executive()
      .then(setData)
      .catch(err => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />
  if (!data) return null

  const hcRows = data.headcountTrend.map(p => ({ month: shortMonth(p.month), headcount: p.value }))
  const payRows = data.payrollTrend.map((p: PayrollMonthPoint) => ({
    month: shortMonth(p.month),
    Gross: p.gross,
    Net:   p.net,
  }))

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col xs={12} md={4}><Card><Statistic title="Total headcount"       value={data.totalHeadcount} /></Card></Col>
        <Col xs={12} md={4}><Card><Statistic title="Payroll cost (month)"  value={data.currentMonthPayrollCost}   precision={0} /></Card></Col>
        <Col xs={12} md={4}><Card><Statistic title="Attrition YTD"         value={data.attritionRateYtd}          suffix="%" precision={2} /></Card></Col>
        <Col xs={12} md={4}><Card><Statistic title="Training compliance"   value={data.trainingCompliancePercent} suffix="%" precision={1}
          valueStyle={{ color: data.trainingCompliancePercent >= 80 ? C.green : C.red }} /></Card></Col>
        <Col xs={12} md={4}><Card><Statistic title="Open vacancies"        value={data.openVacancies} /></Card></Col>
        <Col xs={12} md={4}><Card><Statistic title="Pending approvals"     value={data.pendingApprovals}
          valueStyle={data.pendingApprovals > 0 ? { color: C.orange } : undefined} /></Card></Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Card title="Headcount trend (6 months)" size="small">
            <ResponsiveContainer width="100%" height={240}>
              <AreaChart data={hcRows} margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="gHc" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%"  stopColor={C.purple} stopOpacity={0.4} />
                    <stop offset="95%" stopColor={C.purple} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip />
                <Area type="monotone" dataKey="headcount" name="Headcount" stroke={C.purple} fill="url(#gHc)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Payroll cost trend (6 months)" size="small">
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={payRows} margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis tickFormatter={v => (v / 1000).toFixed(0) + 'k'} />
                <Tooltip formatter={fmt2} />
                <Legend />
                <Bar dataKey="Gross" fill={C.purple} radius={[4, 4, 0, 0]} />
                <Bar dataKey="Net"   fill={C.green}  radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>
    </Space>
  )
}

// ============================================================================
//  ReportsPage shell
// ============================================================================
export function ReportsPage() {
  const { hasRole } = useAuth()

  const canSeeManagerTab  = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'DEPARTMENT_MANAGER')
  const canSeeExecutiveTab = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'FINANCE_USER', 'AUDITOR')

  const items = [
    { key: 'headcount',   label: 'Headcount',   children: <HeadcountPanel /> },
    { key: 'attrition',   label: 'Attrition',   children: <AttritionPanel /> },
    { key: 'payroll',     label: 'Payroll',      children: <PayrollPanel /> },
    { key: 'leave',       label: 'Leave',        children: <LeavePanel /> },
    { key: 'attendance',  label: 'Attendance',   children: <AttendancePanel /> },
    { key: 'training',    label: 'Training',     children: <TrainingPanel /> },
    { key: 'performance', label: 'Performance',  children: <PerformancePanel /> },
    { key: 'recruitment', label: 'Recruitment',  children: <RecruitmentPanel /> },
    { key: 'turnover',    label: 'Turnover',     children: <TurnoverPanel /> },
    ...(canSeeManagerTab   ? [{ key: 'manager',   label: 'My Team',   children: <ManagerTeamPanel /> }] : []),
    ...(canSeeExecutiveTab ? [{ key: 'executive', label: 'Executive', children: <ExecutivePanel /> }]   : []),
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Reports & Analytics</Typography.Title>}>
      <Tabs items={items} />
    </Card>
  )
}
