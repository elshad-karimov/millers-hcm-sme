// M311 — Manager onboarding dashboard (Phase E.2).
// Shows the logged-in manager's direct reports who are currently onboarding:
// stat cards + progress table with overdue highlighting.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Badge,
  Card,
  Empty,
  Progress,
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
import { onboardingApi, type ManagerOnboardingView, type OnboardingRow } from '../api/onboarding'

const { Title, Text } = Typography

export function ManagerOnboardingPage() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<ManagerOnboardingView | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    onboardingApi.myTeam()
      .then(setData)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, []) // eslint-disable-line

  if (loading) return <Spin />

  if (!data) return null

  const cols: ColumnsType<OnboardingRow> = [
    {
      title: 'New hire',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.employeeName ?? '—'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
        </Space>
      ),
    },
    {
      title: 'Template',
      dataIndex: 'templateName',
      width: 160,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Join date',
      dataIndex: 'joinDate',
      width: 110,
      render: (v: string | null, r) => {
        if (!v) return '—'
        const days = r.daysToJoin
        const label = days == null ? ''
          : days > 0 ? ` (in ${days}d)`
          : days === 0 ? ' (today)'
          : ` (${Math.abs(days)}d ago)`
        return (
          <Tooltip title={label}>
            <span>{dayjs(v).format('DD MMM YYYY')}</span>
            {label && <Text type="secondary" style={{ fontSize: 11 }}>{label}</Text>}
          </Tooltip>
        )
      },
    },
    {
      title: 'Progress',
      width: 200,
      render: (_, r) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Progress
            percent={r.progressPercent}
            size="small"
            status={r.overdueTaskCount > 0 ? 'exception' : r.progressPercent === 100 ? 'success' : 'active'}
            style={{ margin: 0 }}
          />
          <Text type="secondary" style={{ fontSize: 11 }}>
            {r.completedTasks}/{r.totalTasks} tasks
            {r.requiredTotal > 0 && ` · ${r.requiredCompleted}/${r.requiredTotal} required`}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Overdue',
      width: 90,
      render: (_, r) => r.overdueTaskCount > 0
        ? <Badge count={r.overdueTaskCount} color="red" />
        : <Tag color="green" style={{ marginInlineEnd: 0 }}>On track</Tag>,
    },
    {
      title: 'Next due',
      dataIndex: 'nextDueDate',
      width: 110,
      render: (v: string | null) => {
        if (!v) return <Text type="secondary">—</Text>
        const isPast = dayjs(v).isBefore(dayjs(), 'day')
        return <Text type={isPast ? 'danger' : undefined}>{dayjs(v).format('DD MMM YYYY')}</Text>
      },
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ margin: 0 }}>My team's onboarding</Title>
        <Text type="secondary">
          Onboarding progress for {data.managerName
            ? <strong>{data.managerName}</strong>
            : 'you'}'s direct reports.
        </Text>
      </div>

      {/* Stat cards */}
      <Space wrap>
        <Card size="small" style={{ minWidth: 130 }}>
          <Statistic title="Direct reports" value={data.directReportCount} />
        </Card>
        <Card size="small" style={{ minWidth: 130 }}>
          <Statistic
            title="Onboarding now"
            value={data.activeOnboardingCount}
            valueStyle={{ color: data.activeOnboardingCount > 0 ? '#1677ff' : undefined }}
          />
        </Card>
        <Card size="small" style={{ minWidth: 130 }}>
          <Statistic
            title="With overdue tasks"
            value={data.overdueCount}
            valueStyle={{ color: data.overdueCount > 0 ? '#ff4d4f' : '#52c41a' }}
          />
        </Card>
      </Space>

      {/* Onboarding rows */}
      <Card>
        <Table
          rowKey="assignmentId"
          columns={cols}
          dataSource={data.rows}
          pagination={false}
          size="small"
          rowClassName={(r) => r.overdueTaskCount > 0 ? 'ant-table-row-danger' : ''}
          locale={{ emptyText: <Empty description="None of your direct reports are currently onboarding" /> }}
        />
      </Card>
    </Space>
  )
}
