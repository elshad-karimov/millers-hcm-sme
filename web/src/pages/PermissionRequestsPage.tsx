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
  permissionApi,
  type PermissionRequest,
  type PermissionRequestStatus,
  type PermissionType,
} from '../api/permission'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { RoleSets } from '../auth/roleSets'

const STATUS_OPTIONS: PermissionRequestStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
]

const STATUS_COLOR: Record<PermissionRequestStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export function PermissionRequestsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [types, setTypes] = useState<PermissionType[]>([])
  const [rows, setRows] = useState<PermissionRequest[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [status, setStatus] = useState<PermissionRequestStatus | undefined>()
  const [filesFor, setFilesFor] = useState<PermissionRequest | null>(null)

  useEffect(() => {
    Promise.all([permissionApi.types(), employeesApi.list({ size: 500 })]).then(([t, e]) => {
      setTypes(t)
      setEmployees(e.content)
    })
  }, [])

  const load = () => {
    setLoading(true)
    permissionApi
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

  const columns: ColumnsType<PermissionRequest> = [
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
      dataIndex: 'permissionTypeId',
      render: (id: string) => <Tag color="geekblue">{typeMap.get(id)?.code ?? id}</Tag>,
      width: 110,
    },
    { title: 'Date', dataIndex: 'permissionDate', width: 110 },
    {
      title: 'Time',
      render: (_, r) =>
        r.startTime && r.endTime
          ? `${r.startTime.slice(0, 5)}–${r.endTime.slice(0, 5)}`
          : '—',
      width: 120,
    },
    { title: 'Hours', dataIndex: 'durationHours', width: 80, align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: PermissionRequestStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
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
      title={<Typography.Title level={4} style={{ margin: 0 }}>Permission requests</Typography.Title>}
      extra={
        canSubmit && (
          <Button type="primary" onClick={() => navigate('/permission/requests/new')}>
            New permission request
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
            ownerModule="PERMISSION"
            ownerEntity="PermissionRequest"
            ownerId={filesFor.id}
          />
        )}
      </Drawer>
    </Card>
  )
}
