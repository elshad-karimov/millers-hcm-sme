import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  lifecycleApi,
  type ChangeType,
  type ContractChange,
  type ContractChangeStatus,
} from '../api/lifecycle'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_OPTIONS: ContractChangeStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
  'APPLIED',
]

const TYPE_OPTIONS: ChangeType[] = [
  'SALARY',
  'POSITION',
  'DEPARTMENT',
  'MANAGER',
  'GRADE',
  'LOCATION',
  'JOB_TITLE',
  'EMPLOYMENT_TYPE',
  'COST_CENTRE',
]

const STATUS_COLOR: Record<ContractChangeStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'cyan',
  REJECTED: 'red',
  CANCELLED: 'default',
  APPLIED: 'green',
}

const TYPE_COLOR: Record<ChangeType, string> = {
  SALARY: 'green',
  POSITION: 'geekblue',
  DEPARTMENT: 'purple',
  MANAGER: 'orange',
  GRADE: 'blue',
  LOCATION: 'cyan',
  JOB_TITLE: 'magenta',
  EMPLOYMENT_TYPE: 'volcano',
  COST_CENTRE: 'gold',
}

export function ContractChangesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<ContractChange[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [type, setType] = useState<ChangeType | undefined>()
  const [status, setStatus] = useState<ContractChangeStatus | undefined>()

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  const load = () => {
    setLoading(true)
    lifecycleApi
      .contractChanges({ employeeId, type, status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load contract changes'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, type, status, page, size])

  const employeeMap = useMemo(
    () => new Map(employees.map((e) => [e.id, e])),
    [employees],
  )

  const summarise = (c: ContractChange): string => {
    const nv = c.newValue ?? {}
    switch (c.changeType) {
      case 'SALARY':
        return `${nv.monthlyBaseSalary ?? '—'} ${nv.currency ?? ''}`
      case 'MANAGER':
        return `manager → ${nv.managerId ?? '—'}`
      case 'POSITION':
        return `position → ${nv.positionId ?? '—'}`
      case 'DEPARTMENT':
        return `${nv.departmentName ?? nv.orgUnitId ?? '—'}`
      case 'JOB_TITLE':
        return `${nv.positionTitle ?? '—'}`
      case 'COST_CENTRE':
        return `${nv.costCentre ?? '—'}`
      default:
        return JSON.stringify(nv)
    }
  }

  const columns: ColumnsType<ContractChange> = [
    { title: 'Change #', dataIndex: 'changeNo', width: 120 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Type',
      dataIndex: 'changeType',
      width: 140,
      render: (t: ChangeType) => <Tag color={TYPE_COLOR[t]}>{t.replace(/_/g, ' ')}</Tag>,
    },
    { title: 'New value', render: (_, r) => summarise(r) },
    { title: 'Effective', dataIndex: 'effectiveDate', width: 110 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: ContractChangeStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/lifecycle/contract-changes/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Contract changes
        </Typography.Title>
      }
      extra={
        canSubmit && (
          <Button type="primary" onClick={() => navigate('/lifecycle/contract-changes/new')}>
            New contract change
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <EmployeePicker
          allowClear
          placeholder="All employees"
          style={{ width: 280 }}
          value={employeeId}
          onChange={(v) => {
            setEmployeeId(v)
            setPage(0)
          }}
        />
        <Select
          allowClear
          placeholder="All types"
          style={{ width: 200 }}
          options={TYPE_OPTIONS.map((t) => ({ value: t, label: t.replace(/_/g, ' ') }))}
          value={type}
          onChange={(v) => {
            setType(v)
            setPage(0)
          }}
        />
        <Select
          allowClear
          placeholder="All statuses"
          style={{ width: 160 }}
          options={STATUS_OPTIONS.map((s) => ({ value: s, label: s }))}
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
        dataSource={rows}
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
