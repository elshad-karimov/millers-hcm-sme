// M125 — Real-time presence map (polling Phase 1).
//
// Polls /api/presence/snapshot every 30 seconds (configurable). State
// resolution is done server-side via PresenceResolver; the SPA only
// renders. The page works at any scope: managers see their team, HR
// sees everyone the access scope allows.

import { useEffect, useMemo, useRef, useState } from 'react'
import {
  App as AntdApp,
  Badge,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Statistic,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  presenceApi,
  type PresenceRow,
  type PresenceSnapshot,
  type PresenceState,
} from '../api/presence'

const { Title, Text, Paragraph } = Typography

const STATE_COLOR: Record<PresenceState, string> = {
  IN_OFFICE: '#52c41a',
  OFFLINE: '#888',
  ON_LEAVE: '#1677ff',
  ON_TRIP: '#722ed1',
  NOT_SCHEDULED: '#bfbfbf',
  UNKNOWN: '#fa8c16',
}

const STATE_LABEL: Record<PresenceState, string> = {
  IN_OFFICE: 'In office',
  OFFLINE: 'Offline',
  ON_LEAVE: 'On leave',
  ON_TRIP: 'On trip',
  NOT_SCHEDULED: 'Off',
  UNKNOWN: '—',
}

const POLL_OPTIONS = [
  { label: '15s', value: 15 },
  { label: '30s', value: 30 },
  { label: '1m', value: 60 },
  { label: '5m', value: 300 },
]

const ORDER: PresenceState[] = ['IN_OFFICE', 'ON_TRIP', 'ON_LEAVE', 'OFFLINE', 'NOT_SCHEDULED', 'UNKNOWN']

