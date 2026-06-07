// M131 — Team time-off calendar.
//
// Managers + HR view every team member's APPROVED + PENDING leave on one
// month grid, with a daily roll-up that flags days where too many people
// are out at once (default threshold 40% of team size). Read-only — no
// approvals here; the actual workflow stays on LeaveRequestsPage.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Calendar,
  Card,
  Col,
  DatePicker,
  Empty,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { leaveApi, type TeamCalendarResponse, type TeamLeaveEntry } from '../api/leave'
import { orgApi, type OrgUnitResponse } from '../api/org'

const { Title, Text } = Typography

function fmt(d: string | Dayjs): string {
  return typeof d === 'string' ? dayjs(d).format('MMM D') : d.format('MMM D')
}

export function TeamLeaveCalendarPage() {
  const { message } = AntdApp.useApp()

  const [month, setMonth] = useState<Dayjs>(dayjs().startOf('month'))
  const [units, setUnits] = useState<OrgUnitResponse[]>([])
  const [orgUnitId, setOrgUnitId] = useState<string | undefined>()
  const [threshold, setThreshold] = useState<number>(40)
  const [data, setData] = useState<TeamCalendarResponse | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    // Load org-unit picker options once from the active version.
    orgApi.active()
      .then((v) => v ? orgApi.units(v.id) : Promise.resolve([] as OrgUnitResponse[]))
      .then(setUnits)
      .catch(() => { /* tolerated: page still works without org filter */ })
  }, [])

  const load = () => {
    const windowStart = month.startOf('month').format('YYYY-MM-DD')
    const windowEnd = month.endOf('month').format('YYYY-MM-DD')
    setLoading(true)
    leaveApi.teamCalendar({ orgUnitId, windowStart, windowEnd, thresholdPercent: threshold })
      .then(setData)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load calendar'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [month, orgUnitId])

  // Index entries by day for the calendar cell renderer.
  const entriesByDay = useMemo(() => {
    const out = new Map<string, TeamLeaveEntry[]>()
    if (!data) return out
    for (const e of data.entries) {
      let cur = dayjs(e.startDate)
      const end = dayjs(e.endDate)
      while (!cur.isAfter(end)) {
        const k = cur.format('YYYY-MM-DD')
        const arr = out.get(k) ?? []
        arr.push(e)
        out.set(k, arr)
        cur = cur.add(1, 'day')
      }
    }
    return out
  }, [data])

  const daysIndex = useMemo(() => {
    const m = new Map<string, { outCount: number; percentOff: number; flagged: boolean }>()
    if (!data) return m
    for (const d of data.days) m.set(d.date, d)
    return m
  }, [data])

  const flaggedDates = useMemo(
    () => data?.days.filter((d) => d.flagged).map((d) => d.date) ?? [],
    [data],
  )

  const cellRender = (current: Dayjs) => {
    if (current.month() !== month.month()) return null
    const key = current.format('YYYY-MM-DD')
    const items = entriesByDay.get(key) ?? []
    const rollup = daysIndex.get(key)
    return (
      <div style={{ minHeight: 60 }}>
        {rollup && rollup.outCount > 0 && (
          <Tag color={rollup.flagged ? 'red' : 'blue'} style={{ marginBottom: 4 }}>
            {rollup.outCount} out · {rollup.percentOff.toFixed(0)}%
          </Tag>
        )}
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          {items.slice(0, 3).map((e) => (
            <Badge
              key={e.requestId + key}
              color={e.leaveTypeColor ?? '#8884d8'}
              text={
                <span style={{ fontSize: 11, opacity: e.status === 'PENDING' ? 0.6 : 1 }}>
                  {e.employeeName ?? e.employeeId.slice(0, 6)}
                </span>
              }
            />
          ))}
          {items.length > 3 && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              +{items.length - 3} more
            </Text>
          )}
        </Space>
      </div>
    )
  }

  const entryCols: ColumnsType<TeamLeaveEntry> = [
    { title: 'Employee', dataIndex: 'employeeName', render: (v, r) => v ?? r.employeeId.slice(0, 8) },
    { title: 'Type', dataIndex: 'leaveTypeName',
      render: (v, r) => <Tag color={r.leaveTypeColor ?? 'default'}>{v ?? '—'}</Tag> },
    { title: 'From', dataIndex: 'startDate', render: (d: string) => fmt(d) },
    { title: 'To', dataIndex: 'endDate', render: (d: string) => fmt(d) },
    { title: 'Days', dataIndex: 'totalDays', align: 'right', width: 80,
      render: (n: number, r) => `${n}${r.halfDay ? ' (½)' : ''}` },
    { title: 'Status', dataIndex: 'status', width: 120,
      render: (s: string) => <Tag color={s === 'APPROVED' ? 'green' : 'gold'}>{s}</Tag> },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Team time-off calendar</Title>

      <Card size="small">
        <Row gutter={12} align="middle">
          <Col span={6}>
            <Text strong>Month:</Text>
            <DatePicker.MonthPicker
              style={{ width: '100%' }}
              value={month}
              onChange={(v) => v && setMonth(v.startOf('month'))}
              allowClear={false}
            />
          </Col>
          <Col span={8}>
            <Text strong>Org unit:</Text>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              placeholder="All of my scope"
              value={orgUnitId}
              onChange={setOrgUnitId}
              options={units.map((u) => ({ value: u.id, label: u.name }))}
            />
          </Col>
          <Col span={5}>
            <Text strong>Concurrent-absence threshold (% of team):</Text>
            <InputNumber
              min={0}
              max={100}
              step={5}
              value={threshold}
              onChange={(v) => setThreshold(typeof v === 'number' ? v : 0)}
              style={{ width: '100%' }}
            />
          </Col>
          <Col span={5} style={{ textAlign: 'right' }}>
            <Button onClick={load} loading={loading}>Refresh</Button>
          </Col>
        </Row>
      </Card>

      {data && (
        <Row gutter={16}>
          <Col span={6}>
            <Card><Statistic title="Team size" value={data.teamSize} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="Leave rows in window" value={data.entries.length} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="Flagged days" value={flaggedDates.length}
              valueStyle={{ color: flaggedDates.length > 0 ? '#fa541c' : '#52c41a' }} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="Threshold" value={data.thresholdPercent} suffix="%" /></Card>
          </Col>
        </Row>
      )}

      {flaggedDates.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message="High concurrent absence"
          description={`${flaggedDates.length} day(s) at or above ${data?.thresholdPercent}% of the team being out: ${flaggedDates.map(fmt).join(', ')}.`}
        />
      )}

      {loading && !data ? (
        <Spin />
      ) : data && data.teamSize === 0 ? (
        <Card><Empty description="No employees in scope. Pick a different org unit." /></Card>
      ) : (
        <Card>
          <Calendar
            fullscreen
            value={month}
            onPanelChange={(v) => setMonth(v.startOf('month'))}
            cellRender={cellRender}
          />
        </Card>
      )}

      {data && data.entries.length > 0 && (
        <Card title="All leave in this window">
          <Table
            rowKey="requestId"
            size="small"
            columns={entryCols}
            dataSource={data.entries}
            pagination={{ pageSize: 25 }}
          />
        </Card>
      )}
    </Space>
  )
}
