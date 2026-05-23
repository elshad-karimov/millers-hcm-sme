import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Input,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  employeesApi,
  type AuditEntry,
  type Employee,
  type EmploymentStatus,
} from '../api/employees'
import { useAuth } from '../auth/AuthContext'

const STATUS_OPTIONS: EmploymentStatus[] = [
  'ACTIVE',
  'ON_PROBATION',
  'ON_LEAVE',
  'ON_BUSINESS_TRIP',
  'SUSPENDED',
  'TERMINATED',
  'RETIRED',
  'CONTRACTOR',
  'INTERN',
]

export function EmployeeDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole('HR_ADMIN', 'HR_SPECIALIST')
  const canAudit = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR')

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [audit, setAudit] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [statusModal, setStatusModal] = useState(false)
  const [newStatus, setNewStatus] = useState<EmploymentStatus | undefined>()
  const [reason, setReason] = useState('')

  const load = () => {
    setLoading(true)
    Promise.all([employeesApi.get(id), canAudit ? employeesApi.audit(id) : Promise.resolve([])])
      .then(([emp, log]) => {
        setEmployee(emp)
        setAudit(log)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (loading || !employee) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const auditColumns: ColumnsType<AuditEntry> = [
    { title: 'When', dataIndex: 'createdAt', render: (v) => new Date(v).toLocaleString() },
    { title: 'Actor', dataIndex: 'actor' },
    { title: 'Action', dataIndex: 'action', render: (v) => <Tag>{v}</Tag> },
    { title: 'IP', dataIndex: 'ipAddress' },
    {
      title: 'New value',
      dataIndex: 'newValue',
      render: (v?: string | null) =>
        v ? (
          <Typography.Text code style={{ whiteSpace: 'pre-wrap' }}>
            {v}
          </Typography.Text>
        ) : (
          '—'
        ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <Link to="/employees">Employees</Link> /{' '}
            <strong>
              {employee.firstName} {employee.lastName}
            </strong>
            <Tag color="blue">{employee.employeeNo}</Tag>
            <Tag color="geekblue">{employee.employmentStatus.replace(/_/g, ' ')}</Tag>
          </Space>
        }
        extra={
          canEdit && (
            <Space>
              <Button onClick={() => setStatusModal(true)}>Change status</Button>
              <Button type="primary" onClick={() => navigate(`/employees/${id}/edit`)}>
                Edit
              </Button>
            </Space>
          )
        }
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="Employee no">{employee.employeeNo}</Descriptions.Item>
          <Descriptions.Item label="Status">{employee.employmentStatus}</Descriptions.Item>
          <Descriptions.Item label="First name">{employee.firstName}</Descriptions.Item>
          <Descriptions.Item label="Last name">{employee.lastName}</Descriptions.Item>
          <Descriptions.Item label="Middle name">{employee.middleName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Gender">{employee.gender ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Date of birth">{employee.birthDate ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="National ID">{employee.nationalId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Email">{employee.email ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Phone">{employee.phone ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Hire date">{employee.hireDate}</Descriptions.Item>
          <Descriptions.Item label="Department">{employee.departmentName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Position">{employee.positionTitle ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Cost centre">{employee.costCentre ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Created">
            {new Date(employee.createdAt).toLocaleString()} by {employee.createdBy ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Updated">
            {new Date(employee.updatedAt).toLocaleString()} by {employee.updatedBy ?? '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {canAudit && (
        <Card title="Audit history">
          <Table
            rowKey="id"
            columns={auditColumns}
            dataSource={audit}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      )}

      <Modal
        title="Change employment status"
        open={statusModal}
        onCancel={() => setStatusModal(false)}
        onOk={async () => {
          if (!newStatus) return
          try {
            await employeesApi.changeStatus(employee.id, newStatus, reason || undefined)
            message.success('Status updated')
            setStatusModal(false)
            setNewStatus(undefined)
            setReason('')
            load()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Status change failed',
            )
          }
        }}
        okButtonProps={{ disabled: !newStatus }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            placeholder="New status"
            style={{ width: '100%' }}
            value={newStatus}
            options={STATUS_OPTIONS.filter((s) => s !== employee.employmentStatus).map((s) => ({
              value: s,
              label: s.replace(/_/g, ' '),
            }))}
            onChange={setNewStatus}
          />
          <Input.TextArea
            placeholder="Reason (optional)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
          />
        </Space>
      </Modal>
    </Space>
  )
}
