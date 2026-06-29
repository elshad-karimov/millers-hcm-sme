import { useState } from 'react'
import {
  Button, Card, Col, Form, InputNumber, message, Modal, Popconfirm, Row, Select, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi } from '../api/leave'

interface Encashment {
  id: string
  employeeId: string
  leaveTypeId: string
  year: number
  daysEncashed: number
  dailyRate: number
  amount: number
  payrollBonusId: string | null
  status: string
  notes: string | null
  createdBy: string
  createdAt: string
}

const MONTHS = Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: new Date(0, i).toLocaleString('default', { month: 'long' }) }))

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v)

const statusColor: Record<string, string> = { PENDING: 'orange', PAID: 'green', REVERSED: 'default' }

export function LeaveEncashmentPage() {
  const [employeeId, setEmployeeId] = useState('')
  const [listing, setListing] = useState(false)
  const [encashments, setEncashments] = useState<Encashment[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const now = new Date()

  const load = async (empId: string) => {
    if (!empId.trim()) { message.warning('Enter employee ID'); return }
    setListing(true)
    try {
      const data = await leaveApi.listEncashments(empId.trim())
      setEncashments(data)
    } catch {
      message.error('Failed to load')
    } finally {
      setListing(false)
    }
  }

  const handleEncash = async (values: {
    leaveTypeId: string; year: number; days: number;
    payrollYear: number; payrollMonth: number; notes?: string
  }) => {
    setSubmitting(true)
    try {
      await leaveApi.createEncashment({ employeeId: employeeId.trim(), ...values })
      message.success('Encashment created')
      setModalOpen(false)
      form.resetFields()
      await load(employeeId)
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Failed')
    } finally {
      setSubmitting(false)
    }
  }

  const handleReverse = async (id: string) => {
    try {
      await leaveApi.reverseEncashment(id)
      message.success('Reversed')
      await load(employeeId)
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Reversal failed')
    }
  }

  const cols: ColumnsType<Encashment> = [
    { title: 'Year', dataIndex: 'year', width: 70 },
    { title: 'Leave Type', dataIndex: 'leaveTypeId', ellipsis: true },
    { title: 'Days', dataIndex: 'daysEncashed', render: v => fmt(v) },
    { title: 'Daily Rate', dataIndex: 'dailyRate', render: v => fmt(v) },
    { title: 'Amount', dataIndex: 'amount', render: v => <strong>{fmt(v)}</strong> },
    { title: 'Status', dataIndex: 'status', render: v => <Tag color={statusColor[v] || 'default'}>{v}</Tag> },
    { title: 'Payroll Bonus', dataIndex: 'payrollBonusId', render: v => v ? v.slice(0, 8) + '…' : '—' },
    { title: 'Notes', dataIndex: 'notes', ellipsis: true, render: v => v || '—' },
    {
      title: 'Action',
      render: (_, r) => r.status !== 'REVERSED'
        ? <Popconfirm title="Reverse this encashment?" onConfirm={() => handleReverse(r.id)}><Button size="small" danger>Reverse</Button></Popconfirm>
        : null,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>Leave Encashment</Typography.Title>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={8} align="middle">
          <Col flex={1}>
            <input placeholder="Employee UUID" value={employeeId} onChange={e => setEmployeeId(e.target.value)}
              style={{ width: '100%', padding: '4px 8px', border: '1px solid #d9d9d9', borderRadius: 4 }} />
          </Col>
          <Col><Button onClick={() => load(employeeId)} loading={listing}>Load</Button></Col>
          <Col>
            <Button type="primary" onClick={() => setModalOpen(true)} disabled={!employeeId.trim()}>
              New Encashment
            </Button>
          </Col>
        </Row>
      </Card>

      <Table
        size="small"
        columns={cols}
        dataSource={encashments}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title="Create Encashment"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        onOk={() => form.submit()}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" onFinish={handleEncash}
          initialValues={{ year: now.getFullYear(), payrollYear: now.getFullYear(), payrollMonth: now.getMonth() + 1 }}>
          <Row gutter={8}>
            <Col span={12}>
              <Form.Item name="leaveTypeId" label="Leave Type ID" rules={[{ required: true }]}>
                <input style={{ width: '100%', padding: '4px 8px', border: '1px solid #d9d9d9', borderRadius: 4 }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="year" label="Balance Year" rules={[{ required: true }]}>
                <InputNumber style={{ width: '100%' }} min={2020} max={2100} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="days" label="Days to Encash" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} min={0.5} step={0.5} />
          </Form.Item>
          <Row gutter={8}>
            <Col span={12}>
              <Form.Item name="payrollYear" label="Payroll Year" rules={[{ required: true }]}>
                <InputNumber style={{ width: '100%' }} min={2020} max={2100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="payrollMonth" label="Payroll Month" rules={[{ required: true }]}>
                <Select style={{ width: '100%' }} options={MONTHS} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes">
            <input style={{ width: '100%', padding: '4px 8px', border: '1px solid #d9d9d9', borderRadius: 4 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
