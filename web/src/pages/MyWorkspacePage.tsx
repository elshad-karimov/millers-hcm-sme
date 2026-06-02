import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
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
import type { PerformanceReview } from '../api/performance'

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

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
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
              <Button onClick={() => navigate('/leave/requests/new')}>Request leave</Button>
              <Button onClick={() => navigate('/permission/requests/new')}>Request permission</Button>
              <Button onClick={() => navigate('/business-trips/new')}>New business trip</Button>
              <Button onClick={() => navigate('/letters/request')}>Request HR letter</Button>
              <Button onClick={() => navigate('/personal-info/request')}>Update personal info</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={16}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('leave')}>
            <Statistic
              title="Annual leave remaining"
              value={summary.annualLeaveRemaining ?? 0}
              suffix=" days"
              precision={1}
              valueStyle={{ color: (summary.annualLeaveRemaining ?? 0) > 5 ? '#3f8600' : '#cf1322' }}
            />
            {summary.leaveRequestsPending > 0 && (
              <Tag color="gold" style={{ marginTop: 6 }}>
                {summary.leaveRequestsPending} pending
              </Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('permission')}>
            <Statistic
              title="Permission hours left"
              value={summary.permissionHoursRemaining ?? 0}
              suffix=" h"
              precision={1}
            />
            {summary.permissionRequestsPending > 0 && (
              <Tag color="gold" style={{ marginTop: 6 }}>
                {summary.permissionRequestsPending} pending
              </Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('timesheets')}>
            <Statistic
              title={`Timesheet ${summary.year}-${String(new Date().getMonth() + 1).padStart(2, '0')}`}
              value={summary.currentTimesheetStatus ?? 'not started'}
              valueStyle={{ fontSize: 18 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('payslips')}>
            <Statistic
              title="Latest payslip net"
              value={summary.lastPayslipNet ?? 0}
              precision={2}
              suffix=" AZN"
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
            <Statistic title="Certificates" value={summary.certificatesHeld} />
            {summary.mandatoryCoursesPending > 0 ? (
              <Tag color="red" style={{ marginTop: 6 }}>
                {summary.mandatoryCoursesPending} mandatory pending
              </Tag>
            ) : (
              <Tag color="green" style={{ marginTop: 6 }}>All mandatory done</Tag>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onJump('performance')}>
            <Statistic
              title="Latest review"
              value={summary.activeReviewStatus ?? '—'}
              valueStyle={{ fontSize: 18 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={12}>
          <Card title="Profile" size="small">
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="Employee #">{profile.employeeNo}</Descriptions.Item>
              <Descriptions.Item label="Hire date">{profile.hireDate}</Descriptions.Item>
              <Descriptions.Item label="Email">{profile.email ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Phone">{profile.phone ?? '—'}</Descriptions.Item>
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
function LearningTab() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [certs, setCerts] = useState<Certificate[]>([])
  const [comps, setComps] = useState<EmployeeCompetency[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    Promise.all([selfApi.enrollments(), selfApi.certificates(), selfApi.competencies()])
      .then(([e, c, cm]) => {
        setEnrollments(e)
        setCerts(c)
        setComps(cm)
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

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
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
          message="No employee profile linked to this user"
          description={
            error ??
            "Ask HR to set the 'username' on your employee profile so My Workspace can resolve your record."
          }
        />
      </Card>
    )
  }

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>My Workspace</Typography.Title>}>
      <Tabs
        activeKey={active}
        onChange={setActive}
        items={[
          {
            key: 'dashboard',
            label: 'Dashboard',
            children: <Dashboard profile={profile} summary={summary} onJump={setActive} />,
          },
          { key: 'leave',         label: 'Leave',          children: <LeaveTab /> },
          { key: 'permission',    label: 'Permission',     children: <PermissionTab /> },
          { key: 'businessTrips', label: 'Business trips', children: <BusinessTripsTab /> },
          { key: 'timesheets',    label: 'Timesheets',     children: <TimesheetsTab /> },
          { key: 'payslips',      label: 'Payslips',       children: <PayslipsTab /> },
          { key: 'learning',      label: 'Learning',       children: <LearningTab /> },
          { key: 'performance',   label: 'Performance',    children: <PerformanceTab /> },
        ]}
      />
      {summary.mandatoryCoursesPending > 0 && (
        <Alert
          type="warning"
          style={{ marginTop: 16 }}
          showIcon
          message={`${summary.mandatoryCoursesPending} mandatory course${summary.mandatoryCoursesPending === 1 ? '' : 's'} pending`}
          description="Some compliance training is still required. Open the Learning tab and complete the assigned courses."
        />
      )}
      <Progress style={{ display: 'none' }} />
    </Card>
  )
}
