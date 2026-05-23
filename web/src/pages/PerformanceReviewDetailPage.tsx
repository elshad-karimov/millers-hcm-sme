import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  performanceApi,
  type CalibrationRequest,
  type Goal,
  type GoalStatus,
  type PerformanceReview,
  type ReviewStatus,
} from '../api/performance'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'
import { useAuth } from '../auth/AuthContext'

const LIST_PATH = '/performance/reviews'

const STATUS_COLOR: Record<ReviewStatus, string> = {
  DRAFT: 'default',
  SELF_IN_PROGRESS: 'orange',
  SELF_SUBMITTED: 'gold',
  MANAGER_IN_PROGRESS: 'orange',
  MANAGER_SUBMITTED: 'geekblue',
  PENDING_APPROVAL: 'cyan',
  CALIBRATING: 'purple',
  APPROVED: 'green',
  COMPLETED: 'blue',
  REJECTED: 'red',
  CANCELLED: 'default',
}

const GOAL_STATUS_COLOR: Record<GoalStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  ON_TRACK: 'green',
  AT_RISK: 'orange',
  BLOCKED: 'red',
  ACHIEVED: 'cyan',
  MISSED: 'volcano',
  CANCELLED: 'default',
}

const BAND_OPTIONS = [
  'Outstanding',
  'Exceeds Expectations',
  'Meets Expectations',
  'Below Expectations',
  'Needs Improvement',
]

const RECOMMENDATIONS = [
  'PROMOTION',
  'MERIT_INCREASE',
  'BONUS_TIER_A',
  'BONUS_TIER_B',
  'BONUS_TIER_C',
  'PIP',
  'NONE',
]

