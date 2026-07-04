// HCM_15 M412 — talent analytics (PRD §15.9). HR-only dashboard: 9-box
// distribution, HiPo count/%, retention-risk breakdown, pool coverage.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  Row,
  Select,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'

const { Title } = Typography

interface TalentReviewCycle {
  id: string
  name: string
  year: number
  status: string
}

interface NineBoxCell {
  performanceBox: number
  potentialBox: number
  count: number
}

interface RetentionBreakdown {
  risk: string
  count: number
}

interface PoolCoverage {
  poolCode: string
  poolName: string
  memberCount: number
}

interface AnalyticsReport {
  totalReviews: number
  hipoCount: number
  hipoPercent: number
  nineBoxDistribution: NineBoxCell[]
  retentionRiskBreakdown: RetentionBreakdown[]
  poolCoverage: PoolCoverage[]
}

const RISK_COLOR: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'gold',
  HIGH: 'orange',
  CRITICAL: 'red',
}

export function TalentAnalyticsPage() {
  const { message } = AntdApp.useApp()

  const [cycles, setCycles] = useState<TalentReviewCycle[]>([])
  const [selectedCycle, setSelectedCycle] = useState<string | undefined>()
  const [report, setReport] = useState<AnalyticsReport | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api
      .get<TalentReviewCycle[]>('/talent/reviews/cycles')
      .then((res) => {
        setCycles(res.data)
        if (res.data.length > 0) {
          setSelectedCycle(res.data[0].id)
        }
      })
      .catch(() => message.error('Failed to load cycles'))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedCycle) {
      setReport(null)
      return
    }
    setLoading(true)
    api
      .get<AnalyticsReport>(`/talent/analytics?cycleId=${selectedCycle}`)
      .then((res) => setReport(res.data))
      .catch(() => message.error('Failed to load analytics'))
      .finally(() => setLoading(false))
  }, [selectedCycle]) // eslint-disable-line react-hooks/exhaustive-deps

  const nineBoxColumns: ColumnsType<NineBoxCell> = [
    {
      title: 'Performance',
      dataIndex: 'performanceBox',
      width: 120,
      render: (v) => `${v} (${v === 1 ? 'Low' : v === 2 ? 'Mid' : 'High'})`,
    },
    {
      title: 'Potential',
      dataIndex: 'potentialBox',
      width: 120,
      render: (v) => `${v} (${v === 1 ? 'Low' : v === 2 ? 'Mid' : 'High'})`,
    },
    {
      title: 'Count',
      dataIndex: 'count',
      width: 100,
    },
  ]

  const riskColumns: ColumnsType<RetentionBreakdown> = [
    {
      title: 'Risk Level',
      dataIndex: 'risk',
      render: (v) => <Tag color={RISK_COLOR[v]}>{v}</Tag>,
    },
    {
      title: 'Count',
      dataIndex: 'count',
      width: 100,
    },
  ]

  const poolColumns: ColumnsType<PoolCoverage> = [
    {
      title: 'Code',
      dataIndex: 'poolCode',
      width: 120,
    },
    {
      title: 'Pool Name',
      dataIndex: 'poolName',
    },
    {
      title: 'Members',
      dataIndex: 'memberCount',
      width: 100,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={2}>Talent Analytics</Title>
        <Select
          style={{ width: 300 }}
          placeholder="Select cycle"
          value={selectedCycle}
          onChange={setSelectedCycle}
          options={cycles.map((c) => ({
            value: c.id,
            label: `${c.name} (${c.year})`,
          }))}
        />
      </div>

      {report && (
        <>
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={8}>
              <Card>
                <Statistic title="Total Reviews" value={report.totalReviews} />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic title="HiPo Count" value={report.hipoCount} />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic
                  title="HiPo %"
                  value={report.hipoPercent}
                  precision={1}
                  suffix="%"
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={12}>
              <Card title="9-Box Distribution" loading={loading}>
                <Table
                  dataSource={report.nineBoxDistribution}
                  columns={nineBoxColumns}
                  rowKey={(r) => `${r.performanceBox}-${r.potentialBox}`}
                  pagination={false}
                  size="small"
                />
              </Card>
            </Col>
            <Col span={12}>
              <Card title="Retention Risk Breakdown" loading={loading}>
                <Table
                  dataSource={report.retentionRiskBreakdown}
                  columns={riskColumns}
                  rowKey="risk"
                  pagination={false}
                  size="small"
                />
              </Card>
            </Col>
          </Row>

          <Card title="Talent Pool Coverage" loading={loading}>
            <Table
              dataSource={report.poolCoverage}
              columns={poolColumns}
              rowKey="poolCode"
              pagination={{ pageSize: 20 }}
              size="small"
            />
          </Card>
        </>
      )}
    </div>
  )
}
