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
  businessTripApi,
  type BusinessTrip,
  type TripStatus,
  type TripType,
} from '../api/businessTrip'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { EmployeePicker } from '../components/EmployeePicker'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<TripStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  COMPLETED: 'blue',
}

const TYPE_COLOR: Record<TripType, string> = {
  DOMESTIC: 'geekblue',
  INTERNATIONAL: 'volcano',
}

const STATUS_OPTIONS: TripStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED']

export function BusinessTripsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<BusinessTrip[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [status, setStatus] = useState<TripStatus | undefined>()
  const [filesFor, setFilesFor] = useState<BusinessTrip | null>(null)

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  const load = () => {
    setLoading(true)
    businessTripApi
      .list({ employeeId, status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load trips'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, status, page, size])

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  const columns: ColumnsType<BusinessTrip> = [
    { title: 'Trip #', dataIndex: 'tripNo', width: 110 },
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
      dataIndex: 'tripType',
      width: 130,
      render: (t: TripType) => <Tag color={TYPE_COLOR[t]}>{t}</Tag>,
    },
    {
      title: 'Destination',
      render: (_, r) =>
        r.destinationCountry ? `${r.destinationCity}, ${r.destinationCountry}` : r.destinationCity,
    },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    { title: 'End', dataIndex: 'endDate', width: 110 },
    { title: 'Days', dataIndex: 'totalDays', width: 70, align: 'right' },
    {
      title: 'Advance',
      render: (_, r) =>
        r.requestedAdvance
          ? `${r.requestedAdvance} ${r.currency}${
              r.approvedAdvance !== null && r.approvedAdvance !== undefined
                ? ` (appr. ${r.approvedAdvance})`
                : ''
            }`
          : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: TripStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
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
      title={<Typography.Title level={4} style={{ margin: 0 }}>Business trips</Typography.Title>}
      extra={
        canSubmit && (
          <Button type="primary" onClick={() => navigate('/business-trips/new')}>
            New business trip
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
      <Drawer
        title={filesFor ? `Attachments — ${filesFor.tripNo}` : 'Attachments'}
        placement="right"
        width={560}
        open={!!filesFor}
        onClose={() => setFilesFor(null)}
        destroyOnClose
      >
        {filesFor && (
          <AttachmentUploader
            ownerModule="BUSINESS_TRIP"
            ownerEntity="BusinessTripRequest"
            ownerId={filesFor.id}
          />
        )}
      </Drawer>
    </Card>
  )
}