export function PerformanceReviewDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canManager = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'DEPARTMENT_MANAGER')
  const canCalibrate = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [r, setR] = useState<PerformanceReview | null>(null)
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [manager, setManager] = useState<Employee | null>(null)
  const [goals, setGoals] = useState<Goal[]>([])
  const [loading, setLoading] = useState(true)

  const [selfOpen, setSelfOpen] = useState(false)
  const [managerOpen, setManagerOpen] = useState(false)
  const [calibrateOpen, setCalibrateOpen] = useState(false)
  const [selfForm] = Form.useForm<{ rating: number; comments?: string }>()
  const [mgrForm] = Form.useForm<{ rating: number; comments?: string }>()
  const [calForm] = Form.useForm<CalibrationRequest>()

  const load = async () => {
    if (!id) return
    setLoading(true)
    try {
      const data = await performanceApi.review(id)
      setR(data)
      const [emp, mgr, gs] = await Promise.all([
        employeesApi.get(data.employeeId).catch(() => null),
        data.managerId ? employeesApi.get(data.managerId).catch(() => null) : Promise.resolve(null),
        performanceApi.goals(data.cycleId, data.employeeId).catch(() => []),
      ])
      setEmployee(emp as Employee | null)
      setManager(mgr as Employee | null)
      setGoals(gs)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (loading || !r) {
    return (
      <FormPageShell title="Performance review" backTo={LIST_PATH}>
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      </FormPageShell>
    )
  }

  const submitSelf = async (v: { rating: number; comments?: string }) => {
    try {
      await performanceApi.submitSelf(r.id, v)
      message.success('Self review submitted')
      setSelfOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const submitManager = async (v: { rating: number; comments?: string }) => {
    try {
      await performanceApi.submitManager(r.id, v)
      message.success('Manager review submitted')
      setManagerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const submitCalibration = async (v: CalibrationRequest) => {
    try {
      await performanceApi.calibrate(r.id, v)
      message.success('Calibration saved')
      setCalibrateOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const submitForApproval = () => {
    modal.confirm({
      title: 'Submit for approval?',
      content: 'Starts the PERFORMANCE_REVIEW_APPROVAL workflow (Manager → HR → Executive).',
      onOk: async () => {
        try {
          await performanceApi.submitForApproval(r.id)
          message.success('Submitted for approval')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Submission failed',
          )
        }
      },
    })
  }

  const closeReview = () => {
    modal.confirm({
      title: 'Close review?',
      content: 'Marks the review COMPLETED. Final rating must be set first.',
      onOk: async () => {
        try {
          await performanceApi.closeReview(r.id)
          message.success('Review closed')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Close failed',
          )
        }
      },
    })
  }

  return (
    <FormPageShell
      title={`${r.reviewNo} — ${employee ? `${employee.firstName} ${employee.lastName}` : 'Review'}`}
      backTo={LIST_PATH}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 1000 }}>
        <Card
          extra={
            <Tag color={STATUS_COLOR[r.status]} style={{ fontSize: 13 }}>
              {r.status.replace(/_/g, ' ')}
            </Tag>
          }
        >
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Employee">
              {employee ? `${employee.employeeNo} — ${employee.firstName} ${employee.lastName}` : r.employeeId}
            </Descriptions.Item>
            <Descriptions.Item label="Manager">
              {manager ? `${manager.employeeNo} — ${manager.firstName} ${manager.lastName}` : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Self rating">
              {r.selfRating != null ? r.selfRating : '—'}
              {r.selfSubmittedAt && ` (submitted ${r.selfSubmittedAt.slice(0, 10)})`}
            </Descriptions.Item>
            <Descriptions.Item label="Manager rating">
              {r.managerRating != null ? r.managerRating : '—'}
              {r.managerSubmittedAt && ` (submitted ${r.managerSubmittedAt.slice(0, 10)})`}
            </Descriptions.Item>
            <Descriptions.Item label="Goal score (weighted)">
              {r.goalScore != null ? r.goalScore : '— rate the goals first'}
            </Descriptions.Item>
            <Descriptions.Item label="Final rating">
              {r.finalRating != null ? (
                <Typography.Text strong>
                  {r.finalRating}
                  {r.finalBand ? ` — ${r.finalBand}` : ''}
                </Typography.Text>
              ) : (
                '—'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Recommendation">
              {r.recommendation ?? '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Bonus %">
              {r.bonusPercent != null ? `${r.bonusPercent}%` : '—'}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Card title="Goals (this cycle)" size="small">
          {goals.length === 0 ? (
            <Typography.Text type="secondary">
              No goals yet. Add goals from the Goals page; the goal score will roll up automatically
              once each goal is rated.
            </Typography.Text>
          ) : (
            <Space direction="vertical" style={{ width: '100%' }}>
              {goals.map((g) => (
                <Row key={g.id} gutter={16} align="middle">
                  <Col span={1}>
                    <Typography.Text type="secondary">{g.goalNo}</Typography.Text>
                  </Col>
                  <Col span={10}>
                    <Typography.Text>{g.title}</Typography.Text>{' '}
                    <Tag color={GOAL_STATUS_COLOR[g.status]}>{g.status.replace(/_/g, ' ')}</Tag>
                  </Col>
                  <Col span={4}>
                    weight {g.weightPercent}%
                  </Col>
                  <Col span={5}>
                    <Progress percent={Number(g.progressPercent)} size="small" />
                  </Col>
                  <Col span={4}>
                    rating: <Typography.Text strong>{g.rating ?? '—'}</Typography.Text>
                  </Col>
                </Row>
              ))}
            </Space>
          )}
        </Card>

        <Card title="Actions">
          <Space wrap>
            {(r.status === 'SELF_IN_PROGRESS' || r.status === 'DRAFT') && (
              <Button type="primary" onClick={() => {
                selfForm.setFieldsValue({ rating: r.selfRating ?? 3 })
                setSelfOpen(true)
              }}>
                Submit self review
              </Button>
            )}
            {r.status === 'SELF_SUBMITTED' && canManager && (
              <Button type="primary" onClick={() => {
                mgrForm.setFieldsValue({ rating: r.managerRating ?? r.selfRating ?? 3 })
                setManagerOpen(true)
              }}>
                Submit manager review
              </Button>
            )}
            {r.status === 'MANAGER_SUBMITTED' && canManager && (
              <Button type="primary" onClick={submitForApproval}>
                Submit for approval
              </Button>
            )}
            {(r.status === 'PENDING_APPROVAL' || r.status === 'APPROVED' || r.status === 'CALIBRATING') && canCalibrate && (
              <Button onClick={() => {
                calForm.setFieldsValue({
                  finalRating: r.finalRating ?? r.managerRating ?? undefined,
                  finalBand: r.finalBand ?? undefined,
                  recommendation: r.recommendation ?? undefined,
                  bonusPercent: r.bonusPercent ?? undefined,
                  calibrationNotes: r.calibrationNotes ?? undefined,
                })
                setCalibrateOpen(true)
              }}>
                Calibrate
              </Button>
            )}
            {(r.status === 'APPROVED' || r.status === 'CALIBRATING') && r.finalRating != null && canCalibrate && (
              <Button type="primary" onClick={closeReview}>
                Close review
              </Button>
            )}
            {r.status === 'PENDING_APPROVAL' && (
              <Alert
                type="info"
                showIcon
                message="Awaiting workflow approvals"
                description="Approvers will action this in the Approvals inbox."
              />
            )}
          </Space>
        </Card>

        {r.selfComments && (
          <Card title="Self comments" size="small">
            <Typography.Paragraph>{r.selfComments}</Typography.Paragraph>
          </Card>
        )}
        {r.managerComments && (
          <Card title="Manager comments" size="small">
            <Typography.Paragraph>{r.managerComments}</Typography.Paragraph>
          </Card>
        )}
        {r.calibrationNotes && (
          <Card title="Calibration notes" size="small">
            <Typography.Paragraph>{r.calibrationNotes}</Typography.Paragraph>
          </Card>
        )}

        <Space>
          <Button onClick={() => navigate(LIST_PATH)}>Back to list</Button>
        </Space>
      </Space>

      <Modal open={selfOpen} title="Self review" onCancel={() => setSelfOpen(false)} onOk={() => selfForm.submit()} okText="Submit">
        <Form form={selfForm} layout="vertical" onFinish={submitSelf}>
          <Form.Item name="rating" label="Self-assessment rating (0–5)" rules={[{ required: true }]}>
            <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="comments" label="What went well / what's next">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal open={managerOpen} title="Manager review" onCancel={() => setManagerOpen(false)} onOk={() => mgrForm.submit()} okText="Submit">
        <Form form={mgrForm} layout="vertical" onFinish={submitManager}>
          <Form.Item name="rating" label="Manager rating (0–5)" rules={[{ required: true }]}>
            <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="comments" label="Manager comments">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal open={calibrateOpen} title="Calibration" onCancel={() => setCalibrateOpen(false)} onOk={() => calForm.submit()} okText="Save calibration">
        <Form form={calForm} layout="vertical" onFinish={submitCalibration}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="finalRating" label="Final rating (0–5)">
                <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="finalBand" label="Band">
                <select
                  style={{ width: '100%', padding: 4, borderRadius: 4, borderColor: 'rgba(0,0,0,0.15)' }}
                  onChange={(e) => calForm.setFieldsValue({ finalBand: e.target.value })}
                  defaultValue=""
                >
                  <option value="">—</option>
                  {BAND_OPTIONS.map((b) => (
                    <option key={b} value={b}>
                      {b}
                    </option>
                  ))}
                </select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="recommendation" label="Recommendation">
                <select
                  style={{ width: '100%', padding: 4, borderRadius: 4, borderColor: 'rgba(0,0,0,0.15)' }}
                  onChange={(e) => calForm.setFieldsValue({ recommendation: e.target.value })}
                  defaultValue=""
                >
                  <option value="">—</option>
                  {RECOMMENDATIONS.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="bonusPercent" label="Bonus % (of base salary)">
                <InputNumber min={0} max={100} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="calibrationNotes" label="Calibration notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </FormPageShell>
  )
}
