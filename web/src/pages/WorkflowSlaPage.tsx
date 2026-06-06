// M126 — HR dashboard listing all SLA-breached workflow instances.
//
// Read-only view; the scheduler does the work autonomously. Each row
// links to the underlying workflow instance so HR can investigate.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import { workflowSlaApi, type SlaBreachResponse } from '../api/workflowSla'

const { Title, Text, Paragraph } = Typography

export function WorkflowSlaPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<SlaBreachResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')

  const reload = () => {
    setLoading(true)
    workflowSlaApi.recentBreaches(200)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load breaches'))
      .finally(() => setLoading(false))
  }

  useEffect(reload, []) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = rows.filter((r) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      r.title?.toLowerCase().includes(q) ||
      r.definitionCode?.toLowerCase().includes(q) ||
      r.subjectModule?.toLowerCase().includes(q) ||
      r.notifiedTarget?.toLowerCase().includes(q)
    )
  })

  const severityColor = (h: number) => {
    if (h < 8) return 'gold'
    if (h < 48) return 'orange'
    return 'red'
  }

  const columns = [
    {
      title: 'Workflow',
      render: (_: unknown, r: SlaBreachResponse) => (
        <Space direction="vertical" size={0}>
          <Link to={`/approvals?instance=${r.instanceId}`}>
            <Text strong>{r.title ?? r.instanceId.slice(0, 8) + '…'}</Text>
          </Link>
          {r.definitionCode && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.definitionCode}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Subject',
      render: (_: unknown, r: SlaBreachResponse) => (
        <Text style={{ fontSize: 12 }}>
          {r.subjectModule ?? '—'} · {r.subjectEntity ?? '—'}
        </Text>
      ),
      width: 220,
    },
    {
      title: 'Step',
      dataIndex: 'stepIndex',
      render: (idx: number, r: SlaBreachResponse) => (
        <Space size={4} direction="vertical">
          <Text>Step {idx}</Text>
          {r.currentStepRole && (
            <Tag style={{ fontSize: 10 }}>{r.currentStepRole.replace('ROLE_', '')}</Tag>
          )}
        </Space>
      ),
      width: 130,
    },
    {
      title: 'Overdue',
      dataIndex: 'hoursOverdue',
      render: (h: number) => (
        <Tag color={severityColor(h)}>
          {Number(h).toFixed(2)}h
        </Tag>
      ),
      width: 110,
      sorter: (a: SlaBreachResponse, b: SlaBreachResponse) => a.hoursOverdue - b.hoursOverdue,
      defaultSortOrder: 'descend' as const,
    },
    {
      title: 'Breached at',
      dataIndex: 'breachedAt',
      render: (v: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{dayjs(v).format('YYYY-MM-DD HH:mm')}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{dayjs(v).format('HH:mm:ss')}</Text>
        </Space>
      ),
      width: 170,
    },
    {
      title: 'Action',
      dataIndex: 'actionTaken',
      render: (a: string) => <Tag>{a}</Tag>,
      width: 110,
    },
    {
      title: 'Notified',
      dataIndex: 'notifiedTarget',
      render: (t: string | null) => t ? <Text code style={{ fontSize: 11 }}>{t}</Text> : <Text type="secondary">—</Text>,
      width: 180,
    },
  ]

  const totalBreaches = rows.length
  const last24h = rows.filter((r) => dayjs(r.breachedAt).isAfter(dayjs().subtract(24, 'hour'))).length
  const severe = rows.filter((r) => r.hoursOverdue >= 48).length

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>Workflow SLA breaches</Title>
        <Button onClick={reload}>Refresh</Button>
      </Space>

      <Space size="middle" style={{ marginBottom: 16 }}>
        <Card size="small"><Statistic title="Total" value={totalBreaches} valueStyle={{ fontSize: 22 }} /></Card>
        <Card size="small"><Statistic title="In last 24h" value={last24h} valueStyle={{ fontSize: 22 }} /></Card>
        <Card size="small"><Statistic title="≥ 48h overdue" value={severe} valueStyle={{ fontSize: 22, color: '#cf1322' }} /></Card>
      </Space>

      <Card size="small">
        <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
          Workflow steps that have been waiting longer than their configured SLA.
          The scheduler records each breach exactly once and notifies the
          escalation target. Use the link to jump to the underlying workflow.
        </Paragraph>
        <Input.Search
          placeholder="Filter by title / definition / module"
          allowClear
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ marginBottom: 12, maxWidth: 360 }}
        />
        {!loading && filtered.length === 0
          ? <Empty description="No SLA breaches recorded." />
          : (
            <Table
              rowKey="id"
              size="small"
              loading={loading}
              columns={columns}
              dataSource={filtered}
              pagination={{ pageSize: 25 }}
            />
          )}
      </Card>
    </div>
  )
}
