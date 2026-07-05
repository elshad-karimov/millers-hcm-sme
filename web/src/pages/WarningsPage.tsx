import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { api } from '../api/client'
import dayjs from 'dayjs'

type WarningLevel = 'VERBAL' | 'WRITTEN' | 'FINAL' | 'TERMINATION'

interface WarningRecord {
  id: string
  employeeId: string
  employeeName?: string
  employeeNo?: string
  level: WarningLevel
  reason: string
  issuedAt: string
  expiresAt?: string
  acknowledgedAt?: string
  disciplinaryActionId?: string
  attachmentId?: string
  issuedBy: string
}

const WARNING_LEVELS: WarningLevel[] = ['VERBAL', 'WRITTEN', 'FINAL', 'TERMINATION']

const WARNING_LEVEL_COLOR: Record<WarningLevel, string> = {
  VERBAL: 'blue',
  WRITTEN: 'orange',
  FINAL: 'red',
  TERMINATION: 'error',
}

const WARNING_EXPIRY_MONTHS: Record<WarningLevel, number> = {
  VERBAL: 3,
  WRITTEN: 6,
  FINAL: 12,
  TERMINATION: 0,
}

export function WarningsPage() {
  const { message } = AntdApp.useApp()
  const [warnings, setWarnings] = useState<WarningRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [issueOpen, setIssueOpen] = useState(false)
  const [employeeFilter, setEmployeeFilter] = useState<string>()

  const [form] = Form.useForm()

  const fetchWarnings = async () => {
    setLoading(true)
    try {
      if (employeeFilter) {
        const { data } = await api.get(`/api/er/warnings/employees/${employeeFilter}`)
        setWarnings(data)
      } else {
        // If no specific employee, show all (or implement a general list endpoint)
        setWarnings([])
      }
    } catch (err: any) {
      message.error(err.message || 'Failed to load warnings')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (employeeFilter) {
      fetchWarnings()
    }
  }, [employeeFilter])

  const handleIssue = async (values: any) => {
    try {
      await api.post('/api/er/warnings', {
        employeeId: values.employeeId,
        level: values.level,
        reason: values.reason,
        disciplinaryActionId: values.disciplinaryActionId || null,
        attachmentId: values.attachmentId || null,
      })
      message.success('Warning issued')
      setIssueOpen(false)
      form.resetFields()
      fetchWarnings()
    } catch (err: any) {
      message.error(err.message || 'Failed to issue warning')
    }
  }

  const columns: ColumnsType<WarningRecord> = [
    {
      title: 'Employee',
      key: 'employee',
      width: 200,
      render: (_, rec) =>
        rec.employeeName ? `${rec.employeeName} (${rec.employeeNo})` : rec.employeeId,
    },
    {
      title: 'Level',
      dataIndex: 'level',
      key: 'level',
      width: 140,
      render: (level: WarningLevel) => <Tag color={WARNING_LEVEL_COLOR[level]}>{level}</Tag>,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
    },
    {
      title: 'Issued',
      dataIndex: 'issuedAt',
      key: 'issuedAt',
      width: 140,
      render: (val) => (val ? dayjs(val).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Expires',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 140,
      render: (val) => (val ? dayjs(val).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Acknowledged',
      dataIndex: 'acknowledgedAt',
      key: 'acknowledgedAt',
      width: 140,
      render: (val) =>
        val ? (
          <Tag color="green">{dayjs(val).format('YYYY-MM-DD')}</Tag>
        ) : (
          <Tag color="orange">Pending</Tag>
        ),
    },
  ]

  return (
    <Card
      title="Warnings"
      extra={
        <Space>
          <Input
            placeholder="Filter by Employee ID"
            style={{ width: 240 }}
            value={employeeFilter}
            onChange={(e) => setEmployeeFilter(e.target.value)}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setIssueOpen(true)}>
            Issue Warning
          </Button>
        </Space>
      }
    >
      <Table
        dataSource={warnings}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="Issue Warning"
        open={issueOpen}
        onCancel={() => {
          setIssueOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleIssue}>
          <Form.Item
            name="employeeId"
            label="Employee ID"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="Employee UUID" />
          </Form.Item>
          <Form.Item
            name="level"
            label="Warning Level"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select placeholder="Select level">
              {WARNING_LEVELS.map((lv) => (
                <Select.Option key={lv} value={lv}>
                  <Space>
                    <Tag color={WARNING_LEVEL_COLOR[lv]}>{lv}</Tag>
                    {WARNING_EXPIRY_MONTHS[lv] > 0 && (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        (expires in {WARNING_EXPIRY_MONTHS[lv]} months)
                      </Typography.Text>
                    )}
                  </Space>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={4} placeholder="Reason for warning" />
          </Form.Item>
          <Form.Item name="disciplinaryActionId" label="Disciplinary Action ID (optional)">
            <Input placeholder="UUID" />
          </Form.Item>
          <Form.Item name="attachmentId" label="Attachment ID (optional)">
            <Input placeholder="UUID" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
