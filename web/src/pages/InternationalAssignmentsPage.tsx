// M487 — International assignments (mobility)

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  mobilityApi,
  ASSIGNMENT_STATUS_COLOR,
  type InternationalAssignment,
  type InternationalAssignmentRequest,
} from '../api/mobility'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
import { employeesApi, type Employee } from '../api/employees'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { EmployeePicker } from '../components/EmployeePicker'

const { Title, Text } = Typography

function AssignmentsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [assignments, setAssignments] = useState<InternationalAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string | undefined>('ACTIVE')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<InternationalAssignment | null>(null)
  const [form] = Form.useForm<InternationalAssignmentRequest>()
  const [saving, setSaving] = useState(false)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [detailDrawer, setDetailDrawer] = useState<InternationalAssignment | null>(null)

  const load = () => {
    setLoading(true)
    mobilityApi.list(statusFilter)
      .then(setAssignments)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load assignments'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [statusFilter])

  useEffect(() => {
    employeesApi.list().then(res => setEmployees(res.content || [])).catch(() => {})
  }, [])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 'PLANNED' })
    setOpen(true)
  }

  const startEdit = (asgn: InternationalAssignment) => {
    setEditing(asgn)
    form.setFieldsValue({
      employeeId: asgn.employeeId,
      hostCountry: asgn.hostCountry,
      hostCity: asgn.hostCity,
      hostEntity: asgn.hostEntity,
      purpose: asgn.purpose,
      startDate: asgn.startDate as any,
      endDate: asgn.endDate as any,
      status: asgn.status,
      visaType: asgn.visaType,
      visaExpiry: asgn.visaExpiry as any,
      housingAllowance: asgn.housingAllowance,
      colaAmount: asgn.colaAmount,
      hardshipAmount: asgn.hardshipAmount,
      notes: asgn.notes,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    const payload: InternationalAssignmentRequest = {
      employeeId: values.employeeId,
      hostCountry: values.hostCountry,
      hostCity: values.hostCity,
      hostEntity: values.hostEntity,
      purpose: values.purpose,
      startDate: typeof values.startDate === 'string' ? values.startDate : dayjs(values.startDate).format('YYYY-MM-DD'),
      endDate: values.endDate ? (typeof values.endDate === 'string' ? values.endDate : dayjs(values.endDate).format('YYYY-MM-DD')) : undefined,
      status: values.status,
      visaType: values.visaType,
      visaExpiry: values.visaExpiry ? (typeof values.visaExpiry === 'string' ? values.visaExpiry : dayjs(values.visaExpiry).format('YYYY-MM-DD')) : undefined,
      housingAllowance: values.housingAllowance,
      colaAmount: values.colaAmount,
      hardshipAmount: values.hardshipAmount,
      notes: values.notes,
    }
    setSaving(true)
    const api = editing ? mobilityApi.update(editing.id, payload) : mobilityApi.create(payload)
    api
      .then(() => {
        message.success(editing ? 'Assignment updated' : 'Assignment created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const columns: ColumnsType<InternationalAssignment> = [
    { title: 'No.', dataIndex: 'assignmentNo', key: 'assignmentNo' },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      key: 'employeeId',
      render: id => {
        const emp = employees.find(e => e.id === id)
        return emp ? `${emp.firstName} ${emp.lastName}` : id.slice(0, 8)
      },
    },
    { title: 'Host Country', dataIndex: 'hostCountry', key: 'hostCountry' },
    { title: 'Host City', dataIndex: 'hostCity', key: 'hostCity', render: c => c || '—' },
    {
      title: 'Period',
      key: 'period',
      render: (_, rec) => `${dayjs(rec.startDate).format('YYYY-MM-DD')} — ${rec.endDate ? dayjs(rec.endDate).format('YYYY-MM-DD') : 'Open'}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: s => <Tag color={ASSIGNMENT_STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Visa Expiry',
      dataIndex: 'visaExpiry',
      key: 'visaExpiry',
      render: (d) => {
        if (!d) return '—'
        const isExpiring = dayjs(d).diff(dayjs(), 'day') <= 90
        return (
          <Tag color={isExpiring ? 'red' : 'default'}>
            {dayjs(d).format('YYYY-MM-DD')}
          </Tag>
        )
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => (
        <Space>
          <Button size="small" type="link" onClick={() => setDetailDrawer(rec)}>View</Button>
          {canWrite && <Button size="small" type="link" onClick={() => startEdit(rec)}>Edit</Button>}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <Text>Status:</Text>
              <Select value={statusFilter} onChange={setStatusFilter} style={{ width: 150 }} allowClear placeholder="All">
                <Select.Option value="PLANNED">Planned</Select.Option>
                <Select.Option value="ACTIVE">Active</Select.Option>
                <Select.Option value="COMPLETED">Completed</Select.Option>
                <Select.Option value="CANCELLED">Cancelled</Select.Option>
              </Select>
            </Space>
          </Col>
          {canWrite && (
            <Col>
              <Button type="primary" onClick={startCreate}>Create Assignment</Button>
            </Col>
          )}
        </Row>
        <Table
          dataSource={assignments}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Modal
        title={editing ? 'Edit Assignment' : 'Create Assignment'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        width={800}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Employee" name="employeeId" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" disabled={!!editing} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Host Country" name="hostCountry" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Host City" name="hostCity">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Host Entity" name="hostEntity">
            <Input />
          </Form.Item>
          <Form.Item label="Purpose" name="purpose">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Start Date" name="startDate" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="End Date" name="endDate">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Status" name="status">
                <Select>
                  <Select.Option value="PLANNED">Planned</Select.Option>
                  <Select.Option value="ACTIVE">Active</Select.Option>
                  <Select.Option value="COMPLETED">Completed</Select.Option>
                  <Select.Option value="CANCELLED">Cancelled</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Visa Type" name="visaType">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Visa Expiry" name="visaExpiry">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="Housing Allowance" name="housingAllowance">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="COLA Amount" name="colaAmount">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Hardship Amount" name="hardshipAmount">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Notes" name="notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="Assignment Details"
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={600}
      >
        {detailDrawer && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Assignment No">{detailDrawer.assignmentNo}</Descriptions.Item>
              <Descriptions.Item label="Employee">
                {(() => {
                  const emp = employees.find(e => e.id === detailDrawer.employeeId)
                  return emp ? `${emp.firstName} ${emp.lastName} (${emp.employeeNo})` : detailDrawer.employeeId
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="Host Country">{detailDrawer.hostCountry}</Descriptions.Item>
              <Descriptions.Item label="Host City">{detailDrawer.hostCity || '—'}</Descriptions.Item>
              <Descriptions.Item label="Host Entity">{detailDrawer.hostEntity || '—'}</Descriptions.Item>
              <Descriptions.Item label="Purpose">{detailDrawer.purpose || '—'}</Descriptions.Item>
              <Descriptions.Item label="Start Date">{dayjs(detailDrawer.startDate).format('YYYY-MM-DD')}</Descriptions.Item>
              <Descriptions.Item label="End Date">
                {detailDrawer.endDate ? dayjs(detailDrawer.endDate).format('YYYY-MM-DD') : 'Open'}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={ASSIGNMENT_STATUS_COLOR[detailDrawer.status] || 'default'}>{detailDrawer.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Visa Type">{detailDrawer.visaType || '—'}</Descriptions.Item>
              <Descriptions.Item label="Visa Expiry">
                {detailDrawer.visaExpiry ? dayjs(detailDrawer.visaExpiry).format('YYYY-MM-DD') : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Housing Allowance">
                {detailDrawer.housingAllowance ? Number(detailDrawer.housingAllowance).toFixed(2) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="COLA">
                {detailDrawer.colaAmount ? Number(detailDrawer.colaAmount).toFixed(2) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Hardship">
                {detailDrawer.hardshipAmount ? Number(detailDrawer.hardshipAmount).toFixed(2) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Notes">{detailDrawer.notes || '—'}</Descriptions.Item>
            </Descriptions>

            <Card title="Documents">
              <AttachmentUploader
                ownerModule="mobility"
                ownerEntity="internationalassignment"
                ownerId={detailDrawer.id}
              />
            </Card>
          </Space>
        )}
      </Drawer>
    </>
  )
}

function ExpiringVisasTab() {
  const { message } = AntdApp.useApp()
  const [assignments, setAssignments] = useState<InternationalAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [days, setDays] = useState(90)
  const [employees, setEmployees] = useState<Employee[]>([])

  const load = () => {
    setLoading(true)
    mobilityApi.expiringVisas(days)
      .then(setAssignments)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load expiring visas'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [days])

  useEffect(() => {
    employeesApi.list().then(res => setEmployees(res.content || [])).catch(() => {})
  }, [])

  const columns: ColumnsType<InternationalAssignment> = [
    { title: 'No.', dataIndex: 'assignmentNo', key: 'assignmentNo' },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      key: 'employeeId',
      render: id => {
        const emp = employees.find(e => e.id === id)
        return emp ? `${emp.firstName} ${emp.lastName}` : id.slice(0, 8)
      },
    },
    { title: 'Host Country', dataIndex: 'hostCountry', key: 'hostCountry' },
    { title: 'Visa Type', dataIndex: 'visaType', key: 'visaType', render: v => v || '—' },
    {
      title: 'Visa Expiry',
      dataIndex: 'visaExpiry',
      key: 'visaExpiry',
      render: d => d ? dayjs(d).format('YYYY-MM-DD') : '—',
    },
    {
      title: 'Days Until Expiry',
      key: 'daysUntil',
      render: (_, rec) => {
        if (!rec.visaExpiry) return '—'
        const diff = dayjs(rec.visaExpiry).diff(dayjs(), 'day')
        return <Tag color={diff <= 30 ? 'red' : diff <= 60 ? 'orange' : 'blue'}>{diff} days</Tag>
      },
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Col>
          <Space>
            <Text>Show visas expiring in next:</Text>
            <Select value={days} onChange={setDays} style={{ width: 120 }}>
              <Select.Option value={30}>30 days</Select.Option>
              <Select.Option value={60}>60 days</Select.Option>
              <Select.Option value={90}>90 days</Select.Option>
              <Select.Option value={180}>180 days</Select.Option>
            </Select>
          </Space>
        </Col>
      </Row>
      <Table
        dataSource={assignments}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={false}
      />
    </Space>
  )
}

export function InternationalAssignmentsPage() {
  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>International Assignments</Title>
      <Text type="secondary">
        Track employee international assignments, visas, and allowances.
      </Text>

      <Card style={{ marginTop: 24 }}>
        <Tabs
          items={[
            { key: 'assignments', label: 'Assignments', children: <AssignmentsTab /> },
            { key: 'visas', label: 'Expiring Visas', children: <ExpiringVisasTab /> },
          ]}
        />
      </Card>
    </div>
  )
}
