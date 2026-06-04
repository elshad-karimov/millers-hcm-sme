// M102 — Salary planning / comp-ratio page.
// HR Admin only. Shows flight-risk employees, grade-band health, and
// a full comp-ratio table with colour-coded risk levels.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTip, ResponsiveContainer, Cell } from 'recharts'
import { Link } from 'react-router-dom'
import {
  compRatioApi,
  type CompRatioReport,
  type CompRiskLevel,
  type EmployeeCompRatioRow,
  type GradeBandRow,
} from '../api/compRatio'

const { Title, Text } = Typography

const RISK_COLOR: Record<CompRiskLevel, string> = {
  BELOW_RANGE:  '#ff4d4f',
  LOW_IN_RANGE: '#fa8c16',
  AT_MIDPOINT:  '#52c41a',
  HIGH_IN_RANGE:'#1677ff',
  ABOVE_RANGE:  '#722ed1',
  NO_BAND:      '#d9d9d9',
}

const RISK_LABEL: Record<CompRiskLevel, string> = {
  BELOW_RANGE:  'Below range',
  LOW_IN_RANGE: 'Low in range',
  AT_MIDPOINT:  'At midpoint',
  HIGH_IN_RANGE:'High in range',
  ABOVE_RANGE:  'Above range',
  NO_BAND:      'No band',
}

function fmt(n?: number | null, decimals = 0) {
  if (n == null) return '—'
  return n.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}

function RatioBar({ ratio }: { ratio?: number | null }) {
  if (ratio == null) return <Text type="secondary">—</Text>
  const pct = Math.min(Math.max(ratio, 0), 160) // cap for display
  const color = ratio < 80 ? '#ff4d4f' : ratio <= 110 ? '#52c41a' : '#722ed1'
  return (
    <Tooltip title={`${ratio.toFixed(1)}%`}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{ flex: 1, height: 8, background: '#f0f0f0', borderRadius: 4, overflow: 'hidden' }}>
          <div style={{ width: `${(pct / 160) * 100}%`, height: '100%', background: color, borderRadius: 4 }} />
        </div>
        <Text style={{ fontSize: 12, minWidth: 40 }}>{ratio.toFixed(1)}%</Text>
      </div>
    </Tooltip>
  )
}

