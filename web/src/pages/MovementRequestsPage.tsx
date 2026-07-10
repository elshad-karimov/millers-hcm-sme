import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

type MovementType = 'TRANSFER' | 'PROMOTION'
type MovementRequestStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXECUTED'

interface MovementRequest {
  id: string
  requestNo: string
  employeeId: string
  movementType: MovementType
  proposedPositionId: string
  proposedOrgUnitId: string
  proposedGrade: string | null
  proposedSalary: number | null
  effectiveDate: string
  justification: string
  status: MovementRequestStatus
  workflowInstanceId: string | null
  requestedBy: string
  executedAt?: string | null
  executedBy?: string | null
}

interface Employee {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
}

interface Position {
  id: string
  code: string
  title: string
}

interface OrgUnit {
  id: string
  code: string
  name: string
}

const STATUS_COLOR: Record<MovementRequestStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  EXECUTED: 'blue',
}

export function MovementRequestsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const isHRAdmin = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [form] = Form.useForm()
  const [requests, setRequests] = useState<MovementRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [showModal, setShowModal] = useState(false)

  // Lookup data
  const [employees, setEmployees] = useState<Employee[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([])

  useEffect(() => {
    reload()
    loadLookups()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function reload() {
    setLoading(true)
    api
      .get<MovementRequest[]>('/manager/movements')
      .then((r) => setRequests(r.data))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load movement requests'),
      )
      .finally(() => setLoading(false))
  }

  function loadLookups() {
    // Load team members
    api
      .get<Employee[]>('/self/team')
      .then((r) => setEmployees(r.data))
      .catch(() => message.error('Failed to load team members'))

    // Load positions
    api
      .get<Position[]>('/positions')
      .then((r) => setPositions(r.data))
      .catch(() => message.error('Failed to load positions'))

    // Load org units
    api
      .get<OrgUnit[]>('/organization/units')
      .then((r) => setOrgUnits(r.data))
      .catch(() => message.error('Failed to load org units'))
  }

  async function handleCreate() {
    try {
      const values = await form.validateFields()
      setCreating(true)

      const payload = {
        employeeId: values.employeeId,
        movementType: values.movementType,
        proposedPositionId: values.proposedPositionId,
        proposedOrgUnitId: values.proposedOrgUnitId,
        proposedGrade: values.proposedGrade ?? null,
        proposedSalary: values.proposedSalary ?? null,
        effectiveDate: values.effectiveDate.format('YYYY-MM-DD'),
        justification: values.justification,
      }

      await api.post('/manager/movements', payload)
      message.success('Movement request created and submitted')
      setShowModal(false)
      form.resetFields()
      reload()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      message.error(axiosErr?.response?.data?.message ?? 'Failed to create request')
    } finally {
      setCreating(false)
    }
  }

  async function handleCancel(id: string) {
    try {
      await api.post(`/manager/movements/${id}/cancel`)
      message.success('Request cancelled')
      reload()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      message.error(axiosErr?.response?.data?.message ?? 'Failed to cancel request')
    }
  }

  async function handleExecute(id: string) {
    try {
      const r = await api.post<{ message: string }>(`/manager/movements/${id}/execute`)
      message.success(r.data.message)
      reload()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      message.error(axiosErr?.response?.data?.message ?? 'Failed to execute request')
    }
  }

  const columns: ColumnsType<MovementRequest> = [
    { title: 'Request no', dataIndex: 'requestNo' },
    {
      title: 'Type',
      dataIndex: 'movementType',
      render: (v: MovementType) => <Tag color={v === 'PROMOTION' ? 'green' : 'blue'}>{v}</Tag>,
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const emp = employees.find((e) => e.id === id)
        return emp ? `${emp.employeeNo} - ${emp.firstName} ${emp.lastName}` : id.slice(0, 8)
      },
    },
    {
      title: 'Proposed salary',
      dataIndex: 'proposedSalary',
      render: (v: number | null) =>
        v !== null ? new Intl.NumberFormat().format(v) : <em style={{ color: '#999' }}>visible to HR only</em>,
    },
    { title: 'Effective date', dataIndex: 'effectiveDate' },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: MovementRequestStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'SUBMITTED' && (
            <Button size="small" danger onClick={() => handleCancel(r.id)}>
              Cancel
            </Button>
          )}
          {r.status === 'APPROVED' && isHRAdmin && (
            <Popconfirm
              title="Execute this movement?"
              description="Position/org-unit changes will be applied. Salary changes must go through the salary-change flow."
              onConfirm={() => handleExecute(r.id)}
            >
              <Button size="small" type="primary">
                Execute
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  return (
    <>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography.Title level={3}>Movement Requests</Typography.Title>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowModal(true)}>
            New Request
          </Button>
        </div>

        <Card>
          <Table rowKey="id" columns={columns} dataSource={requests} />
        </Card>
      </Space>

      <Modal
        title="New Movement Request"
        open={showModal}
        onCancel={() => {
          setShowModal(false)
          form.resetFields()
        }}
        onOk={handleCreate}
        confirmLoading={creating}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="movementType"
            label="Movement type"
            rules={[{ required: true, message: 'Please select a type' }]}
          >
            <Select
              options={[
                { label: 'Transfer', value: 'TRANSFER' },
                { label: 'Promotion', value: 'PROMOTION' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Please select an employee' }]}
          >
            <Select
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={employees.map((e) => ({
                label: `${e.employeeNo} - ${e.firstName} ${e.lastName}`,
                value: e.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="proposedPositionId"
            label="Proposed position"
            rules={[{ required: true, message: 'Please select a position' }]}
          >
            <Select
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={positions.map((p) => ({
                label: `${p.code} - ${p.title}`,
                value: p.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="proposedOrgUnitId"
            label="Proposed org unit"
            rules={[{ required: true, message: 'Please select an org unit' }]}
          >
            <Select
              showSearch
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={orgUnits.map((u) => ({
                label: `${u.code} - ${u.name}`,
                value: u.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="proposedGrade" label="Proposed grade">
            <Input />
          </Form.Item>
          <Form.Item
            name="proposedSalary"
            label="Proposed salary"
            extra="Visible to HR only"
          >
            <Input type="number" min={0} step={0.01} />
          </Form.Item>
          <Form.Item
            name="effectiveDate"
            label="Effective date"
            rules={[{ required: true, message: 'Please select a date' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="justification"
            label="Justification"
            rules={[{ required: true, message: 'Please provide justification' }]}
          >
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
