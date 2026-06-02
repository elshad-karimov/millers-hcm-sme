// M76 — Manager self-service team dashboard.
// Top: summary cards (headcount, on-leave-today, on-probation, pending leaves,
// contracts ending). Middle: upcoming probation reviews / contract ends.
// Bottom: direct-reports table with quick link to each member's profile.

import { useEffect, useState } from 'react'
import {
  Alert,
  Card,
  Col,
  Empty,
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
import { Link } from 'react-router-dom'
import {
  teamApi,
  type TeamMember,
  type TeamSummary,
  type UpcomingItem,
} from '../api/team'

export function TeamPage() {
  const { message } = AntdApp.useApp()
  const [team, setTeam] = useState<TeamMember[]>([])
  const [summary, setSummary] = useState<TeamSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([teamApi.list(), teamApi.summary()])
      .then(([list, sum]) => {
        setTeam(list)
        setSummary(sum)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load team data'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const memberColumns: ColumnsType<TeamMember> = [
    {
      title: 'Employee no',
      dataIndex: 'employeeNo',
      render: (v: string, r) => <Link to={`/employees/${r.id}`}>{v}</Link>,
    },
    {
      title: 'Name',
      render: (_, r) => `${r.firstName} ${r.lastName}`,
    },
    { title: 'Position', dataIndex: 'positionTitle', render: (v) => v ?? '—' },
    { title: 'Department', dataIndex: 'departmentName', render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'employmentStatus',
      render: (v?: string | null) => (v ? <Tag>{v.replace(/_/g, ' ')}</Tag> : '—'),
    },
    { title: 'Hired', dataIndex: 'hireDate', render: (v) => v ?? '—' },
    {
      title: 'Today',
      dataIndex: 'onLeaveToday',
      render: (v: boolean) =>
        v ? <Tag color="orange">ON LEAVE</Tag> : <Tag color="green">PRESENT</Tag>,
    },
  ]

  const upcomingColumns: ColumnsType<UpcomingItem> = [
    { title: 'Date', dataIndex: 'date' },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (v: string) => <Link to={`/employees/${v}`}>{v.slice(0, 8)}…</Link>,
    },
    { title: 'Item', dataIndex: 'label' },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3}>My team</Typography.Title>

      {summary && summary.headcount === 0 ? (
        <Alert
          type="info"
          showIcon
          message="You don't have any direct reports yet."
          description="Once an employee's manager is set to you, they'll appear here."
        />
      ) : (
        summary && (
          <>
            <Row gutter={16}>
              <Col xs={12} sm={8} md={4}>
                <Card><Statistic title="Headcount" value={summary.headcount} /></Card>
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Card>
                  <Statistic title="On leave today" value={summary.onLeaveToday}
                    valueStyle={summary.onLeaveToday > 0 ? { color: '#fa8c16' } : {}} />
                </Card>
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Card>
                  <Statistic title="On probation" value={summary.onProbation}
                    valueStyle={summary.onProbation > 0 ? { color: '#1677ff' } : {}} />
                </Card>
              </Col>
              <Col xs={12} sm={8} md={4}>
                <Card>
                  <Statistic title="Pending leave requests" value={summary.pendingLeaveRequests}
                    valueStyle={summary.pendingLeaveRequests > 0 ? { color: '#faad14' } : {}} />
                </Card>
              </Col>
              <Col xs={24} sm={16} md={8}>
                <Card>
                  <Statistic title="Contracts ending in 60 days"
                    value={summary.contractsEndingSoon}
                    valueStyle={summary.contractsEndingSoon > 0 ? { color: '#f5222d' } : {}} />
                </Card>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Card title="Upcoming probation reviews" size="small">
                  <Table
                    rowKey={(r) => r.employeeId + r.date + r.label}
                    columns={upcomingColumns}
                    dataSource={summary.upcomingProbationReviews}
                    pagination={false}
                    size="small"
                    locale={{ emptyText: <Empty description="None scheduled" /> }}
                  />
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card title="Upcoming contract ends" size="small">
                  <Table
                    rowKey={(r) => r.employeeId + r.date + r.label}
                    columns={upcomingColumns}
                    dataSource={summary.upcomingContractEnds}
                    pagination={false}
                    size="small"
                    locale={{ emptyText: <Empty description="None ending soon" /> }}
                  />
                </Card>
              </Col>
            </Row>

            <Card title="Direct reports" size="small">
              <Table
                rowKey="id"
                columns={memberColumns}
                dataSource={team}
                pagination={false}
                size="small"
              />
            </Card>
          </>
        )
      )}
    </Space>
  )
}
