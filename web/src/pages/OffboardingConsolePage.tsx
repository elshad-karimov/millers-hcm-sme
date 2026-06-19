import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography, message
} from 'antd'
import { LogoutOutlined, UserDeleteOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import {
  getOffboardingOverview, listOffboardingCases, updateOffboardingCaseStatus,
  type OffboardingCaseResponse, type OffboardingCaseStatus, type OffboardingOverviewResponse,
  type PageResponse,
} from '../api/offboarding'

const { Title } = Typography

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  IN_PROGRESS: 'blue',
  UNDER_NOTICE: 'geekblue',
  CLEARANCE_PENDING: 'orange',
  SETTLEMENT_PENDING: 'purple',
  COMPLETED: 'green',
  CANCELLED: 'default',
  REVERSED: 'red',
}

const SOURCE_COLOR: Record<string, string> = {
  RESIGNATION: 'blue',
  TERMINATION: 'red',
  OTHER: 'default',
}

const TRANSITIONS: Record<OffboardingCaseStatus, OffboardingCaseStatus[]> = {
  DRAFT: ['SUBMITTED'],
  SUBMITTED: ['PENDING_APPROVAL', 'CANCELLED'],
  PENDING_APPROVAL: ['APPROVED', 'CANCELLED'],
  APPROVED: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['UNDER_NOTICE', 'CLEARANCE_PENDING', 'CANCELLED'],
  UNDER_NOTICE: ['CLEARANCE_PENDING', 'CANCELLED'],
  CLEARANCE_PENDING: ['SETTLEMENT_PENDING', 'CANCELLED'],
  SETTLEMENT_PENDING: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
  REVERSED: [],
}

export function OffboardingConsolePage() {
  const [overview, setOverview] = useState<OffboardingOverviewResponse | null>(null)
  const [cases, setCases] = useState<PageResponse<OffboardingCaseResponse> | null>(null)
  const [loading, setLoading] = useState(false)
  const [advancing, setAdvancing] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<OffboardingCaseStatus | undefined>()
  const [tick, setTick] = useState(0)

  const refresh = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    getOffboardingOverview().then(setOverview).catch(() => null)
  }, [tick])

  useEffect(() => {
    setLoading(true)
    listOffboardingCases({ status: statusFilter, page, size: 20 })
      .then(setCases)
      .catch(() => message.error('Failed to load cases'))
      .finally(() => setLoading(false))
  }, [tick, page, statusFilter])

  const advance = async (id: string, status: OffboardingCaseStatus) => {
    setAdvancing(id + status)
    try {
      await updateOffboardingCaseStatus(id, status)
      message.success('Status updated')
      refresh()
    } catch {
      message.error('Failed to update status')
    } finally {
      setAdvancing(null)
    }
  }

  const columns = [
    {
      title: 'Case',
      dataIndex: 'caseNo',
      render: (v: string, r: OffboardingCaseResponse) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontWeight: 600 }}>{v}</span>
          <Tag color={SOURCE_COLOR[r.source]}>{r.source}</Tag>
        </Space>
      ),
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{id.slice(0, 8)}…</span>,
    },
    {
      title: 'Status',
      dataIndex: 'caseStatus',
      render: (v: OffboardingCaseStatus) => <Tag color={STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Last Working Date',
      dataIndex: 'lastWorkingDate',
      render: (v?: string) => v ?? '—',
    },
    {
      title: 'Exit Reason',
      dataIndex: 'exitReason',
      render: (v?: string) => v ?? '—',
    },
    {
      title: 'Actions',
      render: (_: unknown, r: OffboardingCaseResponse) => {
        const next = TRANSITIONS[r.caseStatus] ?? []
        return (
          <Space>
            {next.map(s => (
              <Button
                key={s}
                size="small"
                danger={s === 'CANCELLED'}
                loading={advancing === r.id + s}
                onClick={() => advance(r.id, s)}
              >
                {s.replace(/_/g, ' ')}
              </Button>
            ))}
          </Space>
        )
      },
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} align="center">
        <LogoutOutlined style={{ fontSize: 22 }} />
        <Title level={3} style={{ margin: 0 }}>Offboarding Console</Title>
        <Link to="/lifecycle/offboarding/resignations/new">
          <Button type="primary" icon={<UserDeleteOutlined />}>New Resignation</Button>
        </Link>
      </Space>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card><Statistic title="Active Cases" value={overview?.activeCases ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Leaving This Week" value={overview?.leavingThisWeek ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Leaving This Month" value={overview?.leavingThisMonth ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Total Cases" value={overview?.totalCases ?? 0} /></Card>
        </Col>
      </Row>

      <Card
        title="Offboarding Cases"
        extra={
          <Select
            allowClear
            placeholder="Filter by status"
            style={{ width: 200 }}
            value={statusFilter}
            onChange={v => { setStatusFilter(v); setPage(0) }}
            options={Object.keys(TRANSITIONS).map(s => ({
              label: s.replace(/_/g, ' '),
              value: s,
            }))}
          />
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={cases?.content ?? []}
          columns={columns}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: cases?.totalElements ?? 0,
            onChange: p => setPage(p - 1),
          }}
        />
      </Card>
    </div>
  )
}
