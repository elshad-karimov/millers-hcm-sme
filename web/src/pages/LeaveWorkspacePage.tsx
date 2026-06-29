import { useEffect, useState } from 'react'
import { Card, Col, InputNumber, Row, Statistic, Table, Typography } from 'antd'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi } from '../api/leave'

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const PIE_COLORS = ['#1890ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16']

interface WorkspaceStats {
  pendingApprovals: number
  approvedThisMonth: number
  rejectedThisMonth: number
  cancelledThisMonth: number
  totalDaysTakenThisYear: number
  byType: { typeCode: string; typeName: string; requestCount: number; totalDays: number }[]
  monthlyTrend: { year: number; month: number; approved: number; totalDays: number }[]
  absenceHotspots: { employeeNo: string; employeeName: string; absenceDays: number }[]
}

export function LeaveWorkspacePage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [stats, setStats] = useState<WorkspaceStats | null>(null)
  const [loading, setLoading] = useState(false)

  const load = async (y: number) => {
    setLoading(true)
    try {
      const data = await leaveApi.workspaceStats(y)
      setStats(data as WorkspaceStats)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load(year) }, [year])

  const hotspotCols: ColumnsType<WorkspaceStats['absenceHotspots'][number]> = [
    { title: 'Emp No', dataIndex: 'employeeNo', width: 100 },
    { title: 'Name', dataIndex: 'employeeName' },
    { title: 'Days', dataIndex: 'absenceDays', width: 80, align: 'right' },
  ]

  const trendData = (stats?.monthlyTrend ?? []).map((t) => ({
    month: MONTH_NAMES[t.month - 1],
    Days: Number(t.totalDays.toFixed(1)),
    Requests: t.approved,
  }))

  const pieData = (stats?.byType ?? []).map((t) => ({
    name: t.typeCode,
    value: Number(t.totalDays),
  }))

  return (
    <div style={{ padding: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Typography.Title level={4} style={{ margin: 0 }}>Leave Workspace</Typography.Title>
        </Col>
        <Col>
          <InputNumber
            min={2020}
            max={2100}
            value={year}
            onChange={(v) => v && setYear(v)}
            style={{ width: 100 }}
          />
        </Col>
      </Row>

      {/* KPI tiles */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={5}>
          <Card loading={loading}>
            <Statistic title="Pending approvals" value={stats?.pendingApprovals ?? 0}
              valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card loading={loading}>
            <Statistic title="Approved this month" value={stats?.approvedThisMonth ?? 0}
              valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card loading={loading}>
            <Statistic title="Rejected this month" value={stats?.rejectedThisMonth ?? 0}
              valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card loading={loading}>
            <Statistic title="Cancelled this month" value={stats?.cancelledThisMonth ?? 0} />
          </Card>
        </Col>
        <Col span={4}>
          <Card loading={loading}>
            <Statistic title="Total days taken (YTD)" value={Number(stats?.totalDaysTakenThisYear ?? 0).toFixed(1)} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        {/* Monthly trend */}
        <Col span={16}>
          <Card title="Monthly leave trend" loading={loading} bodyStyle={{ paddingTop: 8 }}>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={trendData}>
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="Days" fill="#1890ff" />
                <Bar dataKey="Requests" fill="#52c41a" />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* By-type pie */}
        <Col span={8}>
          <Card title="Days taken by type" loading={loading} bodyStyle={{ paddingTop: 8 }}>
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={pieData} dataKey="value" nameKey="name" outerRadius={80} label>
                  {pieData.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        {/* By-type table */}
        <Col span={14}>
          <Card title="By leave type" loading={loading}>
            <Table
              rowKey="typeCode"
              size="small"
              pagination={false}
              dataSource={stats?.byType ?? []}
              columns={[
                { title: 'Code', dataIndex: 'typeCode', width: 80 },
                { title: 'Leave type', dataIndex: 'typeName' },
                { title: 'Requests', dataIndex: 'requestCount', width: 90, align: 'right' },
                { title: 'Days', render: (_, r) => Number(r.totalDays).toFixed(1), width: 80, align: 'right' },
              ]}
            />
          </Card>
        </Col>

        {/* Absence hotspots */}
        <Col span={10}>
          <Card title="Top absence employees (YTD)" loading={loading}>
            <Table
              rowKey="employeeNo"
              size="small"
              pagination={false}
              dataSource={stats?.absenceHotspots ?? []}
              columns={hotspotCols}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