export function SalaryPlanningPage() {
  const { message } = AntdApp.useApp()
  const [report, setReport] = useState<CompRatioReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  useEffect(() => {
    compRatioApi
      .report()
      .then(setReport)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load report'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading || !report) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}><Spin /></div>
  }

  const filteredEmployees = report.employees.filter((e) =>
    !search ||
    e.fullName.toLowerCase().includes(search.toLowerCase()) ||
    (e.employeeNo ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (e.department ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (e.gradeCode ?? '').toLowerCase().includes(search.toLowerCase()),
  )

  // Chart data: distribution by risk level.
  const riskDistData = (
    ['BELOW_RANGE', 'LOW_IN_RANGE', 'AT_MIDPOINT', 'HIGH_IN_RANGE', 'ABOVE_RANGE'] as CompRiskLevel[]
  ).map((level) => ({
    level: RISK_LABEL[level],
    count: report.employees.filter((e) => e.riskLevel === level).length,
    color: RISK_COLOR[level],
  }))

  const empCols: ColumnsType<EmployeeCompRatioRow> = [
    {
      title: 'Employee',
      fixed: 'left',
      width: 220,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Link to={`/employees/${r.employeeId}`}>{r.fullName}</Link>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
        </Space>
      ),
    },
    { title: 'Department', dataIndex: 'department', width: 160, render: (v) => v ?? '—' },
    {
      title: 'Grade',
      width: 130,
      render: (_, r) =>
        r.gradeCode ? <Tag>{r.gradeCode}</Tag> : <Text type="secondary">—</Text>,
    },
    {
      title: 'Actual salary',
      dataIndex: 'actualSalary',
      width: 140,
      align: 'right',
      sorter: (a, b) => (a.actualSalary ?? 0) - (b.actualSalary ?? 0),
      render: (v) => fmt(v, 2),
    },
    {
      title: 'Midpoint',
      dataIndex: 'midpointSalary',
      width: 130,
      align: 'right',
      render: (v) => fmt(v, 2),
    },
    {
      title: 'Comp ratio',
      dataIndex: 'compRatio',
      width: 180,
      sorter: (a, b) => (a.compRatio ?? 0) - (b.compRatio ?? 0),
      render: (v) => <RatioBar ratio={v} />,
    },
    {
      title: 'vs midpoint',
      dataIndex: 'salaryVsMidpoint',
      width: 120,
      align: 'right',
      sorter: (a, b) => (a.salaryVsMidpoint ?? 0) - (b.salaryVsMidpoint ?? 0),
      render: (v?: number | null) =>
        v == null ? '—' : (
          <Text style={{ color: v < 0 ? '#ff4d4f' : v > 0 ? '#52c41a' : undefined }}>
            {v >= 0 ? '+' : ''}{fmt(v, 0)}
          </Text>
        ),
    },
    {
      title: 'Risk',
      dataIndex: 'riskLevel',
      width: 130,
      filters: Object.entries(RISK_LABEL).map(([v, t]) => ({ text: t, value: v })),
      onFilter: (val, r) => r.riskLevel === val,
      render: (level: CompRiskLevel) => (
        <Tag color={RISK_COLOR[level]}>{RISK_LABEL[level]}</Tag>
      ),
    },
  ]

  const bandCols: ColumnsType<GradeBandRow> = [
    { title: 'Grade', render: (_, r) => <Tag>{r.gradeCode}</Tag>, width: 100 },
    { title: 'Name', dataIndex: 'gradeName', width: 160 },
    { title: 'Employees', dataIndex: 'employeeCount', width: 110, align: 'center' },
    { title: 'Min', dataIndex: 'minSalary', align: 'right', render: (v) => fmt(v, 0) },
    { title: 'Midpoint', dataIndex: 'midpointSalary', align: 'right', render: (v) => fmt(v, 0) },
    { title: 'Max', dataIndex: 'maxSalary', align: 'right', render: (v) => fmt(v, 0) },
    { title: 'Avg actual', dataIndex: 'avgActualSalary', align: 'right', render: (v) => fmt(v, 0) },
    { title: 'Avg ratio', dataIndex: 'avgCompRatio', align: 'right',
      render: (v) => v ? `${v.toFixed(1)}%` : '—' },
    { title: '↓ Risk', dataIndex: 'belowRange', align: 'center',
      render: (v: number) => v > 0 ? <Tag color="#ff4d4f">{v}</Tag> : <Text type="secondary">0</Text> },
    { title: 'Mid', dataIndex: 'atMidpoint', align: 'center',
      render: (v: number) => <Tag color="#52c41a">{v}</Tag> },
    { title: '↑ High', dataIndex: 'aboveRange', align: 'center',
      render: (v: number) => v > 0 ? <Tag color="#722ed1">{v}</Tag> : <Text type="secondary">0</Text> },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Salary planning — comp ratio</Title>

      {/* KPI summary */}
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card size="small"><Statistic title="Active employees" value={report.totalEmployees} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Flight risk (< 80%)"
              value={report.flightRiskCount}
              valueStyle={report.flightRiskCount > 0 ? { color: '#ff4d4f' } : {}}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Overall avg comp ratio"
              value={report.overallAvgCompRatio != null ? `${report.overallAvgCompRatio.toFixed(1)}%` : '—'}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="No grade / no band"
              value={report.noGradeCount + report.noBandCount}
              valueStyle={(report.noGradeCount + report.noBandCount) > 0 ? { color: '#fa8c16' } : {}}
            />
          </Card>
        </Col>
      </Row>

      {/* Risk distribution chart */}
      <Card title="Comp-ratio distribution by risk level">
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={riskDistData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="level" tick={{ fontSize: 12 }} />
            <YAxis allowDecimals={false} />
            <RechartsTip />
            <Bar dataKey="count" name="Employees">
              {riskDistData.map((d, i) => <Cell key={i} fill={d.color} />)}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </Card>

      {/* Grade-band rollup */}
      <Card title={`Grade bands (${report.gradeBands.length})`}>
        <Table
          rowKey="gradeCode"
          columns={bandCols}
          dataSource={report.gradeBands}
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description="No grades with salary bands configured" /> }}
        />
      </Card>

      {/* Per-employee table */}
      <Card
        title={`Employee comp ratios (${filteredEmployees.length} of ${report.totalEmployees})`}
        extra={
          <Input.Search
            placeholder="Filter by name, grade, dept…"
            style={{ width: 260 }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            allowClear
          />
        }
      >
        <Table
          rowKey="employeeId"
          columns={empCols}
          dataSource={filteredEmployees}
          pagination={{ pageSize: 50, showSizeChanger: true }}
          size="small"
          scroll={{ x: 1100 }}
          locale={{ emptyText: <Empty description="No employees match" /> }}
          rowClassName={(r) =>
            r.riskLevel === 'BELOW_RANGE' ? 'ant-table-row-danger' : ''
          }
        />
      </Card>
    </Space>
  )
}
