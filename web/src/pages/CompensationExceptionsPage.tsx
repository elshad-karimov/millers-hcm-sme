import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type CompensationExceptionDto,
  type CompensationExceptionStatus,
  type CompensationExceptionType,
  type CompensationExceptionSeverity,
  type ResolveExceptionRequest,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title } = Typography

const EXCEPTION_TYPE_COLOR: Record<CompensationExceptionType, string> = {
  BELOW_BAND: 'orange',
  ABOVE_BAND: 'red',
  ABOVE_THRESHOLD: 'red',
  OVER_BUDGET: 'volcano',
}

const SEVERITY_COLOR: Record<CompensationExceptionSeverity, string> = {
  LOW: 'green',
  MEDIUM: 'gold',
  HIGH: 'red',
}

const STATUS_COLOR: Record<CompensationExceptionStatus, string> = {
  OPEN: 'gold',
  ACKNOWLEDGED: 'blue',
  RESOLVED: 'green',
  WAIVED: 'default',
}

export function CompensationExceptionsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<CompensationExceptionDto[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<CompensationExceptionStatus | undefined>()
  const [typeFilter, setTypeFilter] = useState<CompensationExceptionType | undefined>()

  const [resolveOpen, setResolveOpen] = useState(false)
  const [current, setCurrent] = useState<CompensationExceptionDto | null>(null)
  const [form] = Form.useForm<ResolveExceptionRequest>()

  const load = () => {
    setLoading(true)
    compensationApi
      .listExceptions({ status: statusFilter, exceptionType: typeFilter })
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load exceptions'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, typeFilter])

  const openResolve = (exc: CompensationExceptionDto) => {
    setCurrent(exc)
    form.resetFields()
    setResolveOpen(true)
  }

  const handleResolve = async (v: ResolveExceptionRequest) => {
    if (!current) return
    try {
      await compensationApi.resolveException(current.id, v)
      message.success('Exception resolved')
      setResolveOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Resolve failed',
      )
    }
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<CompensationExceptionDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} - ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Exception Type',
      dataIndex: 'exceptionType',
      render: (t: CompensationExceptionType) => (
        <Tag color={EXCEPTION_TYPE_COLOR[t]}>{t.replace('_', ' ')}</Tag>
      ),
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      render: (s: CompensationExceptionSeverity) => <Tag color={SEVERITY_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Source',
      dataIndex: 'sourceType',
      render: (src: string) => src.replace('_', ' '),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (s: CompensationExceptionStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s.replace('_', ' ')}</Tag>
      ),
    },
    {
      title: 'Raised At',
      dataIndex: 'raisedAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: 'Actions',
      width: 120,
      render: (_, rec) => {
        if (rec.status === 'OPEN' && canWrite) {
          return (
            <Button size="small" type="primary" onClick={() => openResolve(rec)}>
              Resolve
            </Button>
          )
        }
        return null
      },
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Compensation Exceptions</Title>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="Filter by status"
            style={{ width: 160 }}
            allowClear
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'OPEN', label: 'Open' },
              { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
              { value: 'RESOLVED', label: 'Resolved' },
              { value: 'WAIVED', label: 'Waived' },
            ]}
          />
          <Select
            placeholder="Filter by type"
            style={{ width: 180 }}
            allowClear
            value={typeFilter}
            onChange={setTypeFilter}
            options={[
              { value: 'BELOW_BAND', label: 'Below Band' },
              { value: 'ABOVE_BAND', label: 'Above Band' },
              { value: 'ABOVE_THRESHOLD', label: 'Above Threshold' },
              { value: 'OVER_BUDGET', label: 'Over Budget' },
            ]}
          />
        </Space>
        <Table
          dataSource={rows}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        open={resolveOpen}
        title="Resolve Exception"
        onCancel={() => setResolveOpen(false)}
        onOk={() => form.submit()}
        okText="Resolve"
      >
        <Form form={form} layout="vertical" onFinish={handleResolve} style={{ marginTop: 16 }}>
          <Form.Item
            name="status"
            label="Resolution Status"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
                { value: 'RESOLVED', label: 'Resolved' },
                { value: 'WAIVED', label: 'Waived' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="note"
            label="Note"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
