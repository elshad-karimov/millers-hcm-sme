import { useEffect, useState } from 'react'
import {
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import {
  WarningOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTip,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import {
  compensationApi,
  type CompensationDashboardDto,
  type BudgetUtilizationRow,
  type OutOfBandReportRow,
} from '../api/compensation'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const COMPA_RATIO_COLORS: Record<string, string> = {
  BELOW_MIN: '#ff4d4f',
  LOW: '#faad14',
  MID: '#52c41a',
  HIGH: '#1890ff',
  ABOVE_MAX: '#f5222d',
}

const COMPA_RATIO_LABELS: Record<string, string> = {
  BELOW_MIN: 'Below Min',
  LOW: 'Low',
  MID: 'Mid',
  HIGH: 'High',
  ABOVE_MAX: 'Above Max',
}

export function CompensationDashboardPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canRead = hasRole(...RoleSets.COMPENSATION_READ)

  const [dashboard, setDashboard] = useState<CompensationDashboardDto | null>(null)
  const [outOfBand, setOutOfBand] = useState<OutOfBandReportRow[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    if (!canRead) return
    setLoading(true)
    Promise.all([
      compensationApi.getDashboard(),
      compensationApi.outOfBandReport(),
    ])
      .then(([dashData, oobData]) => {
        setDashboard(dashData)
        setOutOfBand(oobData)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load dashboard'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!canRead) {
    return (
      <Card>
        <Empty description="You do not have permission to view compensation data" />
      </Card>
    )
  }

  // Transform compaRatioDistribution for Recharts
  const compaRatioChartData = dashboard
    ? Object.entries(dashboard.compaRatioDistribution).map(([key, value]) => ({
        name: COMPA_RATIO_LABELS[key] || key,
        count: value,
        key,
      }))
    : []

  const outOfBandColumns: ColumnsType<OutOfBandReportRow> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      render: (name: string, rec) => `${rec.employeeNo} - ${name}`,
    },
    {
      title: 'Department',
      dataIndex: 'department',
    },
    {
      title: 'Grade',
      dataIndex: 'gradeCode',
    },
    {
      title: 'Band',
      dataIndex: 'bandCode',
    },
    {
      title: 'Band Min',
      dataIndex: 'minSalary',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Band Max',
      dataIndex: 'maxSalary',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Actual Salary',
      dataIndex: 'actualSalary',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Position',
      dataIndex: 'position',
      render: (pos: string) => {
        const color = pos === 'BELOW_MIN' || pos === 'ABOVE_MAX' ? 'red' : 'orange'
        return <Tag color={color}>{COMPA_RATIO_LABELS[pos] || pos}</Tag>
      },
    },
    {
      title: 'Delta',
      render: (_, rec) => {
        const delta = rec.position === 'BELOW_MIN' ? rec.deltaFromMin : rec.deltaFromMax
        const sign = delta > 0 ? '+' : ''
        return (
          <Text type={delta < 0 ? 'danger' : undefined}>
            {sign}
            {delta.toLocaleString()}
          </Text>
        )
      },
      align: 'right',
    },
  ]

  const budgetColumns: ColumnsType<BudgetUtilizationRow> = [
    {
      title: 'Budget Type',
      dataIndex: 'budgetType',
      render: (type: string) => <Tag color="blue">{type.replace('_', ' ')}</Tag>,
    },
    {
      title: 'Scope',
      dataIndex: 'scopeType',
      render: (scope: string, rec) => {
        const ref = rec.scopeRef || 'Global'
        return `${scope} - ${ref}`
      },
    },
    {
      title: 'Budget',
      dataIndex: 'amount',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Consumed',
      dataIndex: 'consumed',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Remaining',
      dataIndex: 'remaining',
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Utilization',
      dataIndex: 'utilizationPct',
      align: 'right',
      render: (pct: number) => {
        const status = pct >= 90 ? 'exception' : pct >= 70 ? 'normal' : 'success'
        return (
          <div style={{ width: 120 }}>
            <Progress percent={parseFloat(pct.toFixed(1))} size="small" status={status} />
          </div>
        )
      },
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Compensation Dashboard</Title>

      {/* Stat Cards */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="Employees with Comp Record"
              value={dashboard?.totalEmployeesWithCompRecord ?? 0}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Without Grade"
              value={dashboard?.employeesWithoutGrade ?? 0}
              valueStyle={{ color: dashboard && dashboard.employeesWithoutGrade > 0 ? '#faad14' : undefined }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Without Band"
              value={dashboard?.employeesWithoutBand ?? 0}
              valueStyle={{ color: dashboard && dashboard.employeesWithoutBand > 0 ? '#faad14' : undefined }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Out of Band"
              value={dashboard?.employeesOutOfBand ?? 0}
              valueStyle={{ color: dashboard && dashboard.employeesOutOfBand > 0 ? '#ff4d4f' : undefined }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="Pending Salary Approvals"
              value={dashboard?.pendingSalaryChangeApprovals ?? 0}
              valueStyle={{ color: dashboard && dashboard.pendingSalaryChangeApprovals > 0 ? '#1890ff' : undefined }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Open Exceptions"
              value={dashboard?.openExceptions ?? 0}
              valueStyle={{ color: dashboard && dashboard.openExceptions > 0 ? '#ff4d4f' : undefined }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* Compa-Ratio Distribution Chart */}
      <Card title="Compa-Ratio Distribution" style={{ marginBottom: 24 }} loading={loading}>
        {compaRatioChartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={compaRatioChartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <RechartsTip />
              <Bar dataKey="count">
                {compaRatioChartData.map((entry) => (
                  <Cell key={entry.key} fill={COMPA_RATIO_COLORS[entry.key] || '#8884d8'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <Empty description="No compa-ratio data" />
        )}
      </Card>

      {/* Budget Utilization */}
      <Card title="Budget Utilization" style={{ marginBottom: 24 }}>
        <Table
          dataSource={dashboard?.budgetUtilization ?? []}
          columns={budgetColumns}
          rowKey={(rec) => `${rec.budgetType}-${rec.scopeType}-${rec.scopeRef}`}
          loading={loading}
          pagination={false}
        />
      </Card>

      {/* Out-of-Band Employees */}
      <Card title="Out-of-Band Employees">
        <Table
          dataSource={outOfBand}
          columns={outOfBandColumns}
          rowKey={(rec) => rec.employeeId}
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </div>
  )
}
