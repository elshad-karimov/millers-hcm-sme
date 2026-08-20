import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  selfApi,
  type SelfProfile,
  type SelfSummary,
} from '../api/self'
import type { LeaveBalance, LeaveRequest, LeaveType } from '../api/leave'
import type { PermissionBalance, PermissionRequest } from '../api/permission'
import type { BusinessTrip } from '../api/businessTrip'
import type { Timesheet } from '../api/timesheet'
import { payrollApi, type PayslipInfo, type SalaryAdvance } from '../api/payroll'
import { compensationApi } from '../api/compensation'
import {
  benefitsApi,
  claimsApi,
  ENROLLMENT_STATUS_COLOR,
  CLAIM_STATUS_COLOR,
  type EnrollmentResponse,
  type ClaimResponse,
} from '../api/benefits'
import type { Enrollment, Certificate, EmployeeCompetency } from '../api/learning'
import {
  pathAssignmentsApi,
  type AssignmentResponse,
  type PathAssignmentStatus,
} from '../api/learningPaths'
import type { PerformanceReview } from '../api/performance'
import { RequiredDocumentsWidget } from '../components/RequiredDocumentsWidget'
import { ApprovalLimitsWidget } from '../components/ApprovalLimitsWidget'
import { ChecklistTasksWidget } from '../components/ChecklistTasksWidget'
import { InternalJobsWidget } from '../components/InternalJobsWidget'
import { HrRequestsWidget } from '../components/HrRequestsWidget'
import { AnnouncementsCard } from '../components/AnnouncementsCard'
import { api } from '../api/client'
import { selfPpeApi, type PpeAssignmentResponse } from '../api/ehs'

// M446 — Warning self-service types
interface WarningRecord {
  id: string
  level: string
  reason: string
  issuedAt: string
  expiresAt?: string
  acknowledgedAt?: string
}

// M440 — Signature self-service types
interface SignerDTO {
  id: string
  requestId: string
  username: string
  employeeId?: string
  status: string
  signedAt?: string
  declineReason?: string
}

interface SignatureRequestDTO {
  id: string
  title: string
  employeeDocumentId?: string
  letterRequestId?: string
  attachmentId?: string
  status: string
  provider: string
  createdAt: string
  createdBy: string
  signers: SignerDTO[]
}

