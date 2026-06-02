// M81 — Span-of-control report. Standalone page so the org / reports nav can
// link to it directly; the EmpMgmt report family stays focused on people
// lifecycle (probation, contracts, certs, rehires).

import { useEffect, useState } from 'react'
import {
  Card,
  Col,
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
import { orgReportApi, type SpanOfControlReport, type SpanOfControlRow } from '../api/org'

const FLAG_COLOR: Record<SpanOfControlRow['flag'], string> = {
  OK: 'green',
  OVERSPAN: 'red',
  UNDERSPAN: 'gold',
}

export function SpanOfControlPage() {
  const { message } = AntdApp.useApp()
  const [report, setReport] = useState<SpanOfControlReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    orgReportApi
      .spanOfControl()
      .then(setReport)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load span-of-control report'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<SpanOfControlRow> = [
    {
      title: 'Manager',
      render: (_, r) => (
        <Link to={`/employees/${r.managerId}`}>
          {r.employeeNo} — {r.fullName}
        </Link>
      ),
    },
    { title: 'Position', dataIndex: 'positionTitle', render: (v) => v ?? '—' },
    { title: 'Department', dataIndex: 'departmentName', render: (v) => v ?? '—' },
    {
      title: 'Direct reports',
      dataIndex: 'directReports',
      sorter: (a, b) => a.directReports - b.directReports,
      defaultSortOrder: 'descend',
    },
    { title: 'Transitive', dataIndex: 'transitiveReports' },
    { title: 'Depth', dataIndex: 'depth' },
    {
      title: 'Flag',
      dataIndex: 'flag',
      render: (v: SpanOfControlRow['flag']) => <Tag color={FLAG_COLOR[v]}>{v}</Tag>,
    },
  ]

  if (loading || !report) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Span of control
      </Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card><Statistic title="Managers" value={report.managersCount} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="Overspan" value={report.overspanCount}
              valueStyle={report.overspanCount > 0 ? { color: '#f5222d' } : {}} />
            <Typography.Text type="secondary">
              ≥ {report.overspanThreshold} direct reports
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="Underspan" value={report.underspanCount}
              valueStyle={report.underspanCount > 0 ? { color: '#faad14' } : {}} />
            <Typography.Text type="secondary">
              ≤ {report.underspanThreshold} direct reports
            </Typography.Text>
          </Card>
        </Col>
      </Row>
      <Card>
        <Table
          rowKey="managerId"
          columns={columns}
          dataSource={report.rows}
          pagination={{ pageSize: 50 }}
          size="small"
        />
      </Card>
    </Space>
  )
}
