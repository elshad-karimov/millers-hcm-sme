// M109 — Position control dashboard.
// Shows per-position approved/occupied/open-vacancy counts, with over-budget
// and drift flags. HR_ADMIN can trigger an immediate reconciliation pass.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
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
import {
  headcountApi,
  type HeadcountSummary,
  type OrgUnitRoll,
  type PositionHeadcountRow,
  type VacancyState,
} from '../api/headcount'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

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
  const { hasRole } = useAuth()
  const canReconcile = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [data, setData] = useState<HeadcountSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [reconciling, setReconciling] = useState(false)
  const [filterOverBudget, setFilterOverBudget] = useState(false)

  const load = () => {
    setLoading(true)
    headcountApi
      .summary()
      .then(setData)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

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
        ]}
      />
    </Space>
  )
}
