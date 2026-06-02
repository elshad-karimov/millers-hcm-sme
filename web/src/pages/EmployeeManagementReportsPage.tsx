// M80 — Employee-Management reports hub.
// Five tabs: Summary cards, Probation due, Contracts expiring, Certs expiring,
// Recent rehires.

import { useEffect, useState } from 'react'
import {
  Card,
  Col,
  Empty,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import {
  empMgmtApi,
  type CertificationExpiringRow,
  type ContractExpiringRow,
  type EmpMgmtSummary,
  type ProbationDueRow,
  type RehireRow,
} from '../api/empMgmt'

function daysTag(days: number) {
  if (days < 0) return <Tag color="red">{Math.abs(days)} days overdue</Tag>
  if (days <= 14) return <Tag color="orange">{days} days</Tag>
  return <Tag>{days} days</Tag>
}

export function EmployeeManagementReportsPage() {
  const { message } = AntdApp.useApp()
  const [summary, setSummary] = useState<EmpMgmtSummary | null>(null)
  const [probation, setProbation] = useState<ProbationDueRow[]>([])
  const [contracts, setContracts] = useState<ContractExpiringRow[]>([])
  const [certs, setCerts] = useState<CertificationExpiringRow[]>([])
  const [rehires, setRehires] = useState<RehireRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      empMgmtApi.summary(),
      empMgmtApi.probationDue().then((r) => r.rows).catch(() => []),
      empMgmtApi.contractsExpiring().then((r) => r.rows).catch(() => []),
      empMgmtApi.certsExpiring().then((r) => r.rows).catch(() => []),
      empMgmtApi.rehires().then((r) => r.rows).catch(() => []),
    ])
      .then(([s, p, c, x, r]) => {
        setSummary(s)
        setProbation(p)
        setContracts(c)
        setCerts(x)
        setRehires(r)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load reports'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const employeeLink = (id?: string | null, label?: string | null) =>
    id ? <Link to={`/employees/${id}`}>{label ?? id.slice(0, 8) + '…'}</Link> : '—'

  const probationCols: ColumnsType<ProbationDueRow> = [
    { title: 'Employee', render: (_, r) => employeeLink(r.employeeId, r.employeeNo + ' — ' + (r.fullName ?? '')) },
    { title: 'Scheduled', dataIndex: 'scheduledDate' },
    {
      title: 'Days until',
      dataIndex: 'daysUntil',
      render: (v: number) => daysTag(v),
    },
    { title: 'Type', dataIndex: 'reviewType', render: (v) => <Tag>{v}</Tag> },
    { title: 'Status', dataIndex: 'status', render: (v) => <Tag color="blue">{v}</Tag> },
  ]

  const contractCols: ColumnsType<ContractExpiringRow> = [
    { title: 'Employee', render: (_, r) => employeeLink(r.employeeId, r.employeeNo + ' — ' + (r.fullName ?? '')) },
    { title: 'Contract no', dataIndex: 'contractNo' },
    { title: 'End date', dataIndex: 'endDate' },
    {
      title: 'Days until',
      dataIndex: 'daysUntil',
      render: (v: number) => daysTag(v),
    },
    { title: 'Type', dataIndex: 'contractType', render: (v) => v ?? '—' },
  ]

  const certCols: ColumnsType<CertificationExpiringRow> = [
    { title: 'Employee', render: (_, r) => employeeLink(r.employeeId, r.employeeNo + ' — ' + (r.fullName ?? '')) },
    { title: 'Certification', dataIndex: 'certificationName' },
    { title: 'Expires', dataIndex: 'expiryDate' },
    {
      title: 'Days until',
      dataIndex: 'daysUntil',
      render: (v: number) => daysTag(v),
    },
  ]

  const rehireCols: ColumnsType<RehireRow> = [
    { title: 'Employee', render: (_, r) => employeeLink(r.employeeId, r.employeeNo + ' — ' + (r.fullName ?? '')) },
    { title: 'New hire date', dataIndex: 'hireDate', render: (v?: string | null) => v ?? '—' },
    {
      title: 'Previous',
      dataIndex: 'previousEmployeeId',
      render: (v?: string | null) => employeeLink(v, v?.slice(0, 8) + '…'),
    },
    { title: 'Reason', dataIndex: 'rehireReason', render: (v?: string | null) => v ?? '—' },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Employee Management reports
      </Typography.Title>

      {summary && (
        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="Headcount" value={summary.headcount} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="On probation" value={summary.onProbation}
              valueStyle={summary.onProbation > 0 ? { color: '#1677ff' } : {}} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="On leave today" value={summary.onLeaveToday}
              valueStyle={summary.onLeaveToday > 0 ? { color: '#fa8c16' } : {}} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="Probation due (60d)" value={summary.probationDueIn60d}
              valueStyle={summary.probationDueIn60d > 0 ? { color: '#1677ff' } : {}} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="Contracts ending (60d)" value={summary.contractsExpiringIn60d}
              valueStyle={summary.contractsExpiringIn60d > 0 ? { color: '#f5222d' } : {}} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="Certs expiring (60d)" value={summary.certsExpiringIn60d}
              valueStyle={summary.certsExpiringIn60d > 0 ? { color: '#fa541c' } : {}} /></Card>
          </Col>
          <Col xs={12} sm={8} md={6} lg={3}>
            <Card><Statistic title="Unverified IDs" value={summary.unverifiedIdentifications}
              valueStyle={summary.unverifiedIdentifications > 0 ? { color: '#faad14' } : {}} /></Card>
          </Col>
        </Row>
      )}

      <Card>
        <Tabs
          defaultActiveKey="probation"
          items={[
            {
              key: 'probation',
              label: `Probation due (${probation.length})`,
              children: (
                <Table rowKey="reviewId" columns={probationCols} dataSource={probation}
                  pagination={{ pageSize: 25 }}
                  locale={{ emptyText: <Empty description="None due in window" /> }} />
              ),
            },
            {
              key: 'contracts',
              label: `Contracts expiring (${contracts.length})`,
              children: (
                <Table rowKey="contractId" columns={contractCols} dataSource={contracts}
                  pagination={{ pageSize: 25 }}
                  locale={{ emptyText: <Empty description="No contracts ending in window" /> }} />
              ),
            },
            {
              key: 'certifications',
              label: `Certs expiring (${certs.length})`,
              children: (
                <Table rowKey="certificationId" columns={certCols} dataSource={certs}
                  pagination={{ pageSize: 25 }}
                  locale={{ emptyText: <Empty description="No certifications expiring in window" /> }} />
              ),
            },
            {
              key: 'rehires',
              label: `Recent rehires (${rehires.length})`,
              children: (
                <Table rowKey="employeeId" columns={rehireCols} dataSource={rehires}
                  pagination={{ pageSize: 25 }}
                  locale={{ emptyText: <Empty description="No rehires recorded" /> }} />
              ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}
