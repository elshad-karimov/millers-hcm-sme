// M94 — Succession bench depth report.
// Per-manager readiness counts (Ready Now / Ready Soon / Long-term / Dev),
// sorted deepest bench first. HR-only (HR_READ on the backend).

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
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { successionApi, type BenchReport, type BenchRow } from '../../api/succession'
import { performanceApi, type ReviewCycle } from '../../api/performance'

const { Title, Text } = Typography

export function BenchDepthPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { cycleId: routeCycleId } = useParams<{ cycleId?: string }>()
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>(routeCycleId)
  const [report, setReport] = useState<BenchReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    performanceApi
      .cycles()
      .then((cs) => {
        setCycles(cs)
        if (!cycleId && cs.length) setCycleId(cs[0].id)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cycles'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!cycleId) return
    setLoading(true)
    successionApi
      .bench(cycleId)
      .then(setReport)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load bench'))
      .finally(() => setLoading(false))
  }, [cycleId, message])

  // Roll-up totals across all managers so the header summary mirrors the grid.
  const totals = report
    ? report.rows.reduce(
        (acc, r) => ({
          readyNow: acc.readyNow + r.readyNow,
          readySoon: acc.readySoon + r.readySoon,
          readyLongTerm: acc.readyLongTerm + r.readyLongTerm,
          underDevelopment: acc.underDevelopment + r.underDevelopment,
        }),
        { readyNow: 0, readySoon: 0, readyLongTerm: 0, underDevelopment: 0 },
      )
    : null

  const cols: ColumnsType<BenchRow> = [
    {
      title: 'Manager',
      dataIndex: 'managerName',
      fixed: 'left',
      width: 220,
      render: (v: string, r) => (
        <Link to={`/employees/${r.managerId}`}>{v}</Link>
      ),
    },
    {
      title: 'Reports',
      dataIndex: 'totalReports',
      width: 100,
      align: 'center',
      sorter: (a, b) => a.totalReports - b.totalReports,
    },
    {
      title: 'Placed',
      dataIndex: 'placedReports',
      width: 100,
      align: 'center',
      render: (v: number, r) => (
        <Tag color={v === r.totalReports ? 'green' : 'orange'}>
          {v} / {r.totalReports}
        </Tag>
      ),
    },
    {
      title: 'Ready Now',
      dataIndex: 'readyNow',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readyNow - b.readyNow,
      defaultSortOrder: 'descend',
      render: (v: number) =>
        v > 0 ? <Tag color="green">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Ready Soon',
      dataIndex: 'readySoon',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readySoon - b.readySoon,
      render: (v: number) =>
        v > 0 ? <Tag color="blue">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Long-term',
      dataIndex: 'readyLongTerm',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readyLongTerm - b.readyLongTerm,
      render: (v: number) =>
        v > 0 ? <Tag color="gold">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Development',
      dataIndex: 'underDevelopment',
      width: 130,
      align: 'center',
      sorter: (a, b) => a.underDevelopment - b.underDevelopment,
      render: (v: number) =>
        v > 0 ? <Tag color="orange">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Link to={`/performance/succession/${cycleId}`} state={{ managerId: r.managerId }}>
          Open grid
        </Link>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Bench depth</Title>
        <Space>
          <Text type="secondary">Cycle:</Text>
          <Select
            style={{ minWidth: 280 }}
            value={cycleId}
            onChange={(v) => {
              setCycleId(v)
              navigate(`/performance/succession/${v}/bench`, { replace: true })
            }}
            options={cycles.map((c) => ({ value: c.id, label: c.name }))}
            placeholder="Pick a cycle"
          />
        </Space>
      </Space>

      {loading || !report || !totals ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
          <Spin />
        </div>
      ) : (
        <>
          <Card size="small">
            <Row gutter={16}>
              <Col span={6}>
                <Statistic title="Managers" value={report.totalManagers} />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Ready Now"
                  value={totals.readyNow}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Ready Soon"
                  value={totals.readySoon}
                  valueStyle={{ color: '#1677ff' }}
                />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Long-term"
                  value={totals.readyLongTerm}
                  valueStyle={{ color: '#d48806' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="Under development"
                  value={totals.underDevelopment}
                  valueStyle={
                    totals.underDevelopment > 0 ? { color: '#fa8c16' } : {}
                  }
                />
              </Col>
            </Row>
          </Card>

          <Card title={`Per-manager bench (${report.cycleName})`}>
            <Table
              rowKey="managerId"
              columns={cols}
              dataSource={report.rows}
              pagination={{ pageSize: 25 }}
              size="small"
              scroll={{ x: 1000 }}
            />
          </Card>
        </>
      )}
    </Space>
  )
}
