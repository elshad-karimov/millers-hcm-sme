import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { attendanceApi, type WorkSchedule } from '../api/attendance'
import { useAuth } from '../auth/AuthContext'

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function renderWorkDays(bits: string) {
  return DAY_LABELS.map((d, i) => (
    <Tag key={d} color={bits.charAt(i) === '1' ? 'geekblue' : 'default'} style={{ margin: 2 }}>
      {d}
    </Tag>
  ))
}

export function AttendanceSchedulesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'HR_SPECIALIST')

  const [rows, setRows] = useState<WorkSchedule[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    attendanceApi
      .schedules()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load schedules'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<WorkSchedule> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'scheduleType',
      render: (v: string) => <Tag>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Hours',
      render: (_, r) => `${r.workStart.slice(0, 5)} – ${r.workEnd.slice(0, 5)}`,
      width: 130,
    },
    {
      title: 'Break / Grace',
      render: (_, r) => `${r.breakMinutes}m / ${r.gracePeriodMinutes}m`,
      width: 130,
    },
    {
      title: 'Days',
      dataIndex: 'workDays',
      render: (v: string) => <Space size={0}>{renderWorkDays(v)}</Space>,
    },
    {
      title: 'Status',
      dataIndex: 'active',
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Disabled'}</Tag>
      ),
      width: 90,
    },
    canEdit
      ? {
          title: '',
          width: 180,
          render: (_, r) => (
            <Space>
              <Button size="small" onClick={() => navigate(`/attendance/schedules/${r.id}/edit`)}>
                Edit
              </Button>
              <Button
                size="small"
                onClick={() => navigate(`/attendance/schedules/${r.id}/assign`)}
              >
                Assign
              </Button>
            </Space>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Work schedules</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/attendance/schedules/new')}>
            New schedule
          </Button>
        )
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />
    </Card>
  )
}
