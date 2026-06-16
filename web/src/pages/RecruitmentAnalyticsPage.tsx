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
  type AgencySpendRow,
  type CostReport,
  type FunnelReport,
  type SourceReport,
  type StaleCandidateRow,
  type StaleReport,
  type TimeToHireReport,
} from '../api/recruitmentAnalytics'
import { recruitmentApi, type SlaBreachReport, type SlaBreachRow } from '../api/recruitment'

export function RecruitmentAnalyticsPage() {
  const { message } = AntdApp.useApp()
  const [from, setFrom] = useState<ReturnType<typeof dayjs>>(dayjs().subtract(1, 'year'))
  const [to, setTo] = useState<ReturnType<typeof dayjs>>(dayjs())
  const [staleDays, setStaleDays] = useState<number>(30)

  const [funnel, setFunnel] = useState<FunnelReport | null>(null)
  const [tth, setTth] = useState<TimeToHireReport | null>(null)
  const [sources, setSources] = useState<SourceReport | null>(null)
  const [stale, setStale] = useState<StaleReport | null>(null)
  // M288 — pipeline SLA breaches (PRD §43)
  const [sla, setSla] = useState<SlaBreachReport | null>(null)
  // M297 — cost-per-hire + recruitment finance (PRD Phase F finale)
  const [cost, setCost] = useState<CostReport | null>(null)
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
      recruitmentApi.slaBreaches(),
      recruitmentAnalyticsApi.cost(f, t),
    ])
      .then(([fn, th, src, st, sl, cs]) => {
        setFunnel(fn)
        setTth(th)
        setSources(src)
        setStale(st)
        setSla(sl)
        setCost(cs)
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

      {/* M288 — pipeline SLA breaches (PRD §43) */}
      {sla && (
        <Card
          title={
            <Space>
              <span>Pipeline SLA</span>
              <Tag color={sla.overdueCount > 0 ? 'red' : 'green'}>
                {sla.overdueCount} overdue
              </Tag>
              {sla.dueSoonCount > 0 && <Tag color="gold">{sla.dueSoonCount} due soon</Tag>}
            </Space>
          }
        >
          <Table<SlaBreachRow>
            rowKey="applicationId"
            size="small"
            dataSource={sla.rows}
            pagination={{ pageSize: 25 }}
            locale={{ emptyText: <Empty description="No applications over SLA" /> }}
            columns={[
              {
                title: 'Application',
                dataIndex: 'applicationNo',
                width: 120,
                render: (no: string, r: SlaBreachRow) => (
                  <Link to={`/recruitment/candidates/${r.candidateId}`}>{no}</Link>
                ),
              },
              { title: 'Candidate', dataIndex: 'candidateName', ellipsis: true },
              { title: 'Position', dataIndex: 'vacancyTitle', ellipsis: true },
              {
                title: 'Stage',
                dataIndex: 'stage',
                width: 150,
                render: (s: string) => s.replace(/_/g, ' '),
              },
              {
                title: 'Owner',
                dataIndex: 'ownerRole',
                width: 150,
                render: (o?: string | null) => (o ? o.replace(/_/g, ' ') : '—'),
              },
              {
                title: 'In stage',
                dataIndex: 'daysInStage',
                width: 110,
                render: (d: number, r: SlaBreachRow) => `${d}d / ${r.slaDays}d SLA`,
              },
              {
                title: 'Status',
                dataIndex: 'severity',
                width: 120,
                render: (s: string, r: SlaBreachRow) =>
                  s === 'OVERDUE' ? (
                    <Tag color="red">{r.daysOver}d over</Tag>
                  ) : (
                    <Tag color="gold">Due today</Tag>
                  ),
              },
            ]}
          />
        </Card>
      )}

      {/* M297 — recruitment cost & finance (PRD Phase F finale) */}
      {cost && (
        <Card title="Recruitment cost & finance">
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <Statistic title="Hires" value={cost.hires} />
              <Typography.Text type="secondary">
                {cost.agencyHires} agency · {cost.referralHires} referral · {cost.directHires} direct
              </Typography.Text>
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="Cost per hire"
                value={cost.costPerHire}
                precision={2}
                valueStyle={{ color: '#722ed1' }}
              />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title="Total committed" value={cost.totalCommitted} precision={2} />
              <Typography.Text type="secondary">paid {cost.totalPaid.toFixed(2)}</Typography.Text>
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="Outstanding"
                value={cost.agencyOutstanding + cost.referralAccrued}
                precision={2}
                valueStyle={
                  cost.agencyOutstanding + cost.referralAccrued > 0 ? { color: '#fa8c16' } : {}
                }
              />
            </Col>
          </Row>
          <Row gutter={[16, 16]} style={{ marginTop: 12 }}>
            <Col xs={24} md={12}>
              <Card size="small" type="inner" title="Agency placement fees">
                <Space size="large">
                  <Statistic title="Total" value={cost.agencyTotal} precision={2} />
                  <Statistic title="Paid" value={cost.agencyPaid} precision={2} />
                  <Statistic title="Outstanding" value={cost.agencyOutstanding} precision={2} />
                </Space>
              </Card>
            </Col>
            <Col xs={24} md={12}>
              <Card size="small" type="inner" title="Referral bonuses">
                <Space size="large">
                  <Statistic title="Total" value={cost.referralTotal} precision={2} />
                  <Statistic title="Paid" value={cost.referralPaid} precision={2} />
                  <Statistic title="Accrued" value={cost.referralAccrued} precision={2} />
                </Space>
              </Card>
            </Col>
          </Row>
          <Table<AgencySpendRow>
            style={{ marginTop: 12 }}
            rowKey="agencyName"
            size="small"
            dataSource={cost.byAgency}
            pagination={false}
            locale={{ emptyText: <Empty description="No agency spend in window" /> }}
            columns={[
              { title: 'Agency', dataIndex: 'agencyName', ellipsis: true },
              { title: 'Placements', dataIndex: 'invoices', width: 110 },
              {
                title: 'Total',
                dataIndex: 'total',
                width: 130,
                render: (v: number) => v.toFixed(2),
              },
              {
                title: 'Paid',
                dataIndex: 'paid',
                width: 130,
                render: (v: number) => v.toFixed(2),
              },
            ]}
          />
        </Card>
      )}
    </Space>
  )
}
