import { useEffect, useState } from 'react'
import {
  Badge,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tabs,
  Typography,
  App as AntdApp,
  Button,
} from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  orgNativeReportsApi,
  type HeadcountRow,
  type HrbpCoverageRow,
  type OrgDistributionReport,
  type OrgUnitFlatRow,
} from '../api/orgNativeReports'
import { LIFECYCLE_COLOR, type OrgUnitLifecycleState } from '../api/orgUnitLifecycle'

const { Title, Text } = Typography

// CSV export helper
function toCsv(rows: OrgUnitFlatRow[]): string {
  const headers = [
    'Code', 'Name', 'Type', 'Parent', 'Lifecycle', 'HRBP',
    'Cost Centre', 'Contact Email', 'Budget', 'Actual', 'Closure Announced', 'Closed Date',
  ]
  const escape = (v: unknown) => {
    const s = v == null ? '' : String(v)
    return s.includes(',') || s.includes('"') ? `"${s.replace(/"/g, '""')}"` : s
  }
  const lines = [
    headers.join(','),
    ...rows.map((r) =>
      [
        r.code, r.name, r.unitType, r.parentCode ?? '', r.lifecycleState ?? '',
        r.hrbpEmployeeNo ?? '', r.costCentreCode ?? '', r.contactEmail ?? '',
        r.headcountBudget ?? '', r.actualHeadcount,
        r.closureAnnouncedDate ?? '', r.closedDate ?? '',
      ]
        .map(escape)
        .join(','),
    ),
  ]
  return lines.join('\n')
}

