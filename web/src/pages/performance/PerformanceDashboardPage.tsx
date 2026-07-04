// HCM_12 M401 — HR performance dashboard (PRD §27.1): review-status funnel,
// pending acknowledgements, rating distribution, high/low performers, live
// PIP / dev-plan / appeal counts.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { api } from '../../api/client'
import { performanceApi, type ReviewCycle } from '../../api/performance'

const { Title, Text } = Typography

interface Performer {
  reviewId: string
  employeeId: string
  employeeName?: string | null
  finalRating: number
  finalBand?: string | null
}

interface Dashboard {
  cycleId: string
  cycleName: string
  totalReviews: number
  byStatus: Record<string, number>
  pendingAcknowledgement: number
  disputed: number
  ratingDistribution: Record<string, number>
  highPerformers: Performer[]
  lowPerformers: Performer[]
  activePips: number
  activeDevPlans: number
  openAppeals: number
  goalPlansPendingApproval: number
}

export function PerformanceDashboardPage() {
  const { message } = AntdApp.useApp()
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [data, setData] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    performanceApi.cycles().then((c) => {
      setCycles(c)
      if (c.length) setCycleId((c.find((x) => x.status === 'OPEN') ?? c[0]).id)
    })
  }, [])

  useEffect(() => {
    if (!cycleId) return
    setLoading(true)
    api
      .get<Dashboard>('/performance/dashboard', { params: { cycleId } })
      .then((r) => setData(r.data))
      .catch(() => message.error('Failed to load dashboard'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId])

  const performerColumns = [
    {
      title: 'Employee',
      key: 'name',
      render: (_: unknown, p: Performer) => p.employeeName ?? p.employeeId,
    },
    {
      title: 'Rating',
      dataIndex: 'finalRating',
      width: 90,
      align: 'right' as const,
      render: (v: number) => Number(v).toFixed(2),
    },
    { title: 'Band', dataIndex: 'finalBand', width: 170, render: (v: string) => v ?? '—' },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Performance dashboard
          </Title>
          <Text type="secondary">HR view (§27.1) — one cycle at a glance.</Text>
        </Col>
        <Col>
          <Select
            style={{ minWidth: 260 }}
            value={cycleId}
            onChange={setCycleId}
            options={cycles.map((c) => ({ value: c.id, label: c.name }))}
          />
        </Col>
      </Row>

      {loading || !data ? (
        <Spin style={{ display: 'block', margin: '80px auto' }} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={16}>
            {[
              { title: 'Reviews', value: data.totalReviews },
              { title: 'Pending acknowledgement', value: data.pendingAcknowledgement },
              { title: 'Disputed', value: data.disputed },
              { title: 'Goal plans awaiting approval', value: data.goalPlansPendingApproval },
              { title: 'Open appeals', value: data.openAppeals },
              { title: 'Active PIPs', value: data.activePips },
              { title: 'Active dev plans', value: data.activeDevPlans },
            ].map((s) => (
              <Col key={s.title} flex="1 1 160px">
                <Card size="small">
                  <Statistic title={s.title} value={s.value} />
                </Card>
              </Col>
            ))}
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Card size="small" title="Review status funnel">
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  {Object.entries(data.byStatus).map(([s, n]) => (
                    <Space key={s} style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Tag>{s.replace(/_/g, ' ')}</Tag>
                      <Text strong>{n}</Text>
                    </Space>
                  ))}
                </Space>
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small" title="Rating distribution">
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  {Object.entries(data.ratingDistribution).map(([band, n]) => (
                    <Space key={band} style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text>{band}</Text>
                      <Text strong>{n}</Text>
                    </Space>
                  ))}
                </Space>
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small" title="High performers (top 5)">
                <Table
                  rowKey="reviewId"
                  size="small"
                  columns={performerColumns}
                  dataSource={data.highPerformers}
                  pagination={false}
                  locale={{ emptyText: 'No rated reviews yet.' }}
                />
              </Card>
              <Card size="small" title="Needs support (bottom 5)" style={{ marginTop: 12 }}>
                <Table
                  rowKey="reviewId"
                  size="small"
                  columns={performerColumns}
                  dataSource={data.lowPerformers}
                  pagination={false}
                  locale={{ emptyText: 'No rated reviews yet.' }}
                />
              </Card>
            </Col>
          </Row>
        </Space>
      )}
    </div>
  )
}
