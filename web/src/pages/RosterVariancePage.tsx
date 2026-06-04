// M113 — Roster variance dashboard.
//
// Surfaces no-shows, late, early-leave, and unplanned overtime from the
// daily_summary rows whose schedule source = ROSTER (M112). HR + managers
// see the data scoped to what they're allowed to see.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  DatePicker,
  Empty,
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
import dayjs from 'dayjs'
import { Bar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip as RechartsTip, XAxis, YAxis } from 'recharts'
import {
  VARIANCE_COLOR,
  VARIANCE_LABEL,
  varianceApi,
  type EmployeeRoll,
  type VarianceCategory,
  type VarianceReport,
} from '../api/variance'

const { Title, Text } = Typography

const ACTIONABLE: VarianceCategory[] = ['NO_SHOW', 'LATE', 'EARLY_LEAVE', 'UNPLANNED_OT']

function fmtMinutes(m: number): string {
  if (!m) return '—'
  const h = Math.floor(m / 60)
  const rem = m % 60
  if (h === 0) return `${rem}m`
  if (rem === 0) return `${h}h`
  return `${h}h ${rem}m`
}

export function RosterVariancePage() {
  const { message } = AntdApp.useApp()
  const [range, setRange] = useState<[ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]>([
    dayjs().subtract(28, 'day'),
    dayjs(),
  ])
  const [report, setReport] = useState<VarianceReport | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    varianceApi
      .report(range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD'))
      .then(setReport)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load variance report'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line
  }, [range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD')])

  const dates = useMemo(() => {
    const list: dayjs.Dayjs[] = []
    let cursor = range[0]
    while (!cursor.isAfter(range[1])) {
      list.push(cursor)
      cursor = cursor.add(1, 'day')
    }
    return list
  }, [range])

  const cellMap = useMemo(() => {
    if (!report) return new Map<string, VarianceCategory>()
    const m = new Map<string, VarianceCategory>()
    for (const c of report.cells) {
      m.set(`${c.employeeId}|${c.workDate}`, c.category)
    }
    return m
  }, [report])

  if (loading) return <Spin />
  if (!report) return <Empty description="No data" />

  const totalsChartData = ACTIONABLE.map((k) => ({
    name: VARIANCE_LABEL[k],
    value: report.totals[k] ?? 0,
    color: VARIANCE_COLOR[k],
  })).concat([
    {
      name: VARIANCE_LABEL.ON_TIME,
      value: report.totals.ON_TIME ?? 0,
      color: VARIANCE_COLOR.ON_TIME,
    },
  ])

  const rollCols: ColumnsType<EmployeeRoll> = [
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.employeeName ?? '—'}</Text>
          {r.orgUnitLabel && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.orgUnitLabel}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Days rostered',
      dataIndex: 'rosteredDays',
      width: 110,
      align: 'right',
    },
    {
      title: 'On time',
      dataIndex: 'onTime',
      width: 90,
      align: 'right',
      render: (v: number) => v === 0
        ? <Text type="secondary">—</Text>
        : <Tag color="green">{v}</Tag>,
    },
    {
      title: 'Late',
      dataIndex: 'late',
      width: 90,
      align: 'right',
      render: (v: number, r) => v === 0
        ? <Text type="secondary">—</Text>
        : (
          <Tooltip title={`Total: ${fmtMinutes(r.totalLateMinutes)}`}>
            <Tag color="orange">{v}</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Left early',
      dataIndex: 'earlyLeave',
      width: 100,
      align: 'right',
      render: (v: number, r) => v === 0
        ? <Text type="secondary">—</Text>
        : (
          <Tooltip title={`Total: ${fmtMinutes(r.totalEarlyMinutes)}`}>
            <Tag color="gold">{v}</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Unplanned OT',
      dataIndex: 'unplannedOt',
      width: 130,
      align: 'right',
      render: (v: number, r) => v === 0
        ? <Text type="secondary">—</Text>
        : (
          <Tooltip title={`Total: ${fmtMinutes(r.totalOvertimeMinutes)}`}>
            <Tag color="purple">{v}</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'No-show',
      dataIndex: 'noShow',
      width: 100,
      align: 'right',
      render: (v: number) => v === 0
        ? <Text type="secondary">—</Text>
        : <Tag color="red">{v}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Roster variance</Title>
      <Text type="secondary">
        Compares the rostered shift against actual punches. Only days where the schedule came
        from the roster (M110+) are counted — legacy WorkSchedule days are excluded so the
        report stays focused on rostered teams.
      </Text>

      <Space>
        <Text>Window:</Text>
        <DatePicker.RangePicker
          value={range}
          onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
          allowClear={false}
        />
      </Space>

      <Row gutter={16}>
        <Col span={4}>
          <Card>
            <Statistic title="Rostered days scanned" value={report.rosteredRowsScanned} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic
              title="No-shows"
              value={report.totals.NO_SHOW ?? 0}
              valueStyle={{ color: VARIANCE_COLOR.NO_SHOW }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic
              title="Late days"
              value={report.totals.LATE ?? 0}
              valueStyle={{ color: VARIANCE_COLOR.LATE }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic
              title="Left-early days"
              value={report.totals.EARLY_LEAVE ?? 0}
              valueStyle={{ color: VARIANCE_COLOR.EARLY_LEAVE }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic
              title="Unplanned OT"
              value={report.totals.UNPLANNED_OT ?? 0}
              valueStyle={{ color: VARIANCE_COLOR.UNPLANNED_OT }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic
              title="On time"
              value={report.totals.ON_TIME ?? 0}
              valueStyle={{ color: VARIANCE_COLOR.ON_TIME }}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Distribution">
        <div style={{ width: '100%', height: 220 }}>
          <ResponsiveContainer>
            <BarChart data={totalsChartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <RechartsTip />
              <Legend />
              <Bar dataKey="value" name="Days">
                {totalsChartData.map((d, i) => (
                  <Cell key={i} fill={d.color} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card title={`Heatmap (${range[0].format('MMM D')} – ${range[1].format('MMM D')})`}
            style={{ overflowX: 'auto' }}>
        {report.byEmployee.length === 0 ? (
          <Empty description="No rostered days in this window." />
        ) : (
          <table style={{ borderCollapse: 'collapse', fontSize: 11 }}>
            <thead>
              <tr>
                <th style={{ ...cellHeader, textAlign: 'left', minWidth: 200, position: 'sticky', left: 0, background: '#fafbfc', zIndex: 2 }}>
                  Employee
                </th>
                {dates.map((d) => (
                  <th key={d.format('YYYY-MM-DD')}
                    style={{
                      ...cellHeader,
                      background: d.day() === 0 || d.day() === 6 ? '#fafafa' : undefined,
                    }}>
                    {d.format('D')}
                    <div style={{ fontSize: 9, color: '#999' }}>{d.format('ddd')}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {report.byEmployee.map((row) => (
                <tr key={row.employeeId}>
                  <td style={{ ...cell, position: 'sticky', left: 0, background: 'white', zIndex: 1 }}>
                    <Text strong style={{ fontSize: 12 }}>{row.employeeName ?? '—'}</Text>
                  </td>
                  {dates.map((d) => {
                    const key = `${row.employeeId}|${d.format('YYYY-MM-DD')}`
                    const cat = cellMap.get(key)
                    return (
                      <td key={key}
                        style={{
                          ...cell,
                          background: cat ? VARIANCE_COLOR[cat] : '#fff',
                          opacity: cat === 'ON_TIME' ? 0.5 : 1,
                          textAlign: 'center',
                        }}>
                        {cat && cat !== 'ON_TIME' && cat !== 'NOT_APPLICABLE' ? (
                          <Tooltip title={`${VARIANCE_LABEL[cat]} — ${d.format('YYYY-MM-DD')}`}>
                            <span style={{ color: 'white', fontWeight: 600 }}>
                              {cat === 'NO_SHOW' ? '×' :
                                cat === 'LATE' ? 'L' :
                                cat === 'EARLY_LEAVE' ? 'E' :
                                cat === 'UNPLANNED_OT' ? '+' : ''}
                            </span>
                          </Tooltip>
                        ) : null}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="By employee (worst variance first)">
        <Table
          rowKey="employeeId"
          columns={rollCols}
          dataSource={report.byEmployee}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No rostered days in this window" /> }}
        />
      </Card>
    </Space>
  )
}

const cellHeader: React.CSSProperties = {
  border: '1px solid #f0f0f0',
  padding: '4px 6px',
  background: '#fafbfc',
  textAlign: 'center',
  fontWeight: 600,
  fontSize: 11,
  minWidth: 28,
}

const cell: React.CSSProperties = {
  border: '1px solid #f0f0f0',
  padding: '4px',
  width: 28,
  height: 28,
}
