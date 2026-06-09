// M109 — Position control dashboard.
// Shows per-position approved/occupied/open-vacancy counts, with over-budget
// and drift flags. HR_ADMIN can trigger an immediate reconciliation pass.
// M156 — Headcount change request workflow tab added.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
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
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  headcountApi,
  hcrApi,
  type HeadcountChangeResponse,
  type HeadcountSummary,
  type HcrStatus,
  type OrgUnitRoll,
  type PositionHeadcountRow,
  type VacancyState,
} from '../api/headcount'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const HCR_STATUS_COLOR: Record<HcrStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
}

const STATE_COLOR: Record<VacancyState, string> = {
  PLANNED: 'default',
  VACANT: 'red',
  PARTIALLY_OCCUPIED: 'gold',
  OCCUPIED: 'green',
  FROZEN: 'blue',
  CANCELLED: 'default',
}

function percent(occupied: number, approved: number): number {
  if (approved <= 0) return 0
  return Math.round((occupied / approved) * 100)
}

export function PositionControlPage() {
  const { message } = AntdApp.useApp()
  // M242 — used by the "+ Create vacancy" shortcut on each gapped row.
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const canReconcile = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const canSubmitHcr = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [data, setData] = useState<HeadcountSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [reconciling, setReconciling] = useState(false)
  const [filterOverBudget, setFilterOverBudget] = useState(false)

  // HCR state
  const [pendingHcrs, setPendingHcrs] = useState<HeadcountChangeResponse[]>([])
  const [hcrLoading, setHcrLoading] = useState(false)
  const [submitModalOpen, setSubmitModalOpen] = useState(false)
  const [submitTargetId, setSubmitTargetId] = useState<string | null>(null)
  const [submitTargetLabel, setSubmitTargetLabel] = useState('')
  const [submitLoading, setSubmitLoading] = useState(false)
  const [hcrForm] = Form.useForm<{ requestedDelta: number; reason?: string }>()

  const load = () => {
    setLoading(true)
    headcountApi
      .summary()
      .then(setData)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  const loadPendingHcrs = () => {
    setHcrLoading(true)
    hcrApi.listPending()
      .then(setPendingHcrs)
      .catch(() => {/* non-fatal */})
      .finally(() => setHcrLoading(false))
  }

  useEffect(() => { load(); loadPendingHcrs() /* eslint-disable-next-line */ }, [])

  const openSubmitModal = (row: PositionHeadcountRow) => {
    setSubmitTargetId(row.positionId)
    setSubmitTargetLabel(`${row.code} — ${row.title}`)
    hcrForm.resetFields()
    setSubmitModalOpen(true)
  }

  const handleSubmitHcr = async () => {
    const values = await hcrForm.validateFields()
    if (!submitTargetId) return
    setSubmitLoading(true)
    try {
      await hcrApi.submit(submitTargetId, values)
      message.success('Headcount change request submitted.')
      setSubmitModalOpen(false)
      loadPendingHcrs()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Submit failed',
      )
    } finally {
      setSubmitLoading(false)
    }
  }

  const runReconcile = async () => {
    setReconciling(true)
    try {
      const res = await headcountApi.reconcile()
      if (res.driftedCount === 0) {
        message.success('No drift — all positions already in sync.')
      } else {
        message.warning(`Fixed drift on ${res.driftedCount} position(s). Drift is logged in the audit.`)
      }
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Reconciliation failed',
      )
    } finally {
      setReconciling(false)
    }
  }

  const rows = useMemo(() => {
    if (!data) return []
    return filterOverBudget ? data.rows.filter((r) => r.overBudget || r.driftDetected) : data.rows
  }, [data, filterOverBudget])

  if (loading) return <Spin />
  if (!data) return <Empty description="No data" />

  const positionCols: ColumnsType<PositionHeadcountRow> = [
    {
      title: 'Position',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.title}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.code}</Text>
        </Space>
      ),
    },
    {
      title: 'Org unit',
      dataIndex: 'orgUnitLabel',
      width: 180,
      render: (v) => v ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Utilization',
      width: 200,
      render: (_, r) => (
        <Tooltip
          title={`${r.actualOccupied} occupied + ${r.openVacancyOpenings} open vacancy = ${r.actualOccupied + r.openVacancyOpenings} committed against ${r.approvedHeadcount} approved`}
        >
          <Progress
            percent={percent(r.actualOccupied + r.openVacancyOpenings, r.approvedHeadcount)}
            size="small"
            status={
              r.overBudget
                ? 'exception'
                : r.actualOccupied >= r.approvedHeadcount
                ? 'success'
                : 'active'
            }
            format={(p) => `${r.actualOccupied}+${r.openVacancyOpenings}/${r.approvedHeadcount} (${p}%)`}
          />
        </Tooltip>
      ),
    },
    {
      title: 'Approved',
      dataIndex: 'approvedHeadcount',
      align: 'right',
      width: 100,
    },
    {
      title: 'Filled',
      dataIndex: 'actualOccupied',
      align: 'right',
      width: 80,
    },
    {
      title: 'Open vac.',
      dataIndex: 'openVacancyOpenings',
      align: 'right',
      width: 100,
    },
    {
      title: 'Remaining',
      dataIndex: 'remainingCapacity',
      align: 'right',
      width: 110,
      render: (v: number) => (
        <Text type={v === 0 ? 'secondary' : v < 0 ? 'danger' : undefined} strong={v > 0}>
          {v}
        </Text>
      ),
    },
    {
      title: 'State',
      width: 130,
      render: (_, r) => (
        <Space size={4}>
          <Tag color={STATE_COLOR[r.vacancyState]}>{r.vacancyState.replace(/_/g, ' ')}</Tag>
          {r.overBudget && <Tag color="red">over budget</Tag>}
          {r.driftDetected && <Tag color="orange">drift</Tag>}
        </Space>
      ),
    },
    // M242 — One-click "Create vacancy" for any position with an
    // unfilled gap. remainingCapacity = approved − actualOccupied −
    // openVacancyOpenings; a positive number is exactly the count of
    // seats we still need to post. Skipped on rows where every seat
    // is either filled OR already has an open vacancy.
    {
      title: '',
      width: 130,
      render: (_: unknown, r: PositionHeadcountRow) => {
        if (r.remainingCapacity <= 0) return null
        return (
          <Tooltip title={`Open a vacancy for ${r.remainingCapacity} seat(s) on this position`}>
            <Button
              size="small"
              type="primary"
              ghost
              onClick={() => navigate(
                `/recruitment/vacancies/new?positionId=${r.positionId}` +
                `&openings=${r.remainingCapacity}`,
              )}
            >
              + Vacancy ({r.remainingCapacity})
            </Button>
          </Tooltip>
        )
      },
    },
    ...(canSubmitHcr
      ? [
          {
            title: '',
            width: 80,
            render: (_: unknown, r: PositionHeadcountRow) => (
              <Button size="small" onClick={() => openSubmitModal(r)}>
                Request
              </Button>
            ),
          } as ColumnsType<PositionHeadcountRow>[number],
        ]
      : []),
  ]

  const pendingHcrCols: ColumnsType<HeadcountChangeResponse> = [
    {
      title: 'Position',
      dataIndex: 'positionId',
      render: (v: string) => {
        const row = data?.rows.find((r) => r.positionId === v)
        return row ? `${row.code} — ${row.title}` : v
      },
    },
    {
      title: 'Delta',
      dataIndex: 'requestedDelta',
      width: 80,
      render: (v: number) => (
        <Text type={v > 0 ? 'success' : 'danger'} strong>
          {v > 0 ? `+${v}` : v}
        </Text>
      ),
    },
    { title: 'Reason', dataIndex: 'reason', render: (v) => v ?? <Text type="secondary">—</Text> },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: HcrStatus) => <Tag color={HCR_STATUS_COLOR[v]}>{v}</Tag>,
    },
    { title: 'Requested by', dataIndex: 'requestedBy', width: 140 },
    {
      title: 'Requested at',
      dataIndex: 'requestedAt',
      width: 160,
      render: (v: string) => v ? new Date(v).toLocaleString() : '—',
    },
  ]

  const orgCols: ColumnsType<OrgUnitRoll> = [
    {
      title: 'Org unit',
      render: (_, r) => r.orgUnitLabel ?? <Text type="secondary">(unassigned)</Text>,
    },
    { title: 'Positions', dataIndex: 'positionCount', align: 'right', width: 100 },
    { title: 'Approved', dataIndex: 'approvedHeadcount', align: 'right', width: 110 },
    { title: 'Filled', dataIndex: 'actualOccupied', align: 'right', width: 100 },
    { title: 'Open vac.', dataIndex: 'openVacancyOpenings', align: 'right', width: 110 },
    {
      title: 'Remaining',
      dataIndex: 'remainingCapacity',
      align: 'right',
      width: 110,
      render: (v: number) => (
        <Text type={v === 0 ? 'secondary' : undefined} strong={v > 0}>{v}</Text>
      ),
    },
    {
      title: 'Utilization',
      width: 220,
      render: (_, r) => (
        <Progress
          percent={percent(r.actualOccupied + r.openVacancyOpenings, r.approvedHeadcount)}
          size="small"
        />
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Position control</Title>
        {canReconcile && (
          <Popconfirm
            title="Reconcile position headcount?"
            description="Recomputes Position.occupiedHeadcount from the employee table. Idempotent — safe to run any time. Every drift fix is audited."
            onConfirm={runReconcile}
            okText="Reconcile"
          >
            <Button loading={reconciling}>Reconcile now…</Button>
          </Popconfirm>
        )}
      </Space>

      <Text type="secondary">
        Live view of approved vs filled headcount, plus outstanding open vacancies.
        Every direct hire, rehire, position swap, and recruitment hire is gated against the approved cap;
        a nightly reconciliation job catches any drift between the stored counter and the ground-truth employee table.
      </Text>

      <Row gutter={16}>
        <Col span={6}>
          <Card><Statistic title="Positions" value={data.positionCount} /></Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Approved seats"
              value={data.totalApproved}
              suffix={
                <Text type="secondary" style={{ fontSize: 13 }}>
                  / {data.totalActualOccupied + data.totalOpenVacancies} committed
                </Text>
              }
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Open vacancies"
              value={data.totalOpenVacancies}
              valueStyle={{ color: data.totalOpenVacancies > 0 ? '#1677ff' : undefined }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Over budget"
              value={data.positionsOverBudget}
              valueStyle={{ color: data.positionsOverBudget > 0 ? '#ff4d4f' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      {data.positionsWithDrift > 0 && (
        <Alert
          showIcon
          type="warning"
          message={`${data.positionsWithDrift} position(s) show drift between stored and ground-truth headcount.`}
          description="This usually clears on the next nightly reconciliation pass. HR admins can run it now from the button above."
        />
      )}

      <Tabs
        items={[
          {
            key: 'by-position',
            label: 'By position',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <Button
                    size="small"
                    type={filterOverBudget ? 'primary' : 'default'}
                    onClick={() => setFilterOverBudget((v) => !v)}
                  >
                    {filterOverBudget ? 'Show all' : 'Show problems only'}
                  </Button>
                  <Text type="secondary">{rows.length} row{rows.length === 1 ? '' : 's'}</Text>
                </Space>
                <Card>
                  <Table
                    rowKey="positionId"
                    columns={positionCols}
                    dataSource={rows}
                    size="small"
                    pagination={{ pageSize: 25 }}
                    rowClassName={(r) => r.overBudget ? 'row-over-budget' : ''}
                  />
                </Card>
              </Space>
            ),
          },
          {
            key: 'by-org',
            label: 'By org unit',
            children: (
              <Card>
                <Table
                  rowKey={(r) => r.orgUnitId ?? '__none__'}
                  columns={orgCols}
                  dataSource={data.byOrgUnit}
                  size="small"
                  pagination={false}
                />
              </Card>
            ),
          },
          {
            key: 'change-requests',
            label: (
              <Badge count={pendingHcrs.length} size="small" offset={[6, -2]}>
                Change requests
              </Badge>
            ),
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
                  <Button size="small" onClick={loadPendingHcrs}>Refresh</Button>
                </Space>
                <Card>
                  <Table
                    rowKey="id"
                    columns={pendingHcrCols}
                    dataSource={pendingHcrs}
                    loading={hcrLoading}
                    size="small"
                    pagination={{ pageSize: 20 }}
                    locale={{ emptyText: 'No pending headcount change requests.' }}
                  />
                </Card>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={`Request headcount change — ${submitTargetLabel}`}
        open={submitModalOpen}
        onCancel={() => setSubmitModalOpen(false)}
        onOk={handleSubmitHcr}
        confirmLoading={submitLoading}
        okText="Submit request"
        destroyOnClose
      >
        <Form form={hcrForm} layout="vertical">
          <Form.Item
            name="requestedDelta"
            label="Delta (positive = increase, negative = decrease)"
            rules={[
              { required: true, message: 'Delta is required' },
              { type: 'integer', message: 'Must be a whole number' },
              { validator: (_, v) => v !== 0 ? Promise.resolve() : Promise.reject('Delta must not be zero') },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={-500} max={500} />
          </Form.Item>
          <Form.Item name="reason" label="Reason / justification">
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="Business justification (optional)" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
