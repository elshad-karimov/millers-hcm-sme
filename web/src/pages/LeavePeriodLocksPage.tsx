import { useEffect, useState } from 'react'
import {
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import { PlusOutlined, LockOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi, type LeavePeriodLock, type LeaveType } from '../api/leave'

interface FormValues {
  periodRange: [dayjs.Dayjs, dayjs.Dayjs]
  leaveTypeId?: string
  reason?: string
}

export function LeavePeriodLocksPage() {
  const { message } = AntdApp.useApp()
  const [locks, setLocks] = useState<LeavePeriodLock[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LeavePeriodLock | null>(null)
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const [l, t] = await Promise.all([leaveApi.periodLocks(), leaveApi.types(false)])
      setLocks(l)
      setTypes(t)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const openNew = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (lock: LeavePeriodLock) => {
    setEditing(lock)
    form.setFieldsValue({
      periodRange: [dayjs(lock.periodStart), dayjs(lock.periodEnd)],
      leaveTypeId: lock.leaveTypeId ?? undefined,
      reason: lock.reason ?? undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      const payload = {
        periodStart: v.periodRange[0].format('YYYY-MM-DD'),
        periodEnd: v.periodRange[1].format('YYYY-MM-DD'),
        leaveTypeId: v.leaveTypeId ?? null,
        reason: v.reason,
      }
      if (editing) {
        await leaveApi.updatePeriodLock(editing.id, payload)
      } else {
        await leaveApi.createPeriodLock(payload)
      }
      message.success(editing ? 'Period lock updated' : 'Period locked')
      setModalOpen(false)
      load()
    } catch {
      message.error('Save failed')
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (id: string) => {
    try {
      await leaveApi.deletePeriodLock(id)
      message.success('Period lock removed')
      load()
    } catch {
      message.error('Remove failed')
    }
  }

  const typeMap = new Map(types.map((t) => [t.id, t.name]))

  const columns: ColumnsType<LeavePeriodLock> = [
    {
      title: 'Period',
      render: (_, r) => `${r.periodStart} → ${r.periodEnd}`,
    },
    {
      title: 'Leave type',
      render: (_, r) => r.leaveTypeId ? typeMap.get(r.leaveTypeId) ?? r.leaveTypeId : <Tag>All types</Tag>,
    },
    { title: 'Reason', dataIndex: 'reason' },
    { title: 'Locked by', dataIndex: 'lockedBy' },
    {
      title: 'Status',
      render: (_, r) => r.active ? <Tag color="red">Active</Tag> : <Tag>Inactive</Tag>,
    },
    {
      title: 'Actions',
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openEdit(r)}>Edit</Button>
          <Button
            size="small"
            danger
            onClick={() => Modal.confirm({
              title: 'Remove this period lock?',
              content: 'Employees will be able to submit/cancel leave for this period again.',
              onOk: () => onDelete(r.id),
            })}
          >
            Remove
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>
          <LockOutlined style={{ marginRight: 8 }} />
          Leave Period Locks
        </h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={openNew}>
          Lock Period
        </Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={locks}
        loading={loading}
        pagination={false}
      />

      <Modal
        title={editing ? 'Edit Period Lock' : 'Lock a Leave Period'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={onSave}
        confirmLoading={saving}
        okText="Save"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="periodRange"
            label="Locked period"
            rules={[{ required: true, message: 'Select a date range' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="leaveTypeId" label="Leave type (leave blank for all)">
            <Select
              allowClear
              placeholder="All leave types"
              options={types.map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` }))}
            />
          </Form.Item>
          <Form.Item name="reason" label="Reason">
            <Input.TextArea rows={2} placeholder="e.g. Payroll close Q2" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
