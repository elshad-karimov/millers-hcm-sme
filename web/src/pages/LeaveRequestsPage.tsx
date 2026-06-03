import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PaperClipOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  leaveApi,
  type LeaveRequest,
  type LeaveRequestStatus,
  type LeaveType,
} from '../api/leave'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { RoleSets } from '../auth/roleSets'

const STATUS_OPTIONS: LeaveRequestStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
]

const STATUS_COLOR: Record<LeaveRequestStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export function LeaveRequestsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [rows, setRows] = useState<LeaveRequest[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [status, setStatus] = useState<LeaveRequestStatus | undefined>()
  const [filesFor, setFilesFor] = useState<LeaveRequest | null>(null)

  useEffect(() => {
    Promise.all([leaveApi.types(), employeesApi.list({ size: 500 })]).then(([t, e]) => {
      setTypes(t)
      setEmployees(e.content)
    })
  }, [])

  const load = () => {
    setLoading(true)
    leaveApi
      .requests({ employeeId, status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load requests'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, status, page, size])

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const columns: ColumnsType<LeaveRequest> = [
    { title: 'Request #', dataIndex: 'requestNo', width: 110 },
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
      dataIndex: 'leaveTypeId',
      render: (id: string) => <Tag color="geekblue">{typeMap.get(id)?.code ?? id}</Tag>,
      width: 110,
    },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    { title: 'End', dataIndex: 'endDate', width: 110 },
    { title: 'Days', dataIndex: 'totalDays', width: 80, align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: LeaveRequestStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Submitted',
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '',
      key: 'files',
      width: 100,
      render: (_, r) => (
        <Button
          size="small"
          icon={<PaperClipOutlined />}
          onClick={() => setFilesFor(r)}
        >
          Files
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Leave requests</Typography.Title>}
      extra={
        canSubmit && (
          <Button type="primary" onClick={() => navigate('/leave/requests/new')}>
            New leave request
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="All employees"
          style={{ width: 280 }}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
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
      <Drawer
        title={filesFor ? `Attachments — ${filesFor.requestNo}` : 'Attachments'}
        placement="right"
        width={560}
        open={!!filesFor}
        onClose={() => setFilesFor(null)}
        destroyOnClose
      >
        {filesFor && (
          <AttachmentUploader
            ownerModule="LEAVE"
            ownerEntity="LeaveRequest"
            ownerId={filesFor.id}
          />
        )}
      </Drawer>
    </Card>
  )
}
