// M477 — Pulse schedule CRUD (HR_ADMIN).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { pulseScheduleApi, type PulseSchedule, type PulseFrequency } from '../../api/engagement'
import { surveysAdminApi, type TemplateResponse } from '../../api/surveys'

const FREQUENCIES: PulseFrequency[] = ['WEEKLY', 'BIWEEKLY', 'MONTHLY']

export function PulseSchedulesPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<PulseSchedule[]>([])
  const [templates, setTemplates] = useState<TemplateResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([pulseScheduleApi.listAll(false), surveysAdminApi.listTemplates(true)])
      .then(([schedules, templates]) => {
        setRows(schedules)
        setTemplates(templates)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ active: true })
    setEditingId(null)
    setModalOpen(true)
  }

  const openEdit = (schedule: PulseSchedule) => {
    form.setFieldsValue(schedule)
    setEditingId(schedule.id!)
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await pulseScheduleApi.update(editingId, values)
        message.success('Schedule updated')
      } else {
        await pulseScheduleApi.create(values)
        message.success('Schedule created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: 'Delete schedule?',
      onOk: async () => {
        try {
          await pulseScheduleApi.delete(id)
          message.success('Schedule deleted')
          load()
        } catch (e: any) {
          message.error(e?.response?.data?.message ?? 'Delete failed')
        }
      },
    })
  }

  const columns: ColumnsType<PulseSchedule> = [
    {
      title: 'Template',
      dataIndex: 'surveyTemplateId',
      render: (id: string) => {
        const tpl = templates.find((t) => t.id === id)
        return tpl?.name || id
      },
    },
    { title: 'Frequency', dataIndex: 'frequency', width: 120 },
    {
      title: 'Day of Week',
      dataIndex: 'dayOfWeek',
      width: 120,
      render: (d) => (d ? `${d} (Mon=1)` : '—'),
    },
    { title: 'Day of Month', dataIndex: 'dayOfMonth', width: 120, render: (d) => d ?? '—' },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (a: boolean) => <Tag color={a ? 'green' : 'default'}>{a ? 'Yes' : 'No'}</Tag>,
    },
    { title: 'Last Run', dataIndex: 'lastRunAt', width: 180, render: (d) => d ?? '—' },
    {
      title: 'Actions',
      width: 150,
      render: (_, r) => (
        <Space>
          <a onClick={() => openEdit(r)}>Edit</a>
          <a onClick={() => handleDelete(r.id!)} style={{ color: 'red' }}>
            Delete
          </a>
        </Space>
      ),
    },
  ]

  return (
    <Card title="Pulse Schedules" extra={<Button type="primary" onClick={openCreate}>Create Schedule</Button>}>
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} />

      <Modal
        title={editingId ? 'Edit Schedule' : 'Create Schedule'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="surveyTemplateId" label="Survey Template" rules={[{ required: true }]}>
            <Select>
              {templates.map((t) => (
                <Select.Option key={t.id} value={t.id}>
                  {t.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="frequency" label="Frequency" rules={[{ required: true }]}>
            <Select>
              {FREQUENCIES.map((f) => (
                <Select.Option key={f} value={f}>
                  {f}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="dayOfWeek" label="Day of Week (1=Mon..7=Sun)">
            <InputNumber min={1} max={7} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="dayOfMonth" label="Day of Month (1-28)">
            <InputNumber min={1} max={28} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