// ============================================================================
//  Dashboard tab
// ============================================================================
function Dashboard({
  profile,
  summary,
  onJump,
}: {
  profile: SelfProfile
  summary: SelfSummary
  onJump: (key: string) => void
}) {
  const navigate = useNavigate()
  // M230 — Dashboard tab strings come from the selfService namespace;
  // tab content (data tables) stays in English for follow-up.
  const { t } = useTranslation('selfService')

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* M430 — Announcements card (top, dismissible). */}
      <AnnouncementsCard />
      {/* M266 — "✅ Onboarding checklist" widget. Renders nothing once
          all checklist tasks are done; appears first so the new hire
          sees their to-dos at the top. */}
      <ChecklistTasksWidget />
      {/* M263 — "📂 Documents owed to HR" widget. Renders nothing when
          the employee has no pending REQUIRED_DOCUMENT obligations,
          so the dashboard stays clean for the typical case. */}
      <RequiredDocumentsWidget />
      {/* M264 — "💳 Your approval authority" widget. Renders nothing
          unless the employee actually holds approval limits (typical
          for managers + senior staff). */}
      <ApprovalLimitsWidget />
      {/* M281 — "💼 Internal opportunities" widget (Recruitment PRD §10).
          Renders nothing while no INTERNAL-channel postings are live. */}
      <InternalJobsWidget />
      {/* M429 — "HR Requests" widget on the dashboard. */}
      <HrRequestsWidget />
      {/* M446 — "My Warnings" widget. Renders nothing when no warnings. */}
      <MyWarningsWidget />
      {/* M440 — "Pending Signatures" widget. Renders nothing when no pending signatures. */}
      <PendingSignaturesWidget />
      <Card>
        <Row gutter={16} align="middle">
          <Col flex="auto">
            <Typography.Title level={3} style={{ margin: 0 }}>
              {profile.firstName} {profile.lastName}
            </Typography.Title>
            <Typography.Text type="secondary">
              {profile.employeeNo} · {profile.positionTitle ?? '—'} ·{' '}
              {profile.departmentName ?? '—'}
            </Typography.Text>{' '}
            <Tag
              color={
                summary.employmentStatus === 'ACTIVE'
                  ? 'green'
                  : summary.employmentStatus === 'ON_PROBATION'
                  ? 'orange'
                  : 'default'
              }
              style={{ marginLeft: 8 }}
            >
              {summary.employmentStatus ?? '—'}
            </Tag>
          </Col>
          <Col>
            <Space wrap>
              <Button onClick={() => navigate('/leave/requests/new')}>{t('quickActions.requestLeave')}</Button>
              <Button onClick={() => navigate('/permission/requests/new')}>{t('quickActions.requestPermission')}</Button>
              <Button onClick={() => navigate('/business-trips/new')}>{t('quickActions.newBusinessTrip')}</Button>
              <Button onClick={() => navigate('/letters/request')}>{t('quickActions.requestLetter')}</Button>
              <Button onClick={() => navigate('/personal-info/request')}>{t('quickActions.updatePersonalInfo')}</Button>
              <Button onClick={() => navigate('/ehs/incidents')}>Report safety incident</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* M481 — Reward Points widget */}
      <MyRewardPointsWidget employeeId={profile.id} />

      <Row gutter={16}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('leave')}>
            <Statistic
              title={t('kpi.annualLeaveRemaining')}
              value={summary.annualLeaveRemaining ?? 0}
              suffix={t('kpi.annualLeaveUnit')}
              precision={1}
              valueStyle={{ color: (summary.annualLeaveRemaining ?? 0) > 5 ? '#3f8600' : '#cf1322' }}
            />
            {summary.leaveRequestsPending > 0 && (
              <Tag color="gold" style={{ marginTop: 6 }}>
                {t('kpi.pendingCount', { count: summary.leaveRequestsPending })}
              </Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('permission')}>
            <Statistic
              title={t('kpi.permissionHoursLeft')}
              value={summary.permissionHoursRemaining ?? 0}
              suffix={t('kpi.permissionHoursUnit')}
              precision={1}
            />
            {summary.permissionRequestsPending > 0 && (
              <Tag color="gold" style={{ marginTop: 6 }}>
                {t('kpi.pendingCount', { count: summary.permissionRequestsPending })}
              </Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('timesheets')}>
            <Statistic
              title={t('kpi.timesheet', {
                period: `${summary.year}-${String(new Date().getMonth() + 1).padStart(2, '0')}`,
              })}
              value={summary.currentTimesheetStatus ?? t('kpi.timesheetNotStarted')}
              valueStyle={{ fontSize: 18 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('payslips')}>
            <Statistic
              title={t('kpi.latestPayslipNet')}
              value={summary.lastPayslipNet ?? 0}
              precision={2}
              suffix={t('kpi.currency')}
              valueStyle={{ color: '#3f8600' }}
            />
            {summary.lastPayslipAt && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {summary.lastPayslipAt.slice(0, 10)}
              </Typography.Text>
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('learning')}>
            <Statistic title={t('kpi.certificates')} value={summary.certificatesHeld} />
            {summary.mandatoryCoursesPending > 0 ? (
              <Tag color="red" style={{ marginTop: 6 }}>
                {t('kpi.mandatoryPending', { count: summary.mandatoryCoursesPending })}
              </Tag>
            ) : (
              <Tag color="green" style={{ marginTop: 6 }}>{t('kpi.allMandatoryDone')}</Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('performance')}>
            <Statistic
              title={t('kpi.latestReview')}
              value={summary.activeReviewStatus ?? '—'}
              valueStyle={{ fontSize: 18 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={12}>
          <Card title={t('profile.heading')} size="small">
            <Descriptions size="small" column={2}>
              <Descriptions.Item label={t('profile.employeeNo')}>{profile.employeeNo}</Descriptions.Item>
              <Descriptions.Item label={t('profile.hireDate')}>{profile.hireDate}</Descriptions.Item>
              <Descriptions.Item label={t('profile.email')}>{profile.email ?? '—'}</Descriptions.Item>
              <Descriptions.Item label={t('profile.phone')}>{profile.phone ?? '—'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>

      {/* My PPE card */}
      <MyPpeWidget />
    </Space>
  )
}

// ============================================================================
//  My PPE Widget
// ============================================================================
function MyPpeWidget() {
  const { message } = AntdApp.useApp()
  const [assignments, setAssignments] = useState<PpeAssignmentResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    selfPpeApi
      .myAssignments()
      .then(setAssignments)
      .catch(() => {
        // Non-critical — hide widget if user has no PPE
      })
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return null
  if (assignments.length === 0) return null

  const activeAssignments = assignments.filter((a) => !a.returnedAt)
  if (activeAssignments.length === 0) return null

  return (
    <Card title="My PPE" size="small">
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={activeAssignments}
        columns={[
          {
            title: 'Item',
            dataIndex: 'ppeItemName',
            key: 'ppeItemName',
            render: (text) => text || '—',
          },
          {
            title: 'Issued',
            dataIndex: 'issuedAt',
            key: 'issuedAt',
            width: 110,
          },
          {
            title: 'Expiry',
            dataIndex: 'expiryDate',
            key: 'expiryDate',
            width: 110,
            render: (text) => {
              if (!text) return '—'
              const isExpired = dayjs(text).isBefore(dayjs(), 'day')
              return isExpired ? <Tag color="red">{text}</Tag> : text
            },
          },
          {
            title: 'Condition',
            dataIndex: 'conditionAtIssue',
            key: 'conditionAtIssue',
            width: 140,
            render: (text) => text || '—',
          },
        ]}
      />
    </Card>
  )
}

// ============================================================================
//  Leave tab
// ============================================================================
function LeaveTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [requests, setRequests] = useState<LeaveRequest[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([selfApi.leaveBalances(), selfApi.leaveTypes(), selfApi.leaveRequests()])
      .then(([b, t, r]) => {
        setBalances(b)
        setTypes(t)
        setRequests(r)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const typeMap = new Map(types.map((t) => [t.id, t]))

  const balanceCols: ColumnsType<LeaveBalance> = [
    {
      title: 'Type',
      dataIndex: 'leaveTypeId',
      render: (id: string) => {
        const t = typeMap.get(id)
        return t ? `${t.code} — ${t.name}` : id
      },
    },
    { title: 'Entitlement', dataIndex: 'entitlementDays', width: 110, align: 'right' },
    { title: 'Used', dataIndex: 'usedDays', width: 100, align: 'right' },
    { title: 'Reserved', dataIndex: 'reservedDays', width: 100, align: 'right' },
    { title: 'Remaining', dataIndex: 'remaining', width: 110, align: 'right' },
  ]

  const reqCols: ColumnsType<LeaveRequest> = [
    { title: 'Request #', dataIndex: 'requestNo', width: 120 },
    {
      title: 'Type',
      dataIndex: 'leaveTypeId',
      render: (id: string) => typeMap.get(id)?.code ?? id,
      width: 140,
    },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    { title: 'End', dataIndex: 'endDate', width: 110 },
    { title: 'Days', dataIndex: 'days', width: 90, align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag>{s}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="My balances"
        size="small"
        extra={
          <Button type="primary" onClick={() => navigate('/leave/requests/new')}>
            Request leave
          </Button>
        }
      >
        <Table rowKey="id" size="small" columns={balanceCols} dataSource={balances} pagination={false} />
      </Card>
      <Card title="My requests" size="small">
        <Table rowKey="id" size="small" columns={reqCols} dataSource={requests} pagination={false} />
      </Card>
    </Space>
  )
}

// ============================================================================
//  Permission tab
// ============================================================================
function PermissionTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [balances, setBalances] = useState<PermissionBalance[]>([])
  const [requests, setRequests] = useState<PermissionRequest[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([selfApi.permissionBalances(), selfApi.permissionRequests()])
      .then(([b, r]) => {
        setBalances(b)
        setRequests(r)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const balanceCols: ColumnsType<PermissionBalance> = [
    { title: 'Permission type', dataIndex: 'permissionTypeId' },
    { title: 'Entitlement (h)', dataIndex: 'entitlementHours', width: 130, align: 'right' },
    { title: 'Used (h)', dataIndex: 'usedHours', width: 110, align: 'right' },
    { title: 'Reserved (h)', dataIndex: 'reservedHours', width: 130, align: 'right' },
    { title: 'Remaining (h)', dataIndex: 'remaining', width: 130, align: 'right' },
  ]
  const reqCols: ColumnsType<PermissionRequest> = [
    { title: 'Request #', dataIndex: 'requestNo', width: 120 },
    { title: 'Date', dataIndex: 'permissionDate', width: 110 },
    { title: 'From', dataIndex: 'startTime', width: 90 },
    { title: 'To', dataIndex: 'endTime', width: 90 },
    { title: 'Hours', dataIndex: 'hours', width: 90, align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag>{s}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="My balances"
        size="small"
        extra={
          <Button type="primary" onClick={() => navigate('/permission/requests/new')}>
            Request permission
          </Button>
        }
      >
        <Table rowKey="id" size="small" columns={balanceCols} dataSource={balances} pagination={false} />
      </Card>
      <Card title="My requests" size="small">
        <Table rowKey="id" size="small" columns={reqCols} dataSource={requests} pagination={false} />
      </Card>
    </Space>
  )
}

// ============================================================================
//  Business trips tab
// ============================================================================
function BusinessTripsTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [rows, setRows] = useState<BusinessTrip[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    selfApi
      .businessTrips()
      .then(setRows)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const cols: ColumnsType<BusinessTrip> = [
    { title: 'Trip #', dataIndex: 'tripNo', width: 110 },
    { title: 'Destination', render: (_, r) => `${r.destinationCity}${r.destinationCountry ? `, ${r.destinationCountry}` : ''}` },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    { title: 'End', dataIndex: 'endDate', width: 110 },
    { title: 'Days', dataIndex: 'totalDays', width: 80, align: 'right' },
    { title: 'Status', dataIndex: 'status', width: 110, render: (s: string) => <Tag>{s}</Tag> },
  ]

  return (
    <Card
      title="My business trips"
      size="small"
      extra={
        <Button type="primary" onClick={() => navigate('/business-trips/new')}>
          New business trip
        </Button>
      }
    >
      <Table rowKey="id" size="small" columns={cols} dataSource={rows} pagination={false} />
    </Card>
  )
}

// ============================================================================
//  Timesheets tab
// ============================================================================
function TimesheetsTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [rows, setRows] = useState<Timesheet[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    selfApi
      .timesheets()
      .then(setRows)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const cols: ColumnsType<Timesheet> = [
    { title: 'Timesheet #', dataIndex: 'timesheetNo', width: 130 },
    {
      title: 'Period',
      render: (_, r) => `${r.periodYear}-${String(r.periodMonth).padStart(2, '0')}`,
      width: 100,
    },
    { title: 'Work days', dataIndex: 'workDays', width: 100, align: 'right' },
    { title: 'Worked (h)', dataIndex: 'totalWorkedHours', width: 110, align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (s: string) => <Tag>{s}</Tag>,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/timesheets/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card title="My timesheets" size="small">
      <Table rowKey="id" size="small" columns={cols} dataSource={rows} pagination={false} />
    </Card>
  )
}

// ============================================================================
//  My Benefits tab (HCM_11 M385)
// ============================================================================
function BenefitsTab() {
  const { message } = AntdApp.useApp()
  const { Title, Text } = Typography
  const [enrolments, setEnrolments] = useState<EnrollmentResponse[]>([])
  const [claims, setClaims] = useState<ClaimResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([benefitsApi.myEnrolments(), claimsApi.mine().catch(() => [])])
      .then(([e, c]) => { setEnrolments(e); setClaims(c) })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load benefits'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const active = enrolments.filter((e) => e.status === 'ENROLLED')
  const myEmployee = active.reduce((s, e) => s + (e.employeeContribution ?? 0), 0)
  const myEmployer = active.reduce((s, e) => s + (e.employerContribution ?? 0), 0)
  const fmt2 = (n?: number | null) => (n == null ? '—' : n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))

  const claimCols: ColumnsType<ClaimResponse> = [
    { title: 'Claim #', dataIndex: 'claimNo', width: 110 },
    { title: 'Date', dataIndex: 'claimDate', width: 110 },
    { title: 'Total', align: 'right', width: 110, render: (_, r) => `${fmt2(r.totalAmount)} ${r.currency}` },
    { title: 'Approved', align: 'right', width: 110, render: (_, r) => (r.approvedAmount != null ? fmt2(r.approvedAmount) : '—') },
    { title: 'Status', width: 110, render: (_, r) => <Tag color={CLAIM_STATUS_COLOR[r.status]}>{r.status}</Tag> },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col span={8}><Statistic title="Active plans" value={active.length} /></Col>
        <Col span={8}><Statistic title="Your contribution / mo" value={myEmployee} precision={2} suffix="AZN" /></Col>
        <Col span={8}><Statistic title="Employer contribution / mo" value={myEmployer} precision={2} suffix="AZN" /></Col>
      </Row>

      {enrolments.length === 0 ? (
        <Empty description="You aren't enrolled in any benefits yet. Ask HR if you think this is wrong." />
      ) : (
        enrolments.map((r) => (
          <Card key={r.id} size="small"
            title={<Space><Text strong>{r.planName ?? '—'}</Text><Tag color={ENROLLMENT_STATUS_COLOR[r.status]}>{r.status}</Tag></Space>}>
            <Row gutter={16}>
              <Col span={6}><Statistic title="Start" value={r.startDate} /></Col>
              <Col span={6}><Statistic title="Tier" value={r.coverageTierCode ?? 'flat'} /></Col>
              <Col span={6}><Statistic title="Your share / mo" value={r.employeeContribution ?? 0} precision={2} suffix="AZN" /></Col>
              <Col span={6}><Statistic title="Dependants" value={r.dependentsCovered} /></Col>
            </Row>
            {(r.coveredDependents ?? []).length > 0 && (
              <Text type="secondary">Covers: {(r.coveredDependents ?? []).map((d) => d.name).filter(Boolean).join(', ')}</Text>
            )}
          </Card>
        ))
      )}

      {claims.length > 0 && (
        <Card size="small" title={<Title level={5} style={{ margin: 0 }}>My claims</Title>}>
          <Table rowKey="id" columns={claimCols} dataSource={claims} size="small" pagination={{ pageSize: 10 }} />
        </Card>
      )}
    </Space>
  )
}

// ============================================================================
//  Team calendar tab (M431)
// ============================================================================
function TeamCalendarTab() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [isManager, setIsManager] = useState<boolean | null>(null)
  const [calendarData, setCalendarData] = useState<any>(null)

  useEffect(() => {
    api
      .get('/self/team-calendar')
      .then((r) => {
        if (r.data.manager === false) {
          setIsManager(false)
        } else {
          setIsManager(true)
          setCalendarData(r.data.calendar)
        }
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load team calendar'),
      )
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />
  if (isManager === false) return null

  // Group entries by day
  const grouped = new Map<string, any[]>()
  if (calendarData?.entries) {
    calendarData.entries.forEach((entry: any) => {
      if (!grouped.has(entry.date)) {
        grouped.set(entry.date, [])
      }
      grouped.get(entry.date)!.push(entry)
    })
  }

  const sortedDates = Array.from(grouped.keys()).sort()

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {sortedDates.length === 0 ? (
        <Empty description="No team leave in the selected window" />
      ) : (
        sortedDates.map((date) => (
          <Card key={date} size="small" title={date}>
            <Table
              rowKey={(r) => r.employeeId + r.leaveTypeCode}
              dataSource={grouped.get(date)}
              columns={[
                { title: 'Employee', dataIndex: 'employeeName' },
                { title: 'Leave type', dataIndex: 'leaveTypeName' },
                { title: 'Days', dataIndex: 'daysCount', width: 80 },
              ]}
              pagination={false}
              size="small"
            />
          </Card>
        ))
      )}
    </Space>
  )
}

// ============================================================================
//  Payroll & Compensation tab (M351, M354, M356, M368)
// ============================================================================
function PayrollTab() {
  const { message } = AntdApp.useApp()
  const [payslips, setPayslips] = useState<PayslipInfo[]>([])
  const [advances, setAdvances] = useState<SalaryAdvance[]>([])
  const [loading, setLoading] = useState(true)
  const [advanceModalOpen, setAdvanceModalOpen] = useState(false)
  const [advanceAmount, setAdvanceAmount] = useState<number>()
  const [advanceReason, setAdvanceReason] = useState('')
  const [certYear, setCertYear] = useState(new Date().getFullYear())
  const [compStatementYear, setCompStatementYear] = useState(new Date().getFullYear())

  useEffect(() => {
    Promise.all([
      payrollApi.myPayslips(),
      payrollApi.advances({ status: 'PENDING' }),
    ])
      .then(([ps, adv]) => {
        setPayslips(ps)
        setAdvances(adv)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="My payslips" size="small">
        <Table
          rowKey="runId"
          size="small"
          pagination={false}
          dataSource={payslips}
          columns={[
            {
              title: 'Period',
              render: (_, p) => `${p.periodYear}-${String(p.periodMonth).padStart(2, '0')}`,
              width: 110,
            },
            { title: 'Net amount', dataIndex: 'netAmount', width: 130, align: 'right' },
            {
              title: 'Generated',
              dataIndex: 'generatedAt',
              width: 140,
              render: (v?: string) => (v ? new Date(v).toLocaleDateString() : '—'),
            },
            {
              title: '',
              width: 100,
              render: (_, p) => (
                <Button
                  size="small"
                  onClick={() => payrollApi.downloadMyPayslip(p.runId, p.periodYear, p.periodMonth)}
                >
                  Download
                </Button>
              ),
            },
          ]}
        />
      </Card>
      <Card
        title="My salary advances"
        size="small"
        extra={
          <Button size="small" type="primary" onClick={() => setAdvanceModalOpen(true)}>
            Request advance
          </Button>
        }
      >
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={advances}
          columns={[
            { title: 'Requested', dataIndex: 'requestedAmount', width: 130, align: 'right' },
            { title: 'Approved', dataIndex: 'approvedAmount', width: 130, align: 'right', render: (v?: number) => v ?? '—' },
            { title: 'Status', dataIndex: 'status', width: 120, render: (s: string) => <Tag>{s}</Tag> },
            { title: 'Reason', dataIndex: 'reason', render: (r?: string) => r ?? '—' },
          ]}
        />
      </Card>
      <Card title="My tax certificate" size="small">
        <Space>
          <Typography.Text>Year:</Typography.Text>
          <Select
            value={certYear}
            onChange={(v: number) => setCertYear(v)}
            style={{ width: 100 }}
            options={[2024, 2025, 2026].map((y) => ({ value: y, label: y }))}
          />
          <Button
            onClick={async () => {
              try {
                await payrollApi.downloadMyTaxCertificate(certYear)
                message.success('Certificate downloaded')
              } catch (err) {
                message.error((err as any).response?.data?.message ?? 'Download failed')
              }
            }}
          >
            Download certificate
          </Button>
        </Space>
      </Card>

      <Card title="My total compensation" size="small">
        <Space>
          <Typography.Text>Year:</Typography.Text>
          <Select
            value={compStatementYear}
            onChange={(v: number) => setCompStatementYear(v)}
            style={{ width: 100 }}
            options={[2024, 2025, 2026].map((y) => ({ value: y, label: y }))}
          />
          <Button
            onClick={async () => {
              try {
                await compensationApi.downloadMyStatement(compStatementYear)
                message.success('Statement downloaded')
              } catch (err) {
                message.error((err as any).response?.data?.message ?? 'Download failed or not released yet')
              }
            }}
          >
            Download statement
          </Button>
        </Space>
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
          Only RELEASED statements are available for download.
        </Typography.Text>
      </Card>

      <Modal
        title="Request salary advance"
        open={advanceModalOpen}
        onCancel={() => {
          setAdvanceModalOpen(false)
          setAdvanceAmount(undefined)
          setAdvanceReason('')
        }}
        onOk={async () => {
          if (!advanceAmount) {
            message.error('Amount is required')
            return
          }
          try {
            await payrollApi.createAdvance({
              employeeId: '', // backend derives from context
              requestedAmount: advanceAmount,
              reason: advanceReason || undefined,
            })
            message.success('Advance requested')
            setAdvanceModalOpen(false)
            setAdvanceAmount(undefined)
            setAdvanceReason('')
            // reload
            const adv = await payrollApi.advances({ status: 'PENDING' })
            setAdvances(adv)
          } catch (err) {
            message.error((err as any).response?.data?.message ?? 'Request failed')
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>Amount:</Typography.Text>
          <Input
            type="number"
            value={advanceAmount}
            onChange={(e) => setAdvanceAmount(Number(e.target.value))}
            placeholder="Requested amount"
          />
          <Typography.Text>Reason:</Typography.Text>
          <Input.TextArea
            value={advanceReason}
            onChange={(e) => setAdvanceReason(e.target.value)}
            placeholder="Reason for advance"
            rows={3}
          />
        </Space>
      </Modal>
    </Space>
  )
}

// ============================================================================
//  Assets tab — M459 ESS assets
// ============================================================================
type AssetStatus = 'ASSIGNED' | 'RETURNED' | 'LOST' | 'DAMAGED' | 'WRITTEN_OFF'
type AssetType = 'LAPTOP' | 'MOBILE_PHONE' | 'SIM_CARD' | 'VEHICLE' | 'ACCESS_CARD' | 'UNIFORM' | 'TOOLS' | 'EQUIPMENT' | 'LOCKER' | 'FUEL_CARD' | 'CREDIT_CARD' | 'TABLET' | 'HEADSET' | 'OTHER'

interface AssetResponse {
  id: string
  assetType: AssetType
  assetName: string
  assetIdentifier?: string
  description?: string
  status: AssetStatus
  assignedAt: string
  expectedReturnDate?: string
  conditionAtAssignment?: string
  notes?: string
}

const ASSET_STATUS_COLOR: Record<AssetStatus, string> = {
  ASSIGNED: 'blue',
  RETURNED: 'green',
  LOST: 'red',
  DAMAGED: 'orange',
  WRITTEN_OFF: 'default',
}

function AssetsTab() {
  const { message } = AntdApp.useApp()
  const [employeeId, setEmployeeId] = useState<string | null>(null)
  const [assets, setAssets] = useState<AssetResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [damageModalOpen, setDamageModalOpen] = useState(false)
  const [selectedAsset, setSelectedAsset] = useState<AssetResponse | null>(null)
  const [damageForm] = Form.useForm()

  useEffect(() => {
    api
      .get<{ id: string }>('/self/employee')
      .then((r) => {
        setEmployeeId(r.data.id)
        api
          .get<AssetResponse[]>(`/self/assets/${r.data.id}`)
          .then((r) => setAssets(r.data))
          .catch(() => message.error('Failed to load assets'))
          .finally(() => setLoading(false))
      })
      .catch(() => {
        setLoading(false)
        message.error('No employee record is linked to your user')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openReportDamage = (asset: AssetResponse) => {
    setSelectedAsset(asset)
    damageForm.resetFields()
    damageForm.setFieldsValue({ assetId: asset.id, caseType: 'DAMAGE' })
    setDamageModalOpen(true)
  }

  const submitDamageReport = async () => {
    if (!employeeId || !selectedAsset) return
    try {
      const values = await damageForm.validateFields()
      await api.post(`/self/assets/${employeeId}/report-damage`, {
        assetId: selectedAsset.id,
        caseType: values.caseType,
        description: values.description,
      })
      message.success('Damage/loss reported')
      setDamageModalOpen(false)
      setSelectedAsset(null)
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Report failed')
    }
  }

  const assetColumns: ColumnsType<AssetResponse> = [
    {
      title: 'Asset',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{r.assetName}</Typography.Text>
          {r.assetIdentifier && (
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.assetIdentifier}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'assetType',
      render: (t: AssetType) => <Tag>{t}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: AssetStatus) => <Tag color={ASSET_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Assigned',
      dataIndex: 'assignedAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: 'Condition',
      dataIndex: 'conditionAtAssignment',
      render: (v?: string) => (v ? <Tag color="cyan">{v}</Tag> : '—'),
    },
    {
      title: 'Actions',
      render: (_, r) =>
        r.status === 'ASSIGNED' ? (
          <a onClick={() => openReportDamage(r)}>Report Damage</a>
        ) : null,
    },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Card title="My Assets" size="small">
        <Table
          rowKey="id"
          columns={assetColumns}
          dataSource={assets}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        open={damageModalOpen}
        title="Report Damage or Loss"
        onCancel={() => {
          setDamageModalOpen(false)
          setSelectedAsset(null)
        }}
        onOk={submitDamageReport}
        okText="Report"
      >
        {selectedAsset && (
          <Form form={damageForm} layout="vertical">
            <Typography.Paragraph type="secondary">
              Asset: <strong>{selectedAsset.assetName}</strong>
            </Typography.Paragraph>
            <Form.Item name="caseType" label="Type" rules={[{ required: true }]}>
              <Select>
                <Select.Option value="DAMAGE">Damage</Select.Option>
                <Select.Option value="LOSS">Loss</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="description" label="Description" rules={[{ required: true }]}>
              <Input.TextArea rows={3} placeholder="Describe the damage or loss" />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Space>
  )
}

// ============================================================================
//  Loans tab — M465 ESS loans
// ============================================================================
type LoanRequestStatus =
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'DISBURSED'

interface LoanType {
  id: string
  code: string
  name: string
  description?: string
  maxAmount?: number
  maxMultipleOfNet: number
  maxMonths: number
  interestRatePct: number
  minTenureMonths: number
  maxActiveLoans: number
  active: boolean
}

interface LoanRequest {
  id: string
  requestNo: string
  employeeId: string
  loanTypeId: string
  loanTypeName: string
  requestedAmount: number
  requestedMonths: number
  approvedAmount?: number
  approvedMonths?: number
  purpose?: string
  status: LoanRequestStatus
  eligibilityNotes?: string
  rejectionReason?: string
  submittedAt: string
  approvedAt?: string
}

interface LoanInstallment {
  id: string
  installmentNumber: number
  dueYear: number
  dueMonth: number
  installmentAmount: number
  principalPortion: number
  interestPortion: number
  remainingBalance: number
  status: string
  paidAt?: string
}

const LOAN_REQUEST_STATUS_COLOR: Record<LoanRequestStatus, string> = {
  SUBMITTED: 'orange',
  APPROVED: 'blue',
  REJECTED: 'red',
  CANCELLED: 'default',
  DISBURSED: 'green',
}

function LoansTab() {
  const { message } = AntdApp.useApp()
  const [employeeId, setEmployeeId] = useState<string | null>(null)
  const [requests, setRequests] = useState<LoanRequest[]>([])
  const [loanTypes, setLoanTypes] = useState<LoanType[]>([])
  const [loading, setLoading] = useState(true)
  const [requestModalOpen, setRequestModalOpen] = useState(false)
  const [statementOpen, setStatementOpen] = useState(false)
  const [installments, setInstallments] = useState<LoanInstallment[]>([])
  const [currentRequest, setCurrentRequest] = useState<LoanRequest | null>(null)
  const [form] = Form.useForm()

  const loadRequests = (empId: string) => {
    api
      .get<LoanRequest[]>(`/self/loans/${empId}`)
      .then((r) => setRequests(r.data))
      .catch(() => message.error('Failed to load loan requests'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    api
      .get<{ id: string }>('/self/employee')
      .then((r) => {
        setEmployeeId(r.data.id)
        loadRequests(r.data.id)
        api
          .get<LoanType[]>('/payroll/loan-types')
          .then((r) => setLoanTypes(r.data.filter((t) => t.active)))
          .catch(() => {})
      })
      .catch(() => {
        setLoading(false)
        message.error('No employee record is linked to your user')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openRequestModal = () => {
    form.resetFields()
    setRequestModalOpen(true)
  }

  const submitRequest = async () => {
    if (!employeeId) return
    try {
      const values = await form.validateFields()
      await api.post(`/self/loans/${employeeId}`, values)
      message.success('Loan request submitted')
      setRequestModalOpen(false)
      loadRequests(employeeId)
    } catch (e: any) {
      if (e?.errorFields) return
      const msg = e?.response?.data?.message ?? 'Request failed'
      message.error(msg)
    }
  }

  const openStatement = (request: LoanRequest) => {
    if (!employeeId) return
    setCurrentRequest(request)
    setStatementOpen(true)
    api
      .get<LoanInstallment[]>(`/self/loans/${employeeId}/requests/${request.id}/installments`)
      .then((r) => setInstallments(r.data))
      .catch(() => message.error('Failed to load installments'))
  }

  const requestColumns: ColumnsType<LoanRequest> = [
    { title: 'Request No', dataIndex: 'requestNo', width: 120 },
    { title: 'Loan Type', dataIndex: 'loanTypeName' },
    {
      title: 'Requested',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{r.requestedAmount.toFixed(2)} AZN</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {r.requestedMonths} months
          </Typography.Text>
        </Space>
      ),
      width: 120,
      align: 'right',
    },
    {
      title: 'Approved',
      render: (_, r) =>
        r.approvedAmount ? (
          <Space direction="vertical" size={0}>
            <Typography.Text>{r.approvedAmount.toFixed(2)} AZN</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.approvedMonths} months
            </Typography.Text>
          </Space>
        ) : (
          '—'
        ),
      width: 120,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: LoanRequestStatus) => (
        <Tag color={LOAN_REQUEST_STATUS_COLOR[s]}>{s}</Tag>
      ),
    },
    {
      title: 'Actions',
      width: 120,
      render: (_, r) =>
        r.status === 'APPROVED' || r.status === 'DISBURSED' ? (
          <a onClick={() => openStatement(r)}>Statement</a>
        ) : r.status === 'REJECTED' && r.rejectionReason ? (
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {r.rejectionReason}
          </Typography.Text>
        ) : null,
    },
  ]

  const installmentColumns: ColumnsType<LoanInstallment> = [
    { title: '#', dataIndex: 'installmentNumber', width: 50 },
    {
      title: 'Due',
      render: (_, r) => `${r.dueYear}/${String(r.dueMonth).padStart(2, '0')}`,
      width: 100,
    },
    {
      title: 'Amount',
      dataIndex: 'installmentAmount',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Principal',
      dataIndex: 'principalPortion',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Interest',
      dataIndex: 'interestPortion',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Balance',
      dataIndex: 'remainingBalance',
      align: 'right',
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: string) => <Tag color={s === 'PAID' ? 'green' : 'orange'}>{s}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Card
        title="My Loan Requests"
        size="small"
        extra={
          <Button type="primary" onClick={openRequestModal}>
            Request Loan
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={requestColumns}
          dataSource={requests}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        open={requestModalOpen}
        title="Request a Loan"
        onCancel={() => setRequestModalOpen(false)}
        onOk={submitRequest}
        okText="Submit Request"
        width={500}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="loanTypeId" label="Loan Type" rules={[{ required: true }]}>
            <Select placeholder="Select loan type">
              {loanTypes.map((t) => (
                <Select.Option key={t.id} value={t.id}>
                  {t.name}
                  {t.description && (
                    <Typography.Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
                      {t.description}
                    </Typography.Text>
                  )}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="requestedAmount"
            label="Requested Amount (AZN)"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} step={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="requestedMonths"
            label="Repayment Period (months)"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={120} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="purpose" label="Purpose">
            <Input.TextArea rows={3} placeholder="Reason for loan request" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={statementOpen}
        onClose={() => setStatementOpen(false)}
        width={720}
        title={currentRequest ? `Loan Statement — ${currentRequest.requestNo}` : ''}
      >
        {currentRequest && (
          <>
            <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
              Loan Type: <strong>{currentRequest.loanTypeName}</strong>
              <br />
              Approved Amount: <strong>{currentRequest.approvedAmount?.toFixed(2)} AZN</strong> over{' '}
              <strong>{currentRequest.approvedMonths} months</strong>
            </Typography.Paragraph>
            <Table
              rowKey="id"
              size="small"
              columns={installmentColumns}
              dataSource={installments}
              pagination={false}
            />
          </>
        )}
      </Drawer>
    </Space>
  )
}

// ============================================================================
//  Learning tab
// ============================================================================
// Colour per assignment status — mirrors M96 drawer.
const PATH_STATUS_COLOR: Record<PathAssignmentStatus, string> = {
  ASSIGNED: 'blue',
  IN_PROGRESS: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
}

function LearningTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [certs, setCerts] = useState<Certificate[]>([])
  const [comps, setComps] = useState<EmployeeCompetency[]>([])
  const [pathAssignments, setPathAssignments] = useState<AssignmentResponse[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    Promise.all([
      selfApi.enrollments(),
      selfApi.certificates(),
      selfApi.competencies(),
      pathAssignmentsApi.mine(),
    ])
      .then(([e, c, cm, pa]) => {
        setEnrollments(e)
        setCerts(c)
        setComps(cm)
        setPathAssignments(pa)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const enrCols: ColumnsType<Enrollment> = [
    { title: 'Enroll #', dataIndex: 'enrollmentNo', width: 120 },
    {
      title: 'Course',
      dataIndex: 'courseId',
      render: (id: string) => (
        <Button type="link" size="small" onClick={() => navigate(`/learning/courses/${id}`)}>
          Open course
        </Button>
      ),
    },
    { title: 'Status', dataIndex: 'status', width: 120, render: (s: string) => <Tag>{s}</Tag> },
    { title: 'Attempts', dataIndex: 'attemptsUsed', width: 100, align: 'right' },
    {
      title: 'Best score',
      dataIndex: 'bestScorePercent',
      width: 110,
      align: 'right',
      render: (v: number | null) => (v != null ? `${v}%` : '—'),
    },
  ]
  const certCols: ColumnsType<Certificate> = [
    { title: 'Cert #', dataIndex: 'certificateNo', width: 130 },
    { title: 'Issued', dataIndex: 'issuedAt', width: 130, render: (s: string) => s.slice(0, 10) },
    { title: 'Valid until', dataIndex: 'validUntil', width: 130 },
    {
      title: 'Score',
      dataIndex: 'scorePercent',
      width: 90,
      align: 'right',
      render: (s: number) => <Tag color="green">{s}%</Tag>,
    },
  ]
  const compCols: ColumnsType<EmployeeCompetency> = [
    { title: 'Competency #', dataIndex: 'competencyId' },
    { title: 'Proficiency', dataIndex: 'proficiency', width: 110, align: 'right', render: (p: number) => <Tag color="cyan">Level {p}</Tag> },
    { title: 'Source', dataIndex: 'source', width: 110 },
    {
      title: 'Awarded',
      dataIndex: 'awardedAt',
      width: 130,
      render: (s: string) => s.slice(0, 10),
    },
  ]

  // Active assignments first, then completed, then cancelled.
  const sortedAssignments = [...pathAssignments].sort((a, b) => {
    const order: Record<PathAssignmentStatus, number> = {
      IN_PROGRESS: 0, ASSIGNED: 1, COMPLETED: 2, CANCELLED: 3,
    }
    return (order[a.status] ?? 9) - (order[b.status] ?? 9)
  })

  const devPathItems = sortedAssignments.map((a) => {
    const isActive = a.status === 'ASSIGNED' || a.status === 'IN_PROGRESS'
    const daysLeft = a.targetCompletionDate
      ? Math.ceil(
          (new Date(a.targetCompletionDate).getTime() - Date.now()) / 86_400_000,
        )
      : null
    const overdue = daysLeft !== null && daysLeft < 0
    return {
      key: a.id,
      label: (
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <Tag color={PATH_STATUS_COLOR[a.status]}>{a.status.replace(/_/g, ' ')}</Tag>
            <Typography.Text strong>{a.pathName ?? 'Learning path'}</Typography.Text>
          </Space>
          <Space>
            {a.targetCompletionDate && (
              <Typography.Text
                type={overdue ? 'danger' : 'secondary'}
                style={{ fontSize: 12 }}
              >
                {overdue
                  ? `${-daysLeft!} day${daysLeft === -1 ? '' : 's'} overdue`
                  : `Due ${new Date(a.targetCompletionDate).toLocaleDateString()}`}
              </Typography.Text>
            )}
            <Tooltip title={`${a.completedSteps} of ${a.totalSteps} courses completed`}>
              <Progress
                percent={a.progressPercent}
                size="small"
                style={{ width: 100 }}
                status={a.status === 'COMPLETED' ? 'success' : overdue ? 'exception' : 'active'}
              />
            </Tooltip>
          </Space>
        </Space>
      ),
      children: (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {a.notes && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              <strong>Manager note:</strong> {a.notes}
            </Typography.Text>
          )}
          {a.steps.length === 0 ? (
            <Typography.Text type="secondary">No steps configured for this path.</Typography.Text>
          ) : (
            a.steps.map((step) => (
              <Space key={step.stepId} style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag style={{ minWidth: 28, textAlign: 'center' }}>{step.stepOrder}</Tag>
                  <Button
                    type="link"
                    size="small"
                    style={{ padding: 0 }}
                    onClick={() => navigate(`/learning/courses/${step.courseId}`)}
                  >
                    {step.courseCode
                      ? `${step.courseCode} — ${step.courseTitle ?? ''}`
                      : step.courseTitle ?? step.courseId}
                  </Button>
                  {step.requiredToAdvance && (
                    <Tag color="red" style={{ fontSize: 11 }}>required</Tag>
                  )}
                </Space>
                <Tag
                  color={
                    step.completed ? 'green'
                    : step.status === 'IN_PROGRESS' ? 'gold'
                    : step.status === 'FAILED' ? 'red'
                    : 'default'
                  }
                >
                  {step.status.replace(/_/g, ' ')}
                </Tag>
              </Space>
            ))
          )}
        </Space>
      ),
      style: { borderLeft: isActive ? `3px solid ${overdue ? '#ff4d4f' : '#1677ff'}` : undefined },
    }
  })

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* M100 — My development paths */}
      <Card
        title={
          <Space>
            <span>My development paths</span>
            {sortedAssignments.filter((a) => a.status !== 'CANCELLED' && a.status !== 'COMPLETED').length > 0 && (
              <Tag color="blue">
                {sortedAssignments.filter((a) => a.status === 'IN_PROGRESS' || a.status === 'ASSIGNED').length} active
              </Tag>
            )}
          </Space>
        }
        size="small"
      >
        {sortedAssignments.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="No learning paths assigned yet. Ask your HR team to set up a development plan."
          />
        ) : (
          <Collapse
            bordered={false}
            ghost
            size="small"
            items={devPathItems}
          />
        )}
      </Card>

      <Card
        title="My enrollments"
        size="small"
        extra={
          <Button onClick={() => navigate('/learning/courses')}>Browse catalog</Button>
        }
      >
        <Table rowKey="id" size="small" columns={enrCols} dataSource={enrollments} pagination={false} />
      </Card>
      <Card title="My certificates" size="small">
        <Table rowKey="id" size="small" columns={certCols} dataSource={certs} pagination={false} />
      </Card>
      <Card title="My competencies" size="small">
        <Table rowKey="id" size="small" columns={compCols} dataSource={comps} pagination={false} />
      </Card>
    </Space>
  )
}

// ============================================================================
//  Performance tab
// ============================================================================
function PerformanceTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [reviews, setReviews] = useState<PerformanceReview[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    selfApi
      .reviews()
      .then(setReviews)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const cols: ColumnsType<PerformanceReview> = [
    { title: 'Review #', dataIndex: 'reviewNo', width: 120 },
    { title: 'Self', dataIndex: 'selfRating', width: 80, align: 'right', render: (v: number | null) => v ?? '—' },
    { title: 'Manager', dataIndex: 'managerRating', width: 90, align: 'right', render: (v: number | null) => v ?? '—' },
    { title: 'Goal score', dataIndex: 'goalScore', width: 110, align: 'right', render: (v: number | null) => v ?? '—' },
    {
      title: 'Final',
      dataIndex: 'finalRating',
      width: 90,
      align: 'right',
      render: (v: number | null) => (v != null ? <Typography.Text strong>{v}</Typography.Text> : '—'),
    },
    { title: 'Band', dataIndex: 'finalBand', width: 180 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 150,
      render: (s: string) => <Tag>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/performance/reviews/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card title="My performance reviews" size="small">
      <Table rowKey="id" size="small" columns={cols} dataSource={reviews} pagination={false} />
    </Card>
  )
}

// ============================================================================
//  M446 — My Warnings Widget
// ============================================================================
function MyWarningsWidget() {
  const { message } = AntdApp.useApp()
  const [warnings, setWarnings] = useState<WarningRecord[]>([])
  const [loading, setLoading] = useState(true)

  const fetchWarnings = async () => {
    try {
      const { data } = await api.get('/self/warnings')
      setWarnings(data)
    } catch (err: any) {
      console.error('Failed to load warnings:', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchWarnings()
  }, [])

  const handleAcknowledge = async (warningId: string) => {
    try {
      await api.post(`/self/warnings/${warningId}/acknowledge`, {})
      message.success('Warning acknowledged')
      fetchWarnings()
    } catch (err: any) {
      message.error(err.message || 'Failed to acknowledge warning')
    }
  }

  if (loading) return null
  if (warnings.length === 0) return null

  const unacknowledged = warnings.filter((w) => !w.acknowledgedAt)

  const WARNING_LEVEL_COLOR: Record<string, string> = {
    VERBAL: 'blue',
    WRITTEN: 'orange',
    FINAL: 'red',
    TERMINATION: 'error',
  }

  return (
    <Card title="My Warnings" size="small" style={{ marginBottom: 16 }}>
      <Space direction="vertical" style={{ width: '100%' }}>
        {warnings.map((w) => (
          <Card key={w.id} size="small" style={{ background: w.acknowledgedAt ? '#f9f9f9' : '#fff6e6' }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Space>
                <Tag color={WARNING_LEVEL_COLOR[w.level] || 'default'}>{w.level}</Tag>
                {w.expiresAt && (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Expires: {dayjs(w.expiresAt).format('YYYY-MM-DD')}
                  </Typography.Text>
                )}
              </Space>
              <Typography.Text>{w.reason}</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Issued: {dayjs(w.issuedAt).format('YYYY-MM-DD')}
              </Typography.Text>
              {w.acknowledgedAt ? (
                <Tag color="green">Acknowledged on {dayjs(w.acknowledgedAt).format('YYYY-MM-DD')}</Tag>
              ) : (
                <Button size="small" type="primary" onClick={() => handleAcknowledge(w.id)}>
                  Acknowledge
                </Button>
              )}
            </Space>
          </Card>
        ))}
        {unacknowledged.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={`${unacknowledged.length} warning(s) pending acknowledgment`}
          />
        )}
      </Space>
    </Card>
  )
}

// ============================================================================
//  M481 — Reward Points Widget
// ============================================================================
function MyRewardPointsWidget({ employeeId }: { employeeId: string }) {
  const { message } = AntdApp.useApp()
  const [wallet, setWallet] = useState<any>(null)
  const [catalog, setCatalog] = useState<any[]>([])
  const [redemptions, setRedemptions] = useState<any[]>([])
  const [open, setOpen] = useState(false)
  const [redeemModal, setRedeemModal] = useState(false)
  const [form] = Form.useForm()

  const fetchData = async () => {
    try {
      const [walletRes, catalogRes, redemptionsRes] = await Promise.all([
        api.get(`/engagement/rewards/wallet/${employeeId}`),
        api.get('/engagement/rewards/catalog', { params: { activeOnly: true } }),
        api.get(`/engagement/rewards/redemptions/my/${employeeId}`),
      ])
      setWallet(walletRes.data)
      setCatalog(catalogRes.data)
      setRedemptions(redemptionsRes.data)
    } catch (err: any) {
      console.error('Failed to load reward data:', err)
    }
  }

  useEffect(() => {
    if (employeeId) fetchData()
  }, [employeeId])

  const handleRedeem = async (values: any) => {
    try {
      await api.post('/engagement/rewards/redeem', {
        catalogItemId: values.catalogItemId,
        deliveryAddress: values.deliveryAddress,
      })
      message.success('Reward redeemed! Your request is pending HR approval.')
      setRedeemModal(false)
      form.resetFields()
      fetchData()
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Failed to redeem')
    }
  }

  if (!wallet || catalog.length === 0) return null

  return (
    <>
      <Card title="My Reward Points" extra={<Button type="link" onClick={() => setOpen(true)}>View catalog</Button>}>
        <Row gutter={16}>
          <Col span={8}>
            <Statistic title="Balance" value={wallet.balance || 0} suffix="pts" />
          </Col>
          <Col span={8}>
            <Statistic title="Earned" value={wallet.lifetimeEarned || 0} suffix="pts" />
          </Col>
          <Col span={8}>
            <Statistic title="Spent" value={wallet.lifetimeSpent || 0} suffix="pts" />
          </Col>
        </Row>
        {redemptions.filter((r: any) => r.status === 'REQUESTED').length > 0 && (
          <Tag color="blue" style={{ marginTop: 12 }}>
            {redemptions.filter((r: any) => r.status === 'REQUESTED').length} pending redemptions
          </Tag>
        )}
      </Card>

      <Modal
        title="Reward Catalog"
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={800}
      >
        <Tabs
          items={[
            {
              key: 'catalog',
              label: 'Catalog',
              children: (
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  {catalog.map((item: any) => (
                    <Card key={item.id} size="small">
                      <Row justify="space-between" align="middle">
                        <Col flex="auto">
                          <Typography.Title level={5} style={{ margin: 0 }}>
                            {item.name}
                          </Typography.Title>
                          <Typography.Text type="secondary">{item.description}</Typography.Text>
                          <div style={{ marginTop: 8 }}>
                            <Tag color="blue">{item.points} points</Tag>
                            {item.category && <Tag>{item.category}</Tag>}
                          </div>
                        </Col>
                        <Col>
                          <Button
                            type="primary"
                            disabled={(wallet?.balance || 0) < item.points}
                            onClick={() => {
                              form.setFieldsValue({ catalogItemId: item.id })
                              setRedeemModal(true)
                            }}
                          >
                            Redeem
                          </Button>
                        </Col>
                      </Row>
                    </Card>
                  ))}
                </Space>
              ),
            },
            {
              key: 'redemptions',
              label: 'My Redemptions',
              children: (
                <Table
                  dataSource={redemptions}
                  columns={[
                    { title: 'No.', dataIndex: 'redemptionNo', key: 'redemptionNo' },
                    { title: 'Points', dataIndex: 'points', key: 'points' },
                    {
                      title: 'Status',
                      dataIndex: 'status',
                      key: 'status',
                      render: (s: string) => (
                        <Tag color={s === 'REQUESTED' ? 'blue' : s === 'FULFILLED' ? 'green' : 'red'}>
                          {s}
                        </Tag>
                      ),
                    },
                    {
                      title: 'Requested',
                      dataIndex: 'requestedAt',
                      key: 'requestedAt',
                      render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm'),
                    },
                  ]}
                  rowKey="id"
                  pagination={false}
                />
              ),
            },
          ]}
        />
      </Modal>

      <Modal
        title="Redeem Reward"
        open={redeemModal}
        onCancel={() => { setRedeemModal(false); form.resetFields() }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleRedeem} style={{ marginTop: 16 }}>
          <Form.Item name="catalogItemId" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="Delivery Address (optional)" name="deliveryAddress">
            <Input.TextArea rows={3} placeholder="Enter delivery address if needed" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ============================================================================
//  M440 — Pending Signatures Widget
// ============================================================================
function PendingSignaturesWidget() {
  const { message } = AntdApp.useApp()
  const [requests, setRequests] = useState<SignatureRequestDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [declineModal, setDeclineModal] = useState<string | null>(null)
  const [declineForm] = Form.useForm()

  const fetchRequests = async () => {
    try {
      const { data } = await api.get('/self/signatures')
      setRequests(data)
    } catch (err: any) {
      console.error('Failed to load signature requests:', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRequests()
  }, [])

  const handleSign = async (requestId: string) => {
    try {
      await api.post(`/self/signatures/${requestId}/sign`, {})
      message.success('Document signed')
      fetchRequests()
    } catch (err: any) {
      message.error(err.message || 'Failed to sign document')
    }
  }

  const handleDecline = async (requestId: string, values: any) => {
    try {
      await api.post(`/self/signatures/${requestId}/decline`, {
        reason: values.reason,
      })
      message.success('Signature declined')
      setDeclineModal(null)
      declineForm.resetFields()
      fetchRequests()
    } catch (err: any) {
      message.error(err.message || 'Failed to decline signature')
    }
  }

  if (loading) return null

  const pendingRequests = requests.filter((r) => r.status === 'PENDING')
  if (pendingRequests.length === 0) return null

  return (
    <>
      <Card title="Pending Signatures" size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          {pendingRequests.map((req) => {
            const mySigner = req.signers.find((s) => s.status === 'PENDING')
            if (!mySigner) return null

            return (
              <Card key={req.id} size="small">
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Typography.Text strong>{req.title}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Requested: {dayjs(req.createdAt).format('YYYY-MM-DD HH:mm')} by {req.createdBy}
                  </Typography.Text>
                  <Space>
                    <Button type="primary" size="small" onClick={() => handleSign(req.id)}>
                      Sign
                    </Button>
                    <Button size="small" danger onClick={() => setDeclineModal(req.id)}>
                      Decline
                    </Button>
                  </Space>
                </Space>
              </Card>
            )
          })}
        </Space>
      </Card>

      <Modal
        title="Decline Signature"
        open={!!declineModal}
        onCancel={() => {
          setDeclineModal(null)
          declineForm.resetFields()
        }}
        onOk={() => declineForm.submit()}
      >
        <Form
          form={declineForm}
          layout="vertical"
          onFinish={(values) => {
            if (declineModal) handleDecline(declineModal, values)
          }}
        >
          <Form.Item
            name="reason"
            label="Reason for declining"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ============================================================================
//  MyWorkspacePage shell
// ============================================================================
export function MyWorkspacePage() {
  const { message } = AntdApp.useApp()
  // M230 — wrapper-level strings come from selfService namespace.
  const { t } = useTranslation('selfService')
  const [profile, setProfile] = useState<SelfProfile | null>(null)
  const [summary, setSummary] = useState<SelfSummary | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  // ?tab=<key> is the deep-link the Self-Service tiles use ("Pay and Payslips"
  // → /my?tab=payroll). Kept in the URL so the tab survives a refresh / back.
  const [params, setParams] = useSearchParams()
  const active = params.get('tab') ?? 'dashboard'
  const setActive = useCallback(
    (key: string) => {
      const next = new URLSearchParams(params)
      if (key === 'dashboard') next.delete('tab')
      else next.set('tab', key)
      setParams(next, { replace: true })
    },
    [params, setParams],
  )

  useEffect(() => {
    Promise.all([selfApi.profile(), selfApi.summary()])
      .then(([p, s]) => {
        setProfile(p)
        setSummary(s)
      })
      .catch((err) => {
        const msg = err?.response?.data?.message ?? 'Failed to load'
        setError(msg)
        message.error(msg)
      })
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />
  if (error || !profile || !summary) {
    return (
      <Card>
        <Alert
          type="warning"
          showIcon
          message={t('noProfileAlert.title')}
          description={error ?? t('noProfileAlert.description')}
        />
      </Card>
    )
  }

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>{t('title')}</Typography.Title>}>
      <Tabs
        activeKey={active}
        onChange={setActive}
        items={[
          {
            key: 'dashboard',
            label: t('tabs.dashboard'),
            children: <Dashboard profile={profile} summary={summary} onJump={setActive} />,
          },
          { key: 'leave',         label: t('tabs.leave'),          children: <LeaveTab /> },
          { key: 'permission',    label: t('tabs.permission'),     children: <PermissionTab /> },
          { key: 'businessTrips', label: t('tabs.businessTrips'),  children: <BusinessTripsTab /> },
          { key: 'timesheets',    label: t('tabs.timesheets'),     children: <TimesheetsTab /> },
          { key: 'payroll',       label: 'Payroll',                children: <PayrollTab /> },
          { key: 'benefits',      label: 'Benefits',               children: <BenefitsTab /> },
          { key: 'assets',        label: 'My Assets',              children: <AssetsTab /> },
          { key: 'loans',         label: 'My Loans',               children: <LoansTab /> },
          { key: 'learning',      label: t('tabs.learning'),       children: <LearningTab /> },
          { key: 'performance',   label: t('tabs.performance'),    children: <PerformanceTab /> },
          { key: 'teamCalendar',  label: 'Team Calendar',          children: <TeamCalendarTab /> },
        ]}
      />
      {summary.mandatoryCoursesPending > 0 && (
        <Alert
          type="warning"
          style={{ marginTop: 16 }}
          showIcon
          message={t('mandatoryAlert.title', { count: summary.mandatoryCoursesPending })}
          description={t('mandatoryAlert.description')}
        />
      )}
      <Progress style={{ display: 'none' }} />
    </Card>
  )
}
