import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Switch,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { CheckOutlined, EditOutlined } from '@ant-design/icons'
import {
  attendanceApi,
  type AttendanceException,
  type ExceptionConfig,
} from '../api/attendance'

const { Title, Text } = Typography
const { TextArea } = Input

const SEVERITY_COLORS: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'blue',
}

export function AttendanceExceptionsPage() {
  const [exceptions, setExceptions] = useState<AttendanceException[]>([])
  const [configs, setConfigs] = useState<ExceptionConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [configDrawerOpen, setConfigDrawerOpen] = useState(false)
  const [editingConfig, setEditingConfig] = useState<ExceptionConfig | null>(null)
  const [resolveModalOpen, setResolveModalOpen] = useState(false)
  const [selectedEx, setSelectedEx] = useState<AttendanceException | null>(null)
  const [resolveNotes, setResolveNotes] = useState('')
  const [processing, setProcessing] = useState(false)
  const [form] = Form.useForm<ExceptionConfig>()

  const loadExceptions = useCallback(() => {
    setLoading(true)
    attendanceApi.exceptions('OPEN')
      .then(setExceptions)
      .catch(() => message.error('Failed to load exceptions'))
      .finally(() => setLoading(false))
  }, [])

  const loadConfigs = useCallback(() => {
    setLoading(true)
    attendanceApi.exceptionConfigs()
      .then(setConfigs)
      .catch(() => message.error('Failed to load configs'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadExceptions()
    loadConfigs()
  }, [loadExceptions, loadConfigs])

  function handleAcknowledge(id: string) {
    attendanceApi.acknowledgeException(id)
      .then(() => {
        message.success('Exception acknowledged')
        loadExceptions()
      })
      .catch(() => message.error('Failed to acknowledge'))
  }

  function openResolveModal(ex: AttendanceException) {
    setSelectedEx(ex)
    setResolveNotes('')
    setResolveModalOpen(true)
  }

  function closeResolveModal() {
    setResolveModalOpen(false)
    setSelectedEx(null)
    setResolveNotes('')
  }

  function handleResolve() {
    if (!selectedEx) return
    setProcessing(true)
    attendanceApi.resolveException(selectedEx.id, resolveNotes || undefined)
      .then(() => {
        message.success('Exception resolved')
        closeResolveModal()
        loadExceptions()
      })
      .catch(() => message.error('Failed to resolve'))
      .finally(() => setProcessing(false))
  }

  function openConfigDrawer(config: ExceptionConfig) {
    setEditingConfig(config)
    form.setFieldsValue(config)
    setConfigDrawerOpen(true)
  }

  function closeConfigDrawer() {
    setConfigDrawerOpen(false)
    setEditingConfig(null)
    form.resetFields()
  }

  function handleConfigSubmit(values: ExceptionConfig) {
    if (!editingConfig) return
    setProcessing(true)
    attendanceApi.updateExceptionConfig(editingConfig.id, values)
      .then(() => {
        message.success('Config updated')
        closeConfigDrawer()
        loadConfigs()
      })
      .catch(() => message.error('Failed to update config'))
      .finally(() => setProcessing(false))
  }

  const exceptionColumns = [
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      width: 140,
      render: (v: string) => <Text code>{v.substring(0, 8)}...</Text>,
    },
    {
      title: 'Date',
      dataIndex: 'workDate',
      width: 110,
    },
    {
      title: 'Type',
      dataIndex: 'exceptionType',
      width: 140,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      width: 100,
      render: (v: string) => <Tag color={SEVERITY_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: 'Threshold',
      dataIndex: 'thresholdMinutes',
      width: 100,
      render: (v: number) => `${v} min`,
    },
    {
      title: 'Actual',
      dataIndex: 'actualMinutes',
      width: 100,
      render: (v: number) => `${v} min`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => {
        const color = v === 'OPEN' ? 'orange' : v === 'ACKNOWLEDGED' ? 'blue' : 'green'
        return <Tag color={color}>{v}</Tag>
      },
    },
    {
      title: '',
      width: 160,
      render: (_: unknown, r: AttendanceException) => (
        <Space>
          {r.status === 'OPEN' && (
            <Button size="small" icon={<CheckOutlined />} onClick={() => handleAcknowledge(r.id)}>
              Acknowledge
            </Button>
          )}
          {r.status !== 'RESOLVED' && (
            <Button size="small" type="primary" onClick={() => openResolveModal(r)}>
              Resolve
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const configColumns = [
    {
      title: 'Type',
      dataIndex: 'exceptionType',
      width: 200,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      width: 120,
      render: (v: string) => <Tag color={SEVERITY_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: 'Threshold (min)',
      dataIndex: 'thresholdMinutes',
      width: 140,
    },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      width: 100,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Auto Notify',
      dataIndex: 'autoNotify',
      width: 120,
      render: (v: boolean) => <Tag color={v ? 'blue' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: '',
      width: 80,
      render: (_: unknown, r: ExceptionConfig) => (
        <Button size="small" icon={<EditOutlined />} onClick={() => openConfigDrawer(r)} />
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ marginBottom: 16 }}>Attendance Exceptions</Title>

      <Tabs
        items={[
          {
            key: 'exceptions',
            label: 'Exceptions',
            children: (
              <>
                <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                  Open exceptions that exceed configured thresholds (lateness, early leave, missing clock-out, etc.).
                </Text>
                <Table
                  rowKey="id"
                  dataSource={exceptions}
                  columns={exceptionColumns}
                  loading={loading}
                  size="small"
                  pagination={{ pageSize: 20 }}
                />
              </>
            ),
          },
          {
            key: 'configuration',
            label: 'Configuration',
            children: (
              <>
                <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                  Configure exception thresholds and severity levels.
                </Text>
                <Table
                  rowKey="id"
                  dataSource={configs}
                  columns={configColumns}
                  loading={loading}
                  size="small"
                  pagination={false}
                />
              </>
            ),
          },
        ]}
      />

      <Modal
        title="Resolve Exception"
        open={resolveModalOpen}
        onCancel={closeResolveModal}
        onOk={handleResolve}
        confirmLoading={processing}
        okText="Resolve"
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>Employee:</Text> {selectedEx?.employeeId.substring(0, 8)}...
          <br />
          <Text strong>Type:</Text> {selectedEx?.exceptionType}
          <br />
          <Text strong>Severity:</Text>{' '}
          {selectedEx && <Tag color={SEVERITY_COLORS[selectedEx.severity]}>{selectedEx.severity}</Tag>}
        </div>
        <TextArea
          rows={3}
          placeholder="Resolution notes (optional)"
          value={resolveNotes}
          onChange={(e) => setResolveNotes(e.target.value)}
        />
      </Modal>

      <Drawer
        title={`Edit Config — ${editingConfig?.exceptionType}`}
        open={configDrawerOpen}
        onClose={closeConfigDrawer}
        width={480}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={closeConfigDrawer}>Cancel</Button>
            <Button type="primary" onClick={() => form.submit()} loading={processing}>
              Save
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={handleConfigSubmit}>
          <Form.Item label="Exception Type" name="exceptionType">
            <Input disabled />
          </Form.Item>
          <Form.Item label="Severity" name="severity">
            <Select>
              <Select.Option value="HIGH">High</Select.Option>
              <Select.Option value="MEDIUM">Medium</Select.Option>
              <Select.Option value="LOW">Low</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="Threshold (minutes)" name="thresholdMinutes">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Enabled" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Auto Notify" name="autoNotify" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
