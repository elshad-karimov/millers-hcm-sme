import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate } from 'react-router-dom'
import { employeesApi, type Employee, type EmploymentStatus } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

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
  // M78 / P2-14 — new statuses
  'MATERNITY_LEAVE',
  'MILITARY_SERVICE',
  'EDUCATIONAL_LEAVE',
  'GARDEN_LEAVE',
  'NON_ACTIVE',
]

const STATUS_COLOR: Record<EmploymentStatus, string> = {
  ACTIVE: 'green',
  ON_PROBATION: 'gold',
  ON_LEAVE: 'blue',
  ON_BUSINESS_TRIP: 'cyan',
  SUSPENDED: 'orange',
  TERMINATED: 'red',
  RETIRED: 'default',
  CONTRACTOR: 'purple',
  INTERN: 'lime',
  // M78 / P2-14 — new statuses
  MATERNITY_LEAVE: 'pink',
  MILITARY_SERVICE: 'volcano',
  EDUCATIONAL_LEAVE: 'geekblue',
  GARDEN_LEAVE: 'gold',
  NON_ACTIVE: 'default',
}

export function EmployeesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [data, setData] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<EmploymentStatus | undefined>(undefined)

  const load = () => {
    setLoading(true)
    employeesApi
      .list({ page, size, search: search || undefined, status })
      .then((res) => {
        setData(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) => {
        message.error(err?.response?.data?.message ?? 'Failed to load employees')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, status])

  const columns: ColumnsType<Employee> = [
    {
      title: 'Employee #',
      dataIndex: 'employeeNo',
      render: (value: string, record) => <Link to={`/employees/${record.id}`}>{value}</Link>,
    },
    {
      title: 'Name',
      render: (_, r) => `${r.lastName}, ${r.firstName}${r.middleName ? ' ' + r.middleName : ''}`,
    },
    { title: 'Department', dataIndex: 'departmentName' },
    { title: 'Position', dataIndex: 'positionTitle' },
    { title: 'Hire date', dataIndex: 'hireDate' },
    {
      title: 'Status',
      dataIndex: 'employmentStatus',
      render: (s: EmploymentStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Employees</Typography.Title>}
      extra={
        canEdit && (
          <Button type="primary" onClick={() => navigate('/employees/new')}>
            New employee
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Search by name"
          allowClear
          onSearch={(v) => {
            setSearch(v)
            setPage(0)
            load()
          }}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          placeholder="Filter by status"
          style={{ width: 220 }}
          options={STATUS_OPTIONS.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
        />
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          onChange: (p, s) => {
            setPage(p - 1)
            setSize(s)
          },
          showSizeChanger: true,
        }}
      />
    </Card>
  )
}
