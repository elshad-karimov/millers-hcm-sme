import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  Progress,
  Row,
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
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
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
import type { PayrollResult } from '../api/payroll'
import type { Enrollment, Certificate, EmployeeCompetency } from '../api/learning'
import {
  pathAssignmentsApi,
  type AssignmentResponse,
  type PathAssignmentStatus,
} from '../api/learningPaths'
import type { PerformanceReview } from '../api/performance'
import { RequiredDocumentsWidget } from '../components/RequiredDocumentsWidget'

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
      {/* M263 — "📂 Documents owed to HR" widget. Renders nothing when
          the employee has no pending REQUIRED_DOCUMENT obligations,
          so the dashboard stays clean for the typical case. */}
      <RequiredDocumentsWidget />
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
            <Space>
              <Button onClick={() => navigate('/leave/requests/new')}>{t('quickActions.requestLeave')}</Button>
              <Button onClick={() => navigate('/permission/requests/new')}>{t('quickActions.requestPermission')}</Button>
              <Button onClick={() => navigate('/business-trips/new')}>{t('quickActions.newBusinessTrip')}</Button>
              <Button onClick={() => navigate('/letters/request')}>{t('quickActions.requestLetter')}</Button>
              <Button onClick={() => navigate('/personal-info/request')}>{t('quickActions.updatePersonalInfo')}</Button>
            </Space>
          </Col>
        </Row>
      </Card>

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
    </Space>
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
//  Payslips tab
// ============================================================================
function PayslipsTab() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<PayrollResult[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    selfApi
      .payslips()
      .then(setRows)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />

  const cols: ColumnsType<PayrollResult> = [
    { title: 'Payslip #', dataIndex: 'payslipNo', width: 130 },
    { title: 'Base salary', dataIndex: 'baseSalary', width: 130, align: 'right' },
    { title: 'Bonus', dataIndex: 'bonusAmount', width: 110, align: 'right' },
    { title: 'Overtime', dataIndex: 'overtimePay', width: 110, align: 'right' },
    {
      title: 'Gross',
      render: (_, r) => <Typography.Text>{r.grossAmount}</Typography.Text>,
      width: 130,
      align: 'right',
    },
    { title: 'Income tax', dataIndex: 'incomeTax', width: 110, align: 'right' },
    {
      title: 'Net',
      render: (_, r) => <Typography.Text strong>{r.netAmount}</Typography.Text>,
      width: 130,
      align: 'right',
    },
  ]

  return (
    <Card title="My payslips" size="small">
      <Table rowKey="id" size="small" columns={cols} dataSource={rows} pagination={false} />
    </Card>
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
  const [active, setActive] = useState<string>('dashboard')

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
          { key: 'payslips',      label: t('tabs.payslips'),       children: <PayslipsTab /> },
          { key: 'learning',      label: t('tabs.learning'),       children: <LearningTab /> },
          { key: 'performance',   label: t('tabs.performance'),    children: <PerformanceTab /> },
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
