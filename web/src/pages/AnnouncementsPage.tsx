import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  DatePicker,
  Select,
  Typography,
  App as AntdApp,
  Switch,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  announcementsApi,
  type Announcement,
  type AnnouncementAudience,
} from '../api/announcements'

const { TextArea } = Input

export default function AnnouncementsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [announcements, setAnnouncements] = useState<Announcement[]>([])
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await announcementsApi.list()
      setAnnouncements(res.data)
    } catch (err: any) {
      message.error('Failed to load announcements: ' + (err.message || ''))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const handleCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ audience: 'ALL', active: true })
    setShowModal(true)
  }

  const handleEdit = (ann: Announcement) => {
    setEditing(ann)
    form.setFieldsValue({
      ...ann,
      publishFrom: ann.publishFrom ? dayjs(ann.publishFrom) : null,
      publishTo: ann.publishTo ? dayjs(ann.publishTo) : null,
    })
    setShowModal(true)
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      const dto = {
        ...values,
        publishFrom: values.publishFrom ? values.publishFrom.format('YYYY-MM-DD') : null,
        publishTo: values.publishTo ? values.publishTo.format('YYYY-MM-DD') : null,
      }
      if (editing) {
        await announcementsApi.update(editing.id, { ...editing, ...dto })
        message.success('Announcement updated')
      } else {
        await announcementsApi.create(dto)
        message.success('Announcement created')
      }
      setShowModal(false)
      form.resetFields()
      load()
    } catch (err: any) {
      message.error('Failed to save: ' + (err.message || ''))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await announcementsApi.delete(id)
      message.success('Announcement deleted')
      load()
    } catch (err: any) {
      message.error('Failed to delete: ' + (err.message || ''))
    }
  }

  const columns: ColumnsType<Announcement> = [
    {
      title: 'Title',
      dataIndex: 'title',
    },
    {
      title: 'Publish From',
      dataIndex: 'publishFrom',
      width: 120,
      render: (d) => (d ? dayjs(d).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Publish To',
      dataIndex: 'publishTo',
      width: 120,
      render: (d) => (d ? dayjs(d).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Audience',
      dataIndex: 'audience',
      width: 120,
      render: (a: AnnouncementAudience) => <Tag>{a}</Tag>,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (a) => <Tag color={a ? 'green' : 'default'}>{a ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      width: 150,
      render: (_, rec) => (
        <Space size="small">
          <Button size="small" onClick={() => handleEdit(rec)}>Edit</Button>
          <Button size="small" danger onClick={() => handleDelete(rec.id)}>Delete</Button>
        </Space>
      ),
    },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Announcements</Typography.Title>}>
      <Button type="primary" onClick={handleCreate} style={{ marginBottom: 16 }}>
        New Announcement
      </Button>
      <Table
        loading={loading}
        dataSource={announcements}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={editing ? 'Edit Announcement' : 'New Announcement'}
        open={showModal}
        onCancel={() => {
          setShowModal(false)
          form.resetFields()
        }}
        onOk={handleSave}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="Title"
            rules={[{ required: true, message: 'Please enter title' }]}
          >
            <Input maxLength={300} />
          </Form.Item>
          <Form.Item name="body" label="Body">
            <TextArea rows={4} maxLength={4000} />
          </Form.Item>
          <Form.Item
            name="publishFrom"
            label="Publish From"
            rules={[{ required: true, message: 'Please select publish from date' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="publishTo" label="Publish To (optional)">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="audience"
            label="Audience"
            rules={[{ required: true, message: 'Please select audience' }]}
          >
            <Select>
              <Select.Option value="ALL">All Employees</Select.Option>
              <Select.Option value="DEPARTMENT">Department (specify below)</Select.Option>
              <Select.Option value="LOCATION">Location (specify below)</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="audienceRef" label="Audience Ref (UUID)">
            <Input placeholder="Enter org_unit_id or work_location_id" />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