function downloadCsv(csv: string) {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `org-units-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export function OrgNativeReportsPage() {
  const { message } = AntdApp.useApp()
  const [headcountData, setHeadcountData] = useState<HeadcountRow[]>([])
  const [headcountTotals, setHeadcountTotals] = useState({ budget: 0, actual: 0, variance: 0 })
  const [hrbpData, setHrbpData] = useState<HrbpCoverageRow[]>([])
  const [hrbpSummary, setHrbpSummary] = useState({ total: 0, with: 0, without: 0, pct: 0 })
  const [distData, setDistData] = useState<OrgDistributionReport | null>(null)
  const [flatRows, setFlatRows] = useState<OrgUnitFlatRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      orgNativeReportsApi.headcount(),
      orgNativeReportsApi.hrbpCoverage(),
      orgNativeReportsApi.distribution(),
      orgNativeReportsApi.flat(),
    ])
      .then(([hc, hrbp, dist, flat]) => {
        setHeadcountData(hc.rows)
        setHeadcountTotals({
          budget: hc.totalBudget,
          actual: hc.totalActual,
          variance: hc.totalVariance ?? 0,
        })
        setHrbpData(hrbp.rows)
        setHrbpSummary({
          total: hrbp.totalUnits,
          with: hrbp.unitsWithHrbp,
          without: hrbp.unitsWithoutHrbp,
          pct: hrbp.coveragePct,
        })
        setDistData(dist)
        setFlatRows(flat.rows)
      })
      .catch(() => message.error('Failed to load org reports'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const headcountCols: ColumnsType<HeadcountRow> = [
    { title: 'Code', dataIndex: 'code', width: 100 },
    {
      title: 'Unit',
      dataIndex: 'name',
      render: (n: string, r) => (
        <Space size={4}>
          {n}
          {r.lifecycleState && r.lifecycleState !== 'ACTIVE' && (
            <Tag color={LIFECYCLE_COLOR[r.lifecycleState as OrgUnitLifecycleState]} style={{ fontSize: 11 }}>
              {r.lifecycleState}
            </Tag>
          )}
        </Space>
      ),
    },
    { title: 'Type', dataIndex: 'unitType', width: 120 },
    { title: 'Budget', dataIndex: 'headcountBudget', width: 80, render: (v) => v ?? '—' },
    { title: 'Actual', dataIndex: 'actualHeadcount', width: 80 },
    {
      title: 'Variance',
      dataIndex: 'variance',
      width: 90,
      render: (v: number | null) => {
        if (v == null) return '—'
        const color = v > 0 ? 'green' : v < 0 ? 'red' : 'default'
        return <Tag color={color}>{v > 0 ? `+${v}` : v}</Tag>
      },
    },
  ]

  const hrbpCols: ColumnsType<HrbpCoverageRow> = [
    { title: 'Code', dataIndex: 'code', width: 100 },
    { title: 'Unit', dataIndex: 'name' },
    { title: 'Type', dataIndex: 'unitType', width: 120 },
    {
      title: 'HRBP',
      render: (_: unknown, r) =>
        r.hasHrbp ? (
          <Space size={4}>
            <Badge status="success" />
            <Text>{r.hrbpName}</Text>
          </Space>
        ) : (
          <Space size={4}>
            <Badge status="warning" />
            <Text type="secondary">Not assigned</Text>
          </Space>
        ),
    },
  ]

  const flatCols: ColumnsType<OrgUnitFlatRow> = [
    { title: 'Code', dataIndex: 'code', width: 90 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Type', dataIndex: 'unitType', width: 110 },
    { title: 'Parent', dataIndex: 'parentCode', width: 90, render: (v) => v ?? '—' },
    { title: 'State', dataIndex: 'lifecycleState', width: 100 },
    { title: 'HRBP', dataIndex: 'hrbpEmployeeNo', width: 100, render: (v) => v ?? '—' },
    { title: 'CC code', dataIndex: 'costCentreCode', width: 100, render: (v) => v ?? '—' },
    { title: 'Budget', dataIndex: 'headcountBudget', width: 80, render: (v) => v ?? '—' },
    { title: 'Actual', dataIndex: 'actualHeadcount', width: 80 },
  ]

  const distEntries = (rec: Record<string, number> | undefined) =>
    rec ? Object.entries(rec).sort((a, b) => b[1] - a[1]) : []

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 24 }}>Org reports</Title>

      <Tabs
        items={[
          {
            key: 'headcount',
            label: 'Headcount',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col><Statistic title="Total budget" value={headcountTotals.budget} /></Col>
                  <Col><Statistic title="Actual headcount" value={headcountTotals.actual} /></Col>
                  <Col>
                    <Statistic
                      title="Variance"
                      value={headcountTotals.variance}
                      valueStyle={{ color: headcountTotals.variance >= 0 ? '#3f8600' : '#cf1322' }}
                    />
                  </Col>
                </Row>
                <Table
                  rowKey="unitId"
                  loading={loading}
                  dataSource={headcountData}
                  columns={headcountCols}
                  size="small"
                  pagination={{ pageSize: 25 }}
                />
              </>
            ),
          },
          {
            key: 'hrbp',
            label: 'HRBP coverage',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col><Statistic title="Total units" value={hrbpSummary.total} /></Col>
                  <Col>
                    <Statistic title="With HRBP" value={hrbpSummary.with}
                      valueStyle={{ color: '#3f8600' }} />
                  </Col>
                  <Col>
                    <Statistic title="Without HRBP" value={hrbpSummary.without}
                      valueStyle={{ color: hrbpSummary.without > 0 ? '#cf1322' : undefined }} />
                  </Col>
                  <Col span={6}>
                    <div style={{ paddingTop: 4 }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>Coverage</Text>
                      <Progress
                        percent={Math.round(hrbpSummary.pct)}
                        status={hrbpSummary.pct < 80 ? 'exception' : 'success'}
                        style={{ marginTop: 4 }}
                      />
                    </div>
                  </Col>
                </Row>
                <Table
                  rowKey="unitId"
                  loading={loading}
                  dataSource={hrbpData}
                  columns={hrbpCols}
                  size="small"
                  pagination={{ pageSize: 25 }}
                />
              </>
            ),
          },
          {
            key: 'dist',
            label: 'Distribution',
            children: (
              <Row gutter={24}>
                <Col span={12}>
                  <Card title="By lifecycle state" size="small">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {distEntries(distData?.byLifecycleState).map(([state, count]) => (
                        <Row key={state} justify="space-between">
                          <Col>
                            <Tag color={LIFECYCLE_COLOR[state as OrgUnitLifecycleState]}>
                              {state}
                            </Tag>
                          </Col>
                          <Col><Text strong>{count}</Text></Col>
                        </Row>
                      ))}
                    </Space>
                  </Card>
                </Col>
                <Col span={12}>
                  <Card title="By unit type" size="small">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {distEntries(distData?.byUnitType).map(([type, count]) => (
                        <Row key={type} justify="space-between">
                          <Col><Tag>{type}</Tag></Col>
                          <Col><Text strong>{count}</Text></Col>
                        </Row>
                      ))}
                    </Space>
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'flat',
            label: 'Flat export',
            children: (
              <>
                <div style={{ marginBottom: 12, textAlign: 'right' }}>
                  <Button
                    icon={<DownloadOutlined />}
                    onClick={() => downloadCsv(toCsv(flatRows))}
                    disabled={flatRows.length === 0}
                  >
                    Download CSV
                  </Button>
                </div>
                <Table
                  rowKey="unitId"
                  loading={loading}
                  dataSource={flatRows}
                  columns={flatCols}
                  size="small"
                  pagination={{ pageSize: 25 }}
                  scroll={{ x: 900 }}
                />
              </>
            ),
          },
        ]}
      />
    </div>
  )
}
