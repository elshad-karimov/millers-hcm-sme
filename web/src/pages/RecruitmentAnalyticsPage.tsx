// M88 — Recruitment analytics dashboard.
// Header date-range, four cards (funnel, time-to-hire, sources, stale).

import { useEffect, useState } from 'react'
import {
  Card,
  Col,
  DatePicker,
  Empty,
  InputNumber,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  recruitmentAnalyticsApi,
  type FunnelReport,
  type SourceReport,
  type StaleCandidateRow,
  type StaleReport,
  type TimeToHireReport,
} from '../api/recruitmentAnalytics'

export function RecruitmentAnalyticsPage() {
  const { message } = AntdApp.useApp()
  const [from, setFrom] = useState<ReturnType<typeof dayjs>>(dayjs().subtract(1, 'year'))
  const [to, setTo] = useState<ReturnType<typeof dayjs>>(dayjs())
  const [staleDays, setStaleDays] = useState<number>(30)

  const [funnel, setFunnel] = useState<FunnelReport | null>(null)
  const [tth, setTth] = useState<TimeToHireReport | null>(null)
  const [sources, setSources] = useState<SourceReport | null>(null)
  const [stale, setStale] = useState<StaleReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    const f = from.format('YYYY-MM-DD')
    const t = to.format('YYYY-MM-DD')
    Promise.all([
      recruitmentAnalyticsApi.funnel(f, t),
      recruitmentAnalyticsApi.timeToHire(f, t),
      recruitmentAnalyticsApi.sources(f, t),
      recruitmentAnalyticsApi.stale(staleDays),
    ])
      .then(([fn, th, src, st]) => {
        setFunnel(fn)
        setTth(th)
        setSources(src)
        setStale(st)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load analytics'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to, staleDays])

  if (loading || !funnel || !tth || !sources || !stale) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const funnelChartData = funnel.rows.map((r) => ({
    stage: r.stage.replace(/_/g, ' '),
    applications: r.applications,
  }))

  const tthCols: ColumnsType<TimeToHireReport['stageTransitions'][number]> = [
    { title: 'From', dataIndex: 'fromStage', render: (v: string) => <Tag>{v.replace(/_/g, ' ')}</Tag> },
    { title: 'To', dataIndex: 'toStage', render: (v: string) => <Tag color="blue">{v.replace(/_/g, ' ')}</Tag> },
    { title: 'Transitions', dataIndex: 'transitions' },
    {
      title: 'Avg days',
      dataIndex: 'avgDays',
      render: (v?: number | null) => (v != null ? v : '—'),
    },
    {
      title: 'Median days',
      dataIndex: 'medianDays',
      render: (v?: number | null) => (v != null ? v : '—'),
    },
  ]

  const sourceCols: ColumnsType<SourceReport['rows'][number]> = [
    { title: 'Source', dataIndex: 'source', render: (v) => <Tag>{v}</Tag> },
    { title: 'Applications', dataIndex: 'applications' },
    { title: 'Hires', dataIndex: 'hires' },
    {
      title: 'Hire rate',
      dataIndex: 'hireRatePct',
      render: (v: number) => `${v}%`,
    },
    {
      title: 'Avg time-to-hire',
      dataIndex: 'avgTimeToHireDays',
      render: (v?: number | null) => (v != null ? `${v} days` : '—'),
    },
  ]

  const staleCols: ColumnsType<StaleCandidateRow> = [
    {
      title: 'Candidate',
      render: (_, r) => (
        <Link to="/recruitment/talent-pool">{r.candidateNo} — {r.fullName}</Link>
      ),
    },
    { title: 'Email', dataIndex: 'email', render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'poolStatus',
      render: (v: string) => <Tag>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Days since contact',
      dataIndex: 'daysSinceContact',
      sorter: (a, b) => a.daysSinceContact - b.daysSinceContact,
      defaultSortOrder: 'descend',
      render: (v: number) => (
        <Tag color={v >= 180 ? 'red' : v >= 90 ? 'orange' : 'gold'}>{v} d</Tag>
      ),
    },
    {
      title: 'Last contacted',
      dataIndex: 'lastContactedAt',
      render: (v?: string | null) => (v ? new Date(v).toLocaleDateString() : 'never'),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Recruitment analytics
      </Typography.Title>

      <Card size="small">
        <Space wrap>
          <Typography.Text type="secondary">Window:</Typography.Text>
          <DatePicker
            value={from}
            onChange={(d) => d && setFrom(d)}
            disabled={loading}
          />
          <Typography.Text type="secondary">to</Typography.Text>
          <DatePicker
            value={to}
            onChange={(d) => d && setTo(d)}
            disabled={loading}
          />
          <span style={{ marginLeft: 24 }} />
          <Typography.Text type="secondary">Stale threshold (days):</Typography.Text>
          <InputNumber
            min={1}
            max={365}
            value={staleDays}
            onChange={(v) => v && setStaleDays(v)}
          />
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title={`Funnel (${funnel.totalCreated} created in window)`}>
            <Row gutter={8} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Statistic title="Hired" value={funnel.hired}
                  valueStyle={funnel.hired > 0 ? { color: '#52c41a' } : {}} />
              </Col>
              <Col span={8}>
                <Statistic title="Rejected" value={funnel.rejected}
                  valueStyle={funnel.rejected > 0 ? { color: '#fa8c16' } : {}} />
              </Col>
              <Col span={8}>
                <Statistic title="Withdrawn" value={funnel.withdrawn} />
              </Col>
            </Row>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={funnelChartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="stage" tick={{ fontSize: 11 }} angle={-15}
                  textAnchor="end" height={70} />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="applications" fill="#1677ff" />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Time to hire">
            <Row gutter={8} style={{ marginBottom: 16 }}>
              <Col span={12}>
                <Statistic
                  title="Avg created → hired"
                  value={tth.avgDaysCreatedToHired ?? '—'}
                  suffix={tth.avgDaysCreatedToHired != null ? 'days' : undefined}
                />
              </Col>
              <Col span={12}>
                <Statistic
                  title="Median"
                  value={tth.medianDaysCreatedToHired ?? '—'}
                  suffix={tth.medianDaysCreatedToHired != null ? 'days' : undefined}
                />
              </Col>
            </Row>
            <Table
              rowKey={(r) => `${r.fromStage}-${r.toStage}`}
              columns={tthCols}
              dataSource={tth.stageTransitions}
              pagination={false}
              size="small"
              locale={{ emptyText: <Empty description="No completed transitions in window" /> }}
            />
          </Card>
        </Col>
      </Row>

      <Card title={`Source effectiveness (${sources.rows.length} sources)`}>
        <Table
          rowKey="source"
          columns={sourceCols}
          dataSource={sources.rows}
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description="No applications in window" /> }}
        />
      </Card>

      <Card
        title={
          <Space>
            <span>Stale outreach</span>
            <Tag color={stale.total > 0 ? 'orange' : 'green'}>
              {stale.total} candidate{stale.total === 1 ? '' : 's'}
            </Tag>
          </Space>
        }
        extra={
          <Typography.Text type="secondary">
            Threshold: {stale.thresholdDays} days
          </Typography.Text>
        }
      >
        <Table
          rowKey="candidateId"
          columns={staleCols}
          dataSource={stale.rows}
          pagination={{ pageSize: 25 }}
          size="small"
          locale={{ emptyText: <Empty description="No stale candidates" /> }}
        />
      </Card>
    </Space>
  )
}
