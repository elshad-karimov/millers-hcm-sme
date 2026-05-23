import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  performanceApi,
  type CycleStatus,
  type CycleType,
  type ReviewCycle,
} from '../api/performance'
import { useAuth } from '../auth/AuthContext'

const STATUS_OPTIONS: CycleStatus[] = ['DRAFT', 'OPEN', 'CALIBRATING', 'CLOSED', 'COMPLETED']

const STATUS_COLOR: Record<CycleStatus, string> = {
  DRAFT: 'default',
  OPEN: 'green',
  CALIBRATING: 'gold',
  CLOSED: 'cyan',
  COMPLETED: 'blue',
}

const TYPE_COLOR: Record<CycleType, string> = {
  ANNUAL: 'geekblue',
  MID_YEAR: 'purple',
  QUARTERLY: 'magenta',
  PROBATION: 'orange',
  PROJECT: 'volcano',
  ADHOC: 'default',
}

export function ReviewCyclesPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [rows, setRows] = useState<ReviewCycle[]>([])
  const [status, setStatus] = useState<CycleStatus | undefined>()
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    performanceApi
      .cycles(status)
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load cycles'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status])

  const onTransition = async (c: ReviewCycle, next: CycleStatus) => {
    modal.confirm({
      title: `Move "${c.code}" to ${next}?`,
      onOk: async () => {
        try {
          await performanceApi.changeCycleStatus(c.id, next)
          message.success(`Cycle ${c.code} → ${next}`)
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Transition failed',
          )
        }
      },
    })
  }

  const columns: ColumnsType<ReviewCycle> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'cycleType',
      width: 110,
      render: (t: CycleType) => <Tag color={TYPE_COLOR[t]}>{t.replace(/_/g, ' ')}</Tag>,
    },
    { title: 'Start', dataIndex: 'periodStart', width: 110 },
    { title: 'End', dataIndex: 'periodEnd', width: 110 },
    { title: 'Self due', dataIndex: 'selfReviewDue', width: 110 },
    { title: 'Mgr due', dataIndex: 'managerReviewDue', width: 110 },
    { title: 'Final due', dataIndex: 'finalDue', width: 110 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: CycleStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 280,
      render: (_, r) => (
        <Space size={4} wrap>
          {canEdit && (
            <Button size="small" onClick={() => navigate(`/performance/cycles/${r.id}/edit`)}>
              Edit
            </Button>
          )}
          {canEdit && r.status === 'DRAFT' && (
            <Button size="small" type="primary" onClick={() => onTransition(r, 'OPEN')}>
              Open
            </Button>
          )}
          {canEdit && r.status === 'OPEN' && (
            <Button size="small" onClick={() => onTransition(r, 'CALIBRATING')}>
              Calibrate
            </Button>
          )}
          {canEdit && (r.status === 'OPEN' || r.status === 'CALIBRATING') && (
            <Button size="small" onClick={() => onTransition(r, 'CLOSED')}>
              Close
            </Button>
          )}
          {canEdit && r.status === 'CLOSED' && (
            <Button size="small" type="primary" onClick={() => onTransition(r, 'COMPLETED')}>
              Complete
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Review cycles
        </Typography.Title>
      }
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/performance/cycles/new')}>
            New cycle
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="All statuses"
          style={{ width: 180 }}
          options={STATUS_OPTIONS.map((s) => ({ value: s, label: s }))}
          value={status}
          onChange={setStatus}
        />
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />
    </Card>
  )
}
