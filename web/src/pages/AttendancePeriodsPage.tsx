import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  message,
  Modal,
  Input,
  Table,
  Tag,
  Typography,
  Space,
} from 'antd'
import { LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { attendanceApi, type AttendancePeriod } from '../api/attendance'

const { Title, Text } = Typography
const { TextArea } = Input

export function AttendancePeriodsPage() {
  const [periods, setPeriods] = useState<AttendancePeriod[]>([])
  const [loading, setLoading] = useState(false)
  const [lockModalOpen, setLockModalOpen] = useState(false)
  const [selectedPeriod, setSelectedPeriod] = useState<{ year: number; month: number } | null>(null)
  const [notes, setNotes] = useState('')
  const [processing, setProcessing] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi.periods()
      .then(setPeriods)
      .catch(() => message.error('Failed to load periods'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function openLockModal() {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    setSelectedPeriod({ year, month })
    setNotes('')
    setLockModalOpen(true)
  }

  function closeLockModal() {
    setLockModalOpen(false)
    setSelectedPeriod(null)
    setNotes('')
  }

  function handleLock() {
    if (!selectedPeriod) return
    setProcessing(true)
    attendanceApi.lockPeriod(selectedPeriod.year, selectedPeriod.month, notes || undefined)
      .then(() => {
        message.success('Period locked')
        closeLockModal()
        load()
      })
      .catch(() => message.error('Failed to lock period'))
      .finally(() => setProcessing(false))
  }

  function handleUnlock(year: number, month: number) {
    setProcessing(true)
    attendanceApi.unlockPeriod(year, month)
      .then(() => {
        message.success('Period unlocked')
        load()
      })
      .catch(() => message.error('Failed to unlock period'))
      .finally(() => setProcessing(false))
  }

  const columns = [
    {
      title: 'Year',
      dataIndex: 'year',
      width: 100,
    },
    {
      title: 'Month',
      dataIndex: 'month',
      width: 100,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => {
        const color = v === 'OPEN' ? 'green' : v === 'LOCKED' ? 'orange' : 'red'
        return <Tag color={color}>{v}</Tag>
      },
    },
    {
      title: 'Employee Count',
      dataIndex: 'employeeCountAtLock',
      width: 140,
      render: (v: number | undefined) => v ?? '-',
    },
    {
      title: 'Locked By',
      dataIndex: 'lockedBy',
      width: 160,
      render: (v: string | undefined) => v ? <Text code>{v}</Text> : null,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      ellipsis: true,
    },
    {
      title: '',
      width: 100,
      render: (_: unknown, r: AttendancePeriod) =>
        r.status === 'LOCKED' ? (
          <Button
            size="small"
            icon={<UnlockOutlined />}
            onClick={() => handleUnlock(r.year, r.month)}
            loading={processing}
          >
            Unlock
          </Button>
        ) : null,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>Attendance Periods</Title>
        <Button type="primary" icon={<LockOutlined />} onClick={openLockModal}>
          Lock Current Period
        </Button>
      </div>

      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Lock attendance periods to prevent further changes. OPEN = editable, LOCKED = read-only, CLOSED = finalized.
      </Text>

      <Table
        rowKey="id"
        dataSource={periods}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="Lock Attendance Period"
        open={lockModalOpen}
        onCancel={closeLockModal}
        onOk={handleLock}
        confirmLoading={processing}
        okText="Lock Period"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <Text strong>Period:</Text> {selectedPeriod?.year}-{selectedPeriod?.month?.toString().padStart(2, '0')}
          </div>
          <TextArea
            rows={3}
            placeholder="Optional notes (e.g., 'Monthly closing for January 2026')"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Space>
      </Modal>
    </div>
  )
}
