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
  type Termination,
  type TerminationStatus,
} from '../api/lifecycle'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_OPTIONS: TerminationStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
  'PROCESSED',
]

const STATUS_COLOR: Record<TerminationStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  PROCESSED: 'blue',
}

export function TerminationsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<Termination[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [status, setStatus] = useState<TerminationStatus | undefined>()

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  const load = () => {
    setLoading(true)
    lifecycleApi
      .terminations({ employeeId, status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load terminations'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, status, page, size])

  const employeeMap = useMemo(
    () => new Map(employees.map((e) => [e.id, e])),
    [employees],
  )

  const columns: ColumnsType<Termination> = [
    { title: 'Termination #', dataIndex: 'terminationNo', width: 140 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    { title: 'Reason', dataIndex: 'reasonCode', width: 170, render: (r: string) => r.replace(/_/g, ' ') },
    { title: 'Notice', dataIndex: 'noticeDate', width: 110 },
    { title: 'Last day', dataIndex: 'lastWorkingDate', width: 110 },
    { title: 'Effective', dataIndex: 'effectiveDate', width: 110 },
    {
      title: 'Final settlement',
      render: (_, r) =>
        r.finalSettlementAmount !== null && r.finalSettlementAmount !== undefined
          ? `${r.finalSettlementAmount} ${r.currency ?? ''}`
          : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: TerminationStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s}</Tag>
      ),
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/lifecycle/terminations/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Terminations
        </Typography.Title>
      }
      extra={
        canSubmit && (
          <Button type="primary" onClick={() => navigate('/lifecycle/terminations/new')}>
            New termination
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
          placeholder="All statuses"
          style={{ width: 180 }}
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