export function PresenceMapPage() {
  const { message } = AntdApp.useApp()
  const [snap, setSnap] = useState<PresenceSnapshot | null>(null)
  const [loading, setLoading] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [pollSeconds, setPollSeconds] = useState(30)
  const [view, setView] = useState<'grid' | 'table'>('grid')
  const [search, setSearch] = useState('')
  const [stateFilter, setStateFilter] = useState<PresenceState | undefined>()
  const [deptFilter, setDeptFilter] = useState<string | undefined>()
  const intervalRef = useRef<number | null>(null)

  const load = () => {
    setLoading(true)
    presenceApi.snapshot()
      .then(setSnap)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load snapshot'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Polling — react to autoRefresh + pollSeconds.
  useEffect(() => {
    if (intervalRef.current !== null) {
      window.clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    if (autoRefresh) {
      intervalRef.current = window.setInterval(load, pollSeconds * 1000)
    }
    return () => {
      if (intervalRef.current !== null) window.clearInterval(intervalRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh, pollSeconds])

  const departments = useMemo(() => {
    if (!snap) return [] as string[]
    const set = new Set<string>()
    snap.rows.forEach((r) => { if (r.department) set.add(r.department) })
    return [...set].sort()
  }, [snap])

  const filtered = useMemo(() => {
    if (!snap) return [] as PresenceRow[]
    const q = search.trim().toLowerCase()
    return snap.rows.filter((r) =>
      (!stateFilter || r.state === stateFilter) &&
      (!deptFilter || r.department === deptFilter) &&
      (!q || r.employeeName.toLowerCase().includes(q) || r.employeeNo.toLowerCase().includes(q)),
    )
  }, [snap, search, stateFilter, deptFilter])

  const grouped = useMemo(() => {
    const m = new Map<string, PresenceRow[]>()
    for (const r of filtered) {
      const k = r.department ?? '— Unassigned —'
      const list = m.get(k) ?? []
      list.push(r)
      m.set(k, list)
    }
    return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0]))
  }, [filtered])

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Space direction="vertical" size={0}>
          <Title level={3} style={{ margin: 0 }}>Presence map</Title>
          {snap && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Snapshot for <Text strong>{dayjs(snap.generatedFor).format('YYYY-MM-DD')}</Text>
              {' · '}refreshed {dayjs(snap.generatedAt).format('HH:mm:ss')}
            </Text>
          )}
        </Space>
        <Space>
          <Text type="secondary" style={{ fontSize: 12 }}>Auto-refresh</Text>
          <Switch checked={autoRefresh} onChange={setAutoRefresh} />
          <Select
            disabled={!autoRefresh}
            value={pollSeconds}
            onChange={setPollSeconds}
            style={{ width: 90 }}
            options={POLL_OPTIONS}
          />
          <Segmented
            value={view}
            onChange={(v) => setView(v as 'grid' | 'table')}
            options={[
              { label: 'Grid', value: 'grid' },
              { label: 'Table', value: 'table' },
            ]}
          />
        </Space>
      </Space>

      {/* ── State counts ──────────────────────────────────────── */}
      <Row gutter={12} style={{ marginBottom: 16 }}>
        {ORDER.map((s) => (
          <Col key={s}>
            <Card
              size="small"
              hoverable
              onClick={() => setStateFilter(stateFilter === s ? undefined : s)}
              style={{
                minWidth: 120,
                borderColor: stateFilter === s ? STATE_COLOR[s] : undefined,
                borderWidth: stateFilter === s ? 2 : 1,
              }}
            >
              <Statistic
                title={
                  <Space size={6}>
                    <Badge color={STATE_COLOR[s]} />
                    <Text style={{ fontSize: 12 }}>{STATE_LABEL[s]}</Text>
                  </Space>
                }
                value={snap?.counts?.[s] ?? 0}
                valueStyle={{ fontSize: 22, color: STATE_COLOR[s] }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      {/* ── Filters ───────────────────────────────────────────── */}
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="Search name or ID"
          allowClear
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ width: 260 }}
        />
        <Select
          allowClear
          placeholder="All departments"
          value={deptFilter}
          onChange={setDeptFilter}
          style={{ width: 220 }}
          options={departments.map((d) => ({ value: d, label: d }))}
        />
        {stateFilter && (
          <Tag closable color={STATE_COLOR[stateFilter]} onClose={() => setStateFilter(undefined)}>
            {STATE_LABEL[stateFilter]}
          </Tag>
        )}
      </Space>

      {/* ── Body ──────────────────────────────────────────────── */}
      {!snap && loading && <Card loading />}
      {snap && filtered.length === 0 && (
        <Empty description="No one matches the current filters." />
      )}

      {view === 'grid' && snap && filtered.length > 0 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {grouped.map(([dept, rows]) => (
            <Card key={dept} title={<Text strong>{dept} <Text type="secondary">({rows.length})</Text></Text>} size="small">
              <Row gutter={[12, 12]}>
                {rows.map((r) => (
                  <Col key={r.employeeId} xs={24} sm={12} md={8} lg={6}>
                    <PresenceCard row={r} />
                  </Col>
                ))}
              </Row>
            </Card>
          ))}
        </Space>
      )}

      {view === 'table' && snap && filtered.length > 0 && (
        <Card size="small">
          <table style={{ width: '100%', fontSize: 13 }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid #eee' }}>
                <th style={{ padding: 6 }}>Employee</th>
                <th style={{ padding: 6 }}>Department</th>
                <th style={{ padding: 6 }}>State</th>
                <th style={{ padding: 6 }}>Since</th>
                <th style={{ padding: 6 }}>Note</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.employeeId} style={{ borderBottom: '1px solid #f5f5f5' }}>
                  <td style={{ padding: 6 }}>
                    <Text strong>{r.employeeName}</Text>{' '}
                    <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
                  </td>
                  <td style={{ padding: 6 }}>{r.department ?? '—'}</td>
                  <td style={{ padding: 6 }}>
                    <Tag color={STATE_COLOR[r.state]}>{STATE_LABEL[r.state]}</Tag>
                  </td>
                  <td style={{ padding: 6 }}>
                    {r.since ? dayjs(r.since).format('HH:mm:ss') : <Text type="secondary">—</Text>}
                  </td>
                  <td style={{ padding: 6 }}>{r.note ?? <Text type="secondary">—</Text>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <Paragraph type="secondary" style={{ marginTop: 24, fontSize: 11 }}>
        Presence is derived from approved leave / business trips and today's attendance
        events. Priority order: ON_LEAVE → ON_TRIP → IN_OFFICE → OFFLINE → NOT_SCHEDULED.
      </Paragraph>
    </div>
  )
}

function PresenceCard({ row }: { row: PresenceRow }) {
  return (
    <Card
      size="small"
      style={{ borderLeft: `4px solid ${STATE_COLOR[row.state]}` }}
      hoverable
    >
      <Space direction="vertical" size={2} style={{ width: '100%' }}>
        <Tooltip title={row.employeeNo}>
          <Text strong style={{ fontSize: 13 }}>{row.employeeName}</Text>
        </Tooltip>
        <Space size={6}>
          <Badge color={STATE_COLOR[row.state]} />
          <Text style={{ fontSize: 12, color: STATE_COLOR[row.state] }}>{STATE_LABEL[row.state]}</Text>
        </Space>
        {row.since && (
          <Text type="secondary" style={{ fontSize: 11 }}>
            since {dayjs(row.since).format('HH:mm')}
          </Text>
        )}
        {row.note && (
          <Text type="secondary" style={{ fontSize: 11, fontStyle: 'italic' }}>
            {row.note}
          </Text>
        )}
      </Space>
    </Card>
  )
}
