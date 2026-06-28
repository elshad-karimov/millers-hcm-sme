import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Drawer,
  Form,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined, EditOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons'
import { attendanceApi, type AttendanceDevice, type DeviceRequest } from '../api/attendance'

const { Title, Text } = Typography

export function DeviceMasterPage() {
  const [devices, setDevices] = useState<AttendanceDevice[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<AttendanceDevice | null>(null)
  const [form] = Form.useForm<DeviceRequest>()

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi.devices()
      .then(setDevices)
      .catch(() => message.error('Failed to load devices'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function openCreate() {
    setEditing(null)
    form.resetFields()
    setDrawerOpen(true)
  }

  function openEdit(d: AttendanceDevice) {
    setEditing(d)
    form.setFieldsValue(d as DeviceRequest)
    setDrawerOpen(true)
  }

  function closeDrawer() {
    setDrawerOpen(false)
    setEditing(null)
    form.resetFields()
  }

  function handleSubmit(values: DeviceRequest) {
    setSaving(true)
    const call = editing
      ? attendanceApi.updateDevice(editing.id, values)
      : attendanceApi.createDevice(values)
    call
      .then(() => {
        message.success(editing ? 'Device updated' : 'Device created')
        closeDrawer()
        load()
      })
      .catch(() => message.error('Failed to save device'))
      .finally(() => setSaving(false))
  }

  function handleDeactivate(id: string) {
    attendanceApi.deactivateDevice(id)
      .then(() => {
        message.success('Device deactivated')
        load()
      })
      .catch(() => message.error('Failed to deactivate'))
  }

  function handleActivate(id: string) {
    attendanceApi.activateDevice(id)
      .then(() => {
        message.success('Device activated')
        load()
      })
      .catch(() => message.error('Failed to activate'))
  }

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 120,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'name',
    },
    {
      title: 'Type',
      dataIndex: 'deviceType',
      width: 140,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'IP Address',
      dataIndex: 'ipAddress',
      width: 140,
    },
    {
      title: 'Serial Number',
      dataIndex: 'serialNumber',
      width: 160,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Last Seen',
      dataIndex: 'lastSeenAt',
      width: 160,
      render: (v: string | undefined) => v ? new Date(v).toLocaleString() : '-',
    },
    {
      title: '',
      width: 120,
      render: (_: unknown, r: AttendanceDevice) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
          {r.active ? (
            <Popconfirm title="Deactivate this device?" onConfirm={() => handleDeactivate(r.id)}>
              <Button size="small" danger icon={<StopOutlined />} />
            </Popconfirm>
          ) : (
            <Button size="small" icon={<CheckOutlined />} onClick={() => handleActivate(r.id)} />
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>Attendance Devices</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          New Device
        </Button>
      </div>

      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Biometric devices, card readers, and other attendance capture hardware.
      </Text>

      <Table
        rowKey="id"
        dataSource={devices}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Drawer
        title={editing ? `Edit Device — ${editing.code}` : 'New Attendance Device'}
        open={drawerOpen}
        onClose={closeDrawer}
        width={560}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={closeDrawer}>Cancel</Button>
            <Button type="primary" onClick={() => form.submit()} loading={saving}>
              {editing ? 'Save' : 'Create'}
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="Code" name="code" rules={[{ required: true }]}>
            <Input placeholder="BIO-001" />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input placeholder="Main Entrance Biometric" />
          </Form.Item>
          <Form.Item label="Device Type" name="deviceType" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="BIOMETRIC">Biometric</Select.Option>
              <Select.Option value="CARD_READER">Card Reader</Select.Option>
              <Select.Option value="MOBILE_APP">Mobile App</Select.Option>
              <Select.Option value="WEB_PORTAL">Web Portal</Select.Option>
              <Select.Option value="OTHER">Other</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="Location ID" name="locationId">
            <Input placeholder="Optional" />
          </Form.Item>
          <Form.Item label="IP Address" name="ipAddress">
            <Input placeholder="192.168.1.100" />
          </Form.Item>
          <Form.Item label="Serial Number" name="serialNumber">
            <Input placeholder="SN123456789" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
