import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  DatePicker,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  positionVarianceApi,
  VARIANCE_COLOR,
  VARIANCE_LABEL,
  type PositionVarianceRow,
  type VarianceReport,
} from '../api/positionVariance'

const { Title, Text } = Typography

/**
 * M258 — Position budget-vs-actual variance dashboard (PRD §19).
 *
 * <p>Operator picks a month (defaults to current). Backend joins:
 *   PositionBudget (planned) × PayrollResult (actual via Employee.positionId)
 * and returns per-position rows + totals + counts.
 *
 * <p>UI is deliberately one screen: 4 summary stats up top, then the
 * full per-position table sorted by absolute variance descending so
 * the biggest miss-by-amount sits at the top.
 */
export function PositionVariancePage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  // Default to current month — operators most often look "what's
  // happening right now" rather than picking a back month.
  const today = dayjs()
  const initialMonth = useMemo(() => {
    const y = Number(params.get('year'))
    const m = Number(params.get('month'))
    if (y && m) return dayjs(`${y}-${String(m).padStart(2, '0')}-01`)
    return today.startOf('month')
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const [month, setMonth] = useState(initialMonth)
  const [report, setReport] = useState<VarianceReport | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    positionVarianceApi
      .fetch(month.year(), month.month() + 1)
      .then(setReport)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load variance'),
      )
      .finally(() => setLoading(false))
    // Keep the URL in sync so the operator can bookmark / share.
    setParams(
      { year: String(month.year()), month: String(month.month() + 1) },
      { replace: true },
    )
  }, [month, message, setParams])

  const fmt = (v: number | null | undefined, ccy?: string | null) =>
    v == null
      ? '—'
      : `${Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 })} ${ccy ?? ''}`.trim()

  const columns: ColumnsType<PositionVarianceRow> = [
    {
      title: 'Position',
      dataIndex: 'positionTitle',
      render: (v: string, r: PositionVarianceRow) => (
        <Space size={4}>
          <Link to={`/positions/${r.positionId}/edit`}>{v}</Link>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {r.positionCode}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Org Unit',
      dataIndex: 'orgUnitLabel',
      width: 160,
      render: (v?: string | null) => v || '—',
    },
    {
      title: 'HC',
      key: 'hc',
      width: 80,
      align: 'center' as const,
      render: (_: unknown, r: PositionVarianceRow) => (
        <Text style={{ fontSize: 12 }}>
          {r.actualHeadcount}/{r.approvedHeadcount}
        </Text>
      ),
    },
    {
      title: 'Budgeted',
      dataIndex: 'budgeted',
      width: 140,
      align: 'right' as const,
      render: (v: number | null, r: PositionVarianceRow) => fmt(v, r.currency),
    },
    {
      title: 'Actual',
      dataIndex: 'actual',
      width: 140,
      align: 'right' as const,
      render: (v: number | null, r: PositionVarianceRow) => fmt(v, r.currency),
    },
    {
      title: 'Variance',
      dataIndex: 'variance',
      width: 140,
      align: 'right' as const,
      render: (v: number | null, r: PositionVarianceRow) => {
        if (v == null) return '—'
        const sign = v > 0 ? '+' : ''
        return (
          <Text
            strong
            type={v > 0 ? 'danger' : v < 0 ? 'success' : undefined}
          >
            {sign}
            {fmt(v, r.currency)}
          </Text>
        )
      },
    },
    {
      title: '%',
      dataIndex: 'variancePct',
      width: 90,
      align: 'right' as const,
      render: (v: number | null) =>
        v == null ? (
          '—'
        ) : (
          <Text type={v > 5 ? 'danger' : v < -5 ? 'success' : undefined}>
            {v > 0 ? '+' : ''}
            {Number(v).toFixed(1)}%
          </Text>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 150,
      render: (s: PositionVarianceRow['status']) => (
        <Tag color={VARIANCE_COLOR[s]}>{VARIANCE_LABEL[s]}</Tag>
      ),
    },
  ]

  return (
    <Card
      title={
        <Space>
          <Title level={4} style={{ margin: 0 }}>
            💰 Budget vs Actual (PRD §19)
          </Title>
        </Space>
      }
      extra={
        <Space>
          <Text type="secondary">Month:</Text>
          <DatePicker
            picker="month"
            value={month}
            allowClear={false}
            onChange={(v) => v && setMonth(v.startOf('month'))}
            disabledDate={(d) => d.isAfter(today, 'month')}
          />
          <a onClick={() => navigate('/positions')}>← Positions</a>
        </Space>
      }
    >
      {report && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="Total budgeted"
                value={Number(report.totals.totalBudget ?? 0)}
                precision={0}
                suffix="AZN"
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="Total actual"
                value={Number(report.totals.totalActual ?? 0)}
                precision={0}
                suffix="AZN"
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="Net variance"
                value={Number(report.totals.totalVariance ?? 0)}
                precision={0}
                suffix="AZN"
                valueStyle={{
                  color:
                    (report.totals.totalVariance ?? 0) > 0
                      ? '#cf1322'
                      : (report.totals.totalVariance ?? 0) < 0
                        ? '#3f8600'
                        : undefined,
                }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Space size={8}>
                <Tag color="red">🔴 Over: {report.totals.overCount}</Tag>
                <Tag color="blue">🔵 Under: {report.totals.underCount}</Tag>
                <Tag color="gold">⚠ No budget: {report.totals.noBudgetCount}</Tag>
              </Space>
            </Card>
          </Col>
        </Row>
      )}

      <Table<PositionVarianceRow>
        rowKey="positionId"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={report?.rows ?? []}
        pagination={{ pageSize: 25, showSizeChanger: true }}
      />
    </Card>
  )
}
