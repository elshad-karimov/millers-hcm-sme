import { useEffect, useState } from 'react'
import { Button, Card, Col, DatePicker, Row, Space, Statistic, Table, Typography, message } from 'antd'
import { BarChartOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell, ResponsiveContainer } from 'recharts'
import { api } from '../api/client'

const { Title } = Typography
const { RangePicker } = DatePicker

interface CountByLabel { label: string; count: number }
interface MonthlyCount { month: string; count: number }
interface OffboardingReport {
  totalCases: number
  completedCases: number
  avgNoticeDays: number
  rehireEligiblePct: number
  totalGrossPaid: number
  totalNetPaid: number
  bySource: CountByLabel[]
  byExitReason: CountByLabel[]
  monthlyTrend: MonthlyCount[]
}

const SOURCE_COLORS: Record<string, string> = {
  RESIGNATION: '#1677ff',
  TERMINATION: '#ff4d4f',
  OTHER: '#faad14',
}

async function fetchReport(from: string, to: string): Promise<OffboardingReport> {
  const res = await api.get('/lifecycle/offboarding/analytics', { params: { from, to } })
  return res.data
}

export function OffboardingAnalyticsPage() {
  const defaultRange: [dayjs.Dayjs, dayjs.Dayjs] = [
    dayjs().subtract(11, 'month').startOf('month'),
    dayjs().endOf('month'),
  ]
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>(defaultRange)
  const [report, setReport] = useState<OffboardingReport | null>(null)
  const [loading, setLoading] = useState(false)

  const load = async (r: [dayjs.Dayjs, dayjs.Dayjs]) => {
    setLoading(true)
    try {
      const data = await fetchReport(r[0].format('YYYY-MM-DD'), r[1].format('YYYY-MM-DD'))
      setReport(data)
    } catch {
      message.error('Failed to load analytics')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load(defaultRange) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} align="center">
        <BarChartOutlined style={{ fontSize: 22 }} />
        <Title level={3} style={{ margin: 0 }}>Offboarding Analytics</Title>
        <RangePicker
          value={range}
          onChange={v => { if (v && v[0] && v[1]) setRange([v[0], v[1]]) }}
          picker="month"
          allowClear={false}
        />
        <Button type="primary" loading={loading} onClick={() => load(range)}>Refresh</Button>
      </Space>

      {report && (
        <>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={4}><Card><Statistic title="Total Cases" value={report.totalCases} /></Card></Col>
            <Col span={4}><Card><Statistic title="Completed" value={report.completedCases} /></Card></Col>
            <Col span={4}><Card><Statistic title="Completion Rate" value={report.totalCases ? Math.round(report.completedCases * 100 / report.totalCases) : 0} suffix="%" /></Card></Col>
            <Col span={4}><Card><Statistic title="Avg Notice Days" value={report.avgNoticeDays.toFixed(1)} /></Card></Col>
            <Col span={4}><Card><Statistic title="Rehire Eligible" value={report.rehireEligiblePct.toFixed(0)} suffix="%" /></Card></Col>
            <Col span={4}><Card><Statistic title="Net Paid (AZN)" value={report.totalNetPaid?.toFixed(2) ?? '—'} /></Card></Col>
          </Row>

          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={8}>
              <Card title="Cases by Source" size="small">
                <ResponsiveContainer width="100%" height={200}>
                  <BarChart data={report.bySource} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={110} />
                    <Tooltip />
                    <Bar dataKey="count" name="Cases">
                      {report.bySource.map((d) => (
                        <Cell key={d.label} fill={SOURCE_COLORS[d.label] ?? '#8884d8'} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </Card>
            </Col>
            <Col span={8}>
              <Card title="Top Exit Reasons" size="small">
                {report.byExitReason.length === 0
                  ? <div style={{ color: '#888', textAlign: 'center', padding: 40 }}>No data</div>
                  : (
                    <Table
                      dataSource={report.byExitReason}
                      rowKey="label"
                      size="small"
                      pagination={false}
                      columns={[
                        { title: 'Reason', dataIndex: 'label', ellipsis: true },
                        { title: 'Count', dataIndex: 'count', width: 60 },
                      ]}
                    />
                  )}
              </Card>
            </Col>
            <Col span={8}>
              <Card title="Settlement Summary (Paid)" size="small">
                <Row gutter={8}>
                  <Col span={12}><Statistic title="Total Gross" value={report.totalGrossPaid?.toFixed(2) ?? '0.00'} suffix="AZN" /></Col>
                  <Col span={12}><Statistic title="Net Payable" value={report.totalNetPaid?.toFixed(2) ?? '0.00'} suffix="AZN" /></Col>
                </Row>
              </Card>
            </Col>
          </Row>

          <Card title="Monthly Offboarding Trend" size="small">
            {report.monthlyTrend.length === 0
              ? <div style={{ color: '#888', textAlign: 'center', padding: 40 }}>No data in selected period</div>
              : (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={report.monthlyTrend}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="month" />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="count" name="Cases" fill="#1677ff" />
                  </BarChart>
                </ResponsiveContainer>
              )}
          </Card>
        </>
      )}
    </div>
  )
}
