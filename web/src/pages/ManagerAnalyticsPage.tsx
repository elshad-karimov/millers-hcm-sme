import { useEffect, useState } from 'react'
import { Card, Col, Row, Space, Spin, Statistic, Typography, App as AntdApp } from 'antd'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { api } from '../api/client'

interface AnalyticsData {
  teamHeadcount: number
  turnover12Mo: number
  absenceRateThisMonth: number
  overtimeHours3Mo: number
  trainingCompletionRate: number
  openSkillGaps: number
}

export function ManagerAnalyticsPage() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<AnalyticsData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<AnalyticsData>('/manager/analytics')
      .then((r) => setData(r.data))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load analytics'),
      )
      .finally(() => setLoading(false))
  }, [message])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  if (!data) return null

  const chartData = [
    { name: 'Turnover %', value: data.turnover12Mo },
    { name: 'Absence %', value: data.absenceRateThisMonth },
    { name: 'Training %', value: data.trainingCompletionRate },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3}>Team Analytics</Typography.Title>

      <Row gutter={16}>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic title="Team headcount" value={data.teamHeadcount} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic
              title="12-month turnover"
              value={data.turnover12Mo}
              suffix="%"
              valueStyle={data.turnover12Mo > 15 ? { color: '#f5222d' } : {}}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic
              title="Absence rate (this month)"
              value={data.absenceRateThisMonth}
              suffix="%"
              valueStyle={data.absenceRateThisMonth > 5 ? { color: '#fa8c16' } : {}}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic
              title="Overtime hours (3 mo)"
              value={data.overtimeHours3Mo}
              valueStyle={data.overtimeHours3Mo > 100 ? { color: '#fa8c16' } : {}}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic
              title="Training completion"
              value={data.trainingCompletionRate}
              suffix="%"
              valueStyle={data.trainingCompletionRate < 70 ? { color: '#fa8c16' } : { color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card>
            <Statistic
              title="Open skill gaps"
              value={data.openSkillGaps}
              valueStyle={data.openSkillGaps > 0 ? { color: '#1677ff' } : {}}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Percentage Metrics">
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Bar dataKey="value" fill="#1677ff" name="%" />
          </BarChart>
        </ResponsiveContainer>
      </Card>
    </Space>
  )
}
