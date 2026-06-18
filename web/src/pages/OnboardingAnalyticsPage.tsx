// M312 — Onboarding analytics report (Phase E.3).
// Completion rates, avg days to complete, dept heatmap, template breakdown,
// and task-type bottleneck analysis for a configurable date window.

import { useEffect, useState } from 'react'
import {
  Card,
  Col,
  DatePicker,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { BarChartOutlined, CheckCircleOutlined, ClockCircleOutlined, TeamOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { onboardingApi, type OnboardingAnalyticsReport } from '../api/onboarding'
import { prettyEnum, TASK_TYPE_COLOR } from '../api/checklists'

const { RangePicker } = DatePicker
const { Text } = Typography

const DATE_FMT = 'YYYY-MM-DD'
const DEFAULT_DAYS = 90

export function OnboardingAnalyticsPage() {
  const defaultFrom = dayjs().subtract(DEFAULT_DAYS, 'day')
  const defaultTo = dayjs()

  const [range, setRange] = useState<[Dayjs, Dayjs]>([defaultFrom, defaultTo])
  const [data, setData] = useState<OnboardingAnalyticsReport | null>(null)
  const [loading, setLoading] = useState(false)

  async function load([from, to]: [Dayjs, Dayjs]) {
    setLoading(true)
    try {
      const report = await onboardingApi.analytics(from.format(DATE_FMT), to.format(DATE_FMT))
      setData(report)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(range)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function handleRangeChange(vals: [Dayjs | null, Dayjs | null] | null) {
    if (!vals || !vals[0] || !vals[1]) return
    const next: [Dayjs, Dayjs] = [vals[0], vals[1]]
    setRange(next)
    load(next)
  }

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <BarChartOutlined style={{ fontSize: 20, color: '#1677ff' }} />
        <Typography.Title level={4} style={{ margin: 0 }}>Onboarding Analytics</Typography.Title>
        <RangePicker
          value={range}
          onChange={handleRangeChange as any}
          format={DATE_FMT}
          allowClear={false}
          style={{ marginLeft: 16 }}
        />
      </Space>

      <Spin spinning={loading}>
        {data && (
          <Space direction="vertical" size={24} style={{ width: '100%' }}>
            {/* Summary stat cards */}
            <Row gutter={16}>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Started"
                    value={data.totalStarted}
                    prefix={<TeamOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Completed"
                    value={data.totalCompleted}
                    prefix={<CheckCircleOutlined />}
                    valueStyle={{ color: data.totalCompleted > 0 ? '#52c41a' : undefined }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Completion rate"
                    value={data.completionRatePct}
                    suffix="%"
                    valueStyle={{ color: data.completionRatePct >= 80 ? '#52c41a' : data.completionRatePct >= 50 ? '#faad14' : '#ff4d4f' }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} md={6}>
                <Card>
                  <Statistic
                    title="Avg days to complete"
                    value={data.avgDaysToComplete}
                    prefix={<ClockCircleOutlined />}
                    precision={1}
                    suffix="d"
                    valueStyle={{ color: '#1677ff' }}
                  />
                </Card>
              </Col>
            </Row>

            {/* By department */}
            <Card title="By Department">
              <Table
                dataSource={data.byDepartment}
                rowKey="department"
                size="small"
                pagination={false}
                columns={[
                  { title: 'Department', dataIndex: 'department', key: 'dept' },
                  { title: 'Started', dataIndex: 'started', key: 'started', width: 90, align: 'center' },
                  { title: 'Completed', dataIndex: 'completed', key: 'completed', width: 100, align: 'center' },
                  {
                    title: 'Completion rate',
                    key: 'rate',
                    width: 200,
                    render: (_, r) => (
                      <Space>
                        <Progress
                          percent={r.completionRatePct}
                          size="small"
                          strokeColor={r.completionRatePct >= 80 ? '#52c41a' : r.completionRatePct >= 50 ? '#faad14' : '#ff4d4f'}
                          style={{ width: 120 }}
                          showInfo={false}
                        />
                        <Text>{r.completionRatePct}%</Text>
                      </Space>
                    ),
                  },
                  {
                    title: 'Avg days',
                    dataIndex: 'avgDaysToComplete',
                    key: 'avg',
                    width: 100,
                    align: 'center',
                    render: (v: number) => v > 0 ? `${v.toFixed(1)} d` : '—',
                  },
                ]}
              />
            </Card>

            {/* By template */}
            <Card title="By Template">
              <Table
                dataSource={data.byTemplate}
                rowKey="templateName"
                size="small"
                pagination={false}
                columns={[
                  { title: 'Template', dataIndex: 'templateName', key: 'tpl' },
                  { title: 'Started', dataIndex: 'started', key: 'started', width: 90, align: 'center' },
                  { title: 'Completed', dataIndex: 'completed', key: 'completed', width: 100, align: 'center' },
                  {
                    title: 'Completion rate',
                    key: 'rate',
                    width: 200,
                    render: (_, r) => (
                      <Space>
                        <Progress
                          percent={r.completionRatePct}
                          size="small"
                          strokeColor={r.completionRatePct >= 80 ? '#52c41a' : r.completionRatePct >= 50 ? '#faad14' : '#ff4d4f'}
                          style={{ width: 120 }}
                          showInfo={false}
                        />
                        <Text>{r.completionRatePct}%</Text>
                      </Space>
                    ),
                  },
                  {
                    title: 'Avg days',
                    dataIndex: 'avgDaysToComplete',
                    key: 'avg',
                    width: 100,
                    align: 'center',
                    render: (v: number) => v > 0 ? `${v.toFixed(1)} d` : '—',
                  },
                ]}
              />
            </Card>

            {/* Task-type bottlenecks — lowest completion rate first */}
            <Card title="Task-Type Bottlenecks" extra={<Text type="secondary">Sorted: lowest completion first</Text>}>
              <Table
                dataSource={data.taskBottlenecks}
                rowKey="taskType"
                size="small"
                pagination={false}
                columns={[
                  {
                    title: 'Task type',
                    dataIndex: 'taskType',
                    key: 'type',
                    render: (v: string) => (
                      <Tag color={TASK_TYPE_COLOR[v] ?? 'default'}>{prettyEnum(v)}</Tag>
                    ),
                  },
                  { title: 'Total', dataIndex: 'total', key: 'total', width: 80, align: 'center' },
                  { title: 'Done', dataIndex: 'done', key: 'done', width: 80, align: 'center' },
                  {
                    title: 'Completion rate',
                    key: 'rate',
                    width: 200,
                    render: (_, r) => (
                      <Space>
                        <Progress
                          percent={r.completionRatePct}
                          size="small"
                          strokeColor={r.completionRatePct >= 80 ? '#52c41a' : r.completionRatePct >= 50 ? '#faad14' : '#ff4d4f'}
                          style={{ width: 120 }}
                          showInfo={false}
                        />
                        <Text>{r.completionRatePct}%</Text>
                      </Space>
                    ),
                  },
                ]}
              />
            </Card>
          </Space>
        )}

        {!loading && !data && (
          <Card><Text type="secondary">Select a date range to load analytics.</Text></Card>
        )}
      </Spin>
    </div>
  )
}
