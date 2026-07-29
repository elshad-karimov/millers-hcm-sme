// M488 + M489 — Contingent workforce (contractor engagements)

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  contingentApi,
  ENGAGEMENT_STATUS_COLOR,
  type ContractorEngagement,
  type ContractorEngagementRequest,
  type ConvertToFTERequest,
} from '../api/contingent'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'

const { Title, Text } = Typography

export function ContractorsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)
  const canConvert = hasRole('HR_ADMIN')

  const [engagements, setEngagements] = useState<ContractorEngagement[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string | undefined>('ACTIVE')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ContractorEngagement | null>(null)
  const [form] = Form.useForm<ContractorEngagementRequest>()
  const [saving, setSaving] = useState(false)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [convertModal, setConvertModal] = useState<string | null>(null)
  const [convertForm] = Form.useForm<ConvertToFTERequest>()
  const [converting, setConverting] = useState(false)

  const load = () => {
    setLoading(true)
    contingentApi.list(statusFilter)
      .then(setEngagements)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load engagements'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [statusFilter])

  useEffect(() => {
    employeesApi.list().then(res => setEmployees(res.content || [])).catch(() => {})
  }, [])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 'ACTIVE', tenureAlertDays: 30 })
    setOpen(true)
  }

  const startEdit = (eng: ContractorEngagement) => {
    setEditing(eng)
    form.setFieldsValue({
      employeeId: eng.employeeId,
      vendorAgencyId: eng.vendorAgencyId,
      contractStart: eng.contractStart as any,
      contractEnd: eng.contractEnd as any,
      rate: eng.rate,
      rateUnit: eng.rateUnit,
      poNumber: eng.poNumber,
      tenureAlertDays: eng.tenureAlertDays,
      status: eng.status,
      notes: eng.notes,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    const payload: ContractorEngagementRequest = {
      employeeId: values.employeeId,
      vendorAgencyId: values.vendorAgencyId,
      contractStart: typeof values.contractStart === 'string' ? values.contractStart : dayjs(values.contractStart).format('YYYY-MM-DD'),
      contractEnd: values.contractEnd ? (typeof values.contractEnd === 'string' ? values.contractEnd : dayjs(values.contractEnd).format('YYYY-MM-DD')) : undefined,
      rate: values.rate,
      rateUnit: values.rateUnit,
      poNumber: values.poNumber,
      tenureAlertDays: values.tenureAlertDays,
      status: values.status,
      notes: values.notes,
    }
    setSaving(true)
    const api = editing ? contingentApi.update(editing.id, payload) : contingentApi.create(payload)
    api
      .then(() => {
        message.success(editing ? 'Engagement updated' : 'Engagement created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const handleConvert = async () => {
    if (!convertModal) return
    const values = await convertForm.validateFields()
    const payload: ConvertToFTERequest = {
      newEmploymentType: values.newEmploymentType,
      effectiveDate: dayjs(values.effectiveDate).format('YYYY-MM-DD'),
    }
    setConverting(true)
    contingentApi.convertToFTE(convertModal, payload)
      .then(() => {
        message.success('Contractor converted to FTE')
        setConvertModal(null)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to convert'))
      .finally(() => setConverting(false))
  }

  const columns: ColumnsType<ContractorEngagement> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      key: 'employeeId',
      render: id => {
        const emp = employees.find(e => e.id === id)
        return emp ? `${emp.firstName} ${emp.lastName} (${emp.employeeNo})` : id.slice(0, 8)
      },
    },
    {
      title: 'Vendor',
      dataIndex: 'vendorAgencyId',
      key: 'vendorAgencyId',
      render: id => id ? id.slice(0, 8) : '—',
    },
    {
      title: 'Contract Period',
      key: 'period',
      render: (_, rec) => `${dayjs(rec.contractStart).format('YYYY-MM-DD')} — ${rec.contractEnd ? dayjs(rec.contractEnd).format('YYYY-MM-DD') : 'Open'}`,
    },
    {
      title: 'Rate',
      key: 'rate',
      render: (_, rec) => {
        if (!rec.rate) return '—'
        return `${Number(rec.rate).toFixed(2)} / ${rec.rateUnit || 'unit'}`
      },
    },
    {
      title: 'PO Number',
      dataIndex: 'poNumber',
      key: 'poNumber',
      render: p => p || '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: s => <Tag color={ENGAGEMENT_STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Tenure Alert',
      key: 'tenureAlert',
      render: (_, rec) => {
        if (!rec.contractEnd) return '—'
        const daysUntil = dayjs(rec.contractEnd).diff(dayjs(), 'day')
        if (daysUntil > rec.tenureAlertDays) return '—'
        return <Tag color="orange">{daysUntil} days left</Tag>
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => (
        <Space>
          {canWrite && <Button size="small" type="link" onClick={() => startEdit(rec)}>Edit</Button>}
          {canConvert && rec.status === 'ACTIVE' && (
            <Button size="small" type="link" onClick={() => { setConvertModal(rec.id); convertForm.resetFields() }}>
              Convert to FTE
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Contingent Workforce</Title>
      <Text type="secondary">
        Manage contractor engagements, track tenure, and convert to FTE.
      </Text>

      <Card style={{ marginTop: 24 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Row justify="space-between" align="middle">
            <Col>
              <Space>
                <Text>Status:</Text>
                <Select value={statusFilter} onChange={setStatusFilter} style={{ width: 150 }} allowClear placeholder="All">
                  <Select.Option value="ACTIVE">Active</Select.Option>
                  <Select.Option value="PLANNED">Planned</Select.Option>
                  <Select.Option value="COMPLETED">Completed</Select.Option>
                  <Select.Option value="TERMINATED">Terminated</Select.Option>
                  <Select.Option value="CONVERTED">Converted</Select.Option>
                </Select>
              </Space>
            </Col>
            {canWrite && (
              <Col>
                <Button type="primary" onClick={startCreate}>Create Engagement</Button>
              </Col>
            )}
          </Row>
          <Table
            dataSource={engagements}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{ pageSize: 20 }}
          />
        </Space>
      </Card>

      <Modal
        title={editing ? 'Edit Engagement' : 'Create Engagement'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        width={700}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Employee" name="employeeId" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" disabled={!!editing} />
          </Form.Item>
          <Form.Item label="Vendor Agency ID (optional)" name="vendorAgencyId">
            <Input placeholder="Vendor UUID" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Contract Start" name="contractStart" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Contract End" name="contractEnd">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item label="Rate" name="rate">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Rate Unit" name="rateUnit">
                <Select>
                  <Select.Option value="HOUR">Hour</Select.Option>
                  <Select.Option value="DAY">Day</Select.Option>
                  <Select.Option value="MONTH">Month</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="PO Number" name="poNumber">
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Tenure Alert Days" name="tenureAlertDays">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Status" name="status">
                <Select>
                  <Select.Option value="PLANNED">Planned</Select.Option>
                  <Select.Option value="ACTIVE">Active</Select.Option>
                  <Select.Option value="COMPLETED">Completed</Select.Option>
                  <Select.Option value="TERMINATED">Terminated</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Notes" name="notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Convert Contractor to FTE"
        open={!!convertModal}
        onCancel={() => setConvertModal(null)}
        onOk={handleConvert}
        confirmLoading={converting}
      >
        <Form form={convertForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="New Employment Type" name="newEmploymentType" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="FULL_TIME">Full Time</Select.Option>
              <Select.Option value="PART_TIME">Part Time</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="Effective Date" name="effectiveDate" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
