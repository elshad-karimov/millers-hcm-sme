// M475 — Executive summary analytics (HR_ADMIN/EXECUTIVE).

import { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic, Table, Tag, Typography, Spin, App as AntdApp } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { analyticsApi, type ExecutiveSummary, type ComplianceDeadlineItem } from '../../api/analytics'

export function ExecutiveSummaryPage() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<ExecutiveSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    analyticsApi
      .executiveSummary()
      .then(setData)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading) return <Spin />
  if (!data) return <Card><Typography.Text type="danger">No data</Typography.Text></Card>

  const complianceColumns: ColumnsType<ComplianceDeadlineItem> = [
    { title: 'Title', dataIndex: 'title' },
    { title: 'Due Date', dataIndex: 'dueDate', width: 120 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'PENDING' ? 'orange' : 'green'}>{s}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="Executive Summary">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="Headcount"
                value={data.headcountTrend.current}
                suffix={
                  data.headcountTrend.trend === 'up' ? (
                    <ArrowUpOutlined style={{ color: 'green' }} />
                  ) : data.headcountTrend.trend === 'down' ? (
                    <ArrowDownOutlined style={{ color: 'red' }} />
                  ) : null
                }
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Prev: {data.headcountTrend.previousMonth}
              </Typography.Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic title="Turnover (12M)" value={data.turnover12m} suffix="%" precision={1} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="Payroll Cost"
                value={data.payrollCostTrend.currentMonth}
                suffix="AZN"
                precision={2}
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Prev: {data.payrollCostTrend.previousMonth} AZN
              </Typography.Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic title="eNPS" value={data.enps} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic title="Attrition High Risk" value={data.attritionHighRiskCount} valueStyle={{ color: 'red' }} />
            </Card>
          </Col>
        </Row>
      </Card>

      <Card title="Upcoming Compliance Deadlines">
        <Table
          rowKey="title"
          columns={complianceColumns}
          dataSource={data.upcomingComplianceDeadlines}
          pagination={false}
        />
      </Card>
    </Space>
  )
}

// Missing import fix
import { Space } from 'antd'
