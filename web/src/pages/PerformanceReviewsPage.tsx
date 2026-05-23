import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Modal,
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
  performanceApi,
  type PerformanceReview,
  type ReviewCycle,
  type ReviewStatus,
} from '../api/performance'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'

const STATUS_OPTIONS: ReviewStatus[] = [
  'SELF_IN_PROGRESS',
  'SELF_SUBMITTED',
  'MANAGER_SUBMITTED',
  'PENDING_APPROVAL',
  'APPROVED',
  'CALIBRATING',
  'COMPLETED',
  'REJECTED',
  'CANCELLED',
]

const STATUS_COLOR: Record<ReviewStatus, string> = {
  DRAFT: 'default',
  SELF_IN_PROGRESS: 'orange',
  SELF_SUBMITTED: 'gold',
  MANAGER_IN_PROGRESS: 'orange',
  MANAGER_SUBMITTED: 'geekblue',
  PENDING_APPROVAL: 'cyan',
  CALIBRATING: 'purple',
  APPROVED: 'green',
  COMPLETED: 'blue',
  REJECTED: 'red',
  CANCELLED: 'default',
}

interface StartForm {
  cycleId: string
  employeeId: string
  managerId?: string
}

export function PerformanceReviewsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canStart = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'DEPARTMENT_MANAGER')

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<PerformanceReview[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [status, setStatus] = useState<ReviewStatus | undefined>()

  const [startOpen, setStartOpen] = useState(false)
  const [startForm] = Form.useForm<StartForm>()

  useEffect(() => {
    Promise.all([performanceApi.cycles(), employeesApi.list({ size: 500 })]).then(([c, e]) => {
      setCycles(c)
      setEmployees(e.content)
    })
  }, [])

  const load = () => {
    setLoading(true)
    performanceApi
      .reviews({ cycleId, status, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load reviews'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId, status, page, size])

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const cycleMap = useMemo(() => new Map(cycles.map((c) => [c.id, c])), [cycles])

  const onStart = async (v: StartForm) => {
    try {
      const r = await performanceApi.startReview(v)
      message.success(`Review ${r.reviewNo} started`)
      setStartOpen(false)
      startForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Start failed',
      )
    }
  }

  const columns: ColumnsType<PerformanceReview> = [
    { title: 'Review #', dataIndex: 'reviewNo', width: 110 },
    {
      title: 'Cycle',
      dataIndex: 'cycleId',
      render: (id: string) => cycleMap.get(id)?.code ?? id,
      width: 160,
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = empMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Self',
      dataIndex: 'selfRating',
      width: 80,
      align: 'right',
      render: (v: number | null) => v ?? '—',
    },
    {
      title: 'Manager',
      dataIndex: 'managerRating',
      width: 80,
      align: 'right',
      render: (v: number | null) => v ?? '—',
    },
    {
      title: 'Goal score',
      dataIndex: 'goalScore',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '—',
    },
    {
      title: 'Final',
      dataIndex: 'finalRating',
      width: 80,
      align: 'right',
      render: (v: number | null) =>
        v != null ? (
          <Typography.Text strong>{v}</Typography.Text>
        ) : (
          '—'
        ),
    },
    { title: 'Band', dataIndex: 'finalBand', width: 160 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 150,
      render: (s: ReviewStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/performance/reviews/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Performance reviews</Typography.Title>}
      extra={
        canStart && (
          <Button type="primary" onClick={() => setStartOpen(true)}>
            Start review
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="All cycles"
          style={{ width: 280 }}
          options={cycles.map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
          value={cycleId}
          onChange={(v) => {
            setCycleId(v)
            setPage(0)
          }}
        />
        <Select
          allowClear
          placeholder="All statuses"
          style={{ width: 180 }}
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
        loading={loading}
        columns={columns}
        dataSource={rows}
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

      <Modal
        open={startOpen}
        title="Start performance review"
        onCancel={() => setStartOpen(false)}
        onOk={() => startForm.submit()}
        okText="Start"
      >
        <Form form={startForm} layout="vertical" onFinish={onStart}>
          <Form.Item name="cycleId" label="Cycle" rules={[{ required: true }]}>
            <Select
              options={cycles
                .filter((c) => c.status === 'OPEN' || c.status === 'CALIBRATING')
                .map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
            />
          </Form.Item>
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="managerId" label="Manager (optional, defaults to employee's manager)">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
