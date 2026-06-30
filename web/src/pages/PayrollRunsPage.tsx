import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { payrollApi, type PayrollRun, type PayrollRunStatus, type RunType } from '../api/payroll'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<PayrollRunStatus, string> = {
  DRAFT: 'default',
  CALCULATED: 'gold',
  UNDER_REVIEW: 'orange',
  APPROVED: 'cyan',
  PAID: 'green',
  CLOSED: 'blue',
  REOPENED: 'magenta',
}

const RUNTYPE_COLOR: Record<RunType, string> = {
  REGULAR: 'blue',
  OFF_CYCLE: 'orange',
}

export function PayrollRunsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canCreate = hasRole(...RoleSets.PAYROLL_WRITE)

  const [rows, setRows] = useState<PayrollRun[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [createPeriod, setCreatePeriod] = useState<dayjs.Dayjs>(dayjs())
  const [runType, setRunType] = useState<RunType>('REGULAR')
  const [description, setDescription] = useState('')
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [creating, setCreating] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([payrollApi.runs(), employeesApi.list({ size: 500 })])
      .then(([runs, empPage]) => {
        setRows(runs)
        setEmployees(empPage.content)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load runs'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const createRun = async () => {
    if (runType === 'OFF_CYCLE' && selectedEmployees.length === 0) {
      message.error('Off-cycle runs require at least one employee')
      return
    }
    setCreating(true)
    try {
      const r = await payrollApi.create({
        periodYear: createPeriod.year(),
        periodMonth: createPeriod.month() + 1,
        jurisdiction: 'AZ',
        currency: 'AZN',
        runType,
        description: description || undefined,
        employeeIds: runType === 'OFF_CYCLE' ? selectedEmployees : undefined,
      })
      message.success(`Created ${r.runNo}`)
      setCreateOpen(false)
      setRunType('REGULAR')
      setDescription('')
      setSelectedEmployees([])
      navigate(`/payroll/runs/${r.id}`)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    } finally {
      setCreating(false)
    }
  }

  const columns: ColumnsType<PayrollRun> = [
    { title: 'Run #', dataIndex: 'runNo', width: 110 },
    {
      title: 'Period',
      render: (_, r) => `${r.periodYear}/${String(r.periodMonth).padStart(2, '0')}`,
      width: 110,
    },
    {
      title: 'Type',
      dataIndex: 'runType',
      width: 110,
      render: (rt?: RunType) => {
        const type = rt ?? 'REGULAR'
        return <Tag color={RUNTYPE_COLOR[type]}>{type.replace(/_/g, ' ')}</Tag>
      },
    },
    { title: 'Employees', dataIndex: 'employeeCount', align: 'right', width: 100 },
    {
      title: 'Gross',
      dataIndex: 'totalGross',
      render: (v: number, r) => `${v} ${r.currency}`,
      align: 'right',
    },
    {
      title: 'Income tax',
      dataIndex: 'totalIncomeTax',
      render: (v: number) => v,
      align: 'right',
    },
    {
      title: 'DSMF (er+em)',
      render: (_, r) => `${r.totalDsmfEmployee + r.totalDsmfEmployer}`,
      align: 'right',
    },
    {
      title: 'Allowance',
      dataIndex: 'totalAllowance',
      render: (v?: number) => v ?? 0,
      align: 'right',
      width: 110,
    },
    {
      title: 'Net',
      dataIndex: 'totalNet',
      render: (v: number, r) => <strong>{`${v} ${r.currency}`}</strong>,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: PayrollRunStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
      width: 130,
    },
    {
      title: '',
      width: 80,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/payroll/runs/${r.id}`)}>
          Open
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Payroll runs</Typography.Title>}
      extra={
        canCreate && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            New payroll run
          </Button>
        )
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Modal
        open={createOpen}
        title="New payroll run"
        onCancel={() => {
          setCreateOpen(false)
          setRunType('REGULAR')
          setDescription('')
          setSelectedEmployees([])
        }}
        confirmLoading={creating}
        onOk={createRun}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>Select the payroll period:</Typography.Text>
          <DatePicker
            picker="month"
            value={createPeriod}
            onChange={(v) => v && setCreatePeriod(v)}
            format="YYYY / MM"
            style={{ width: 220 }}
          />
          <Typography.Text style={{ marginTop: 12 }}>Run type:</Typography.Text>
          <Radio.Group value={runType} onChange={(e) => setRunType(e.target.value)}>
            <Radio value="REGULAR">Regular</Radio>
            <Radio value="OFF_CYCLE">Off-cycle</Radio>
          </Radio.Group>
          {runType === 'OFF_CYCLE' && (
            <>
              <Typography.Text style={{ marginTop: 12 }}>Description:</Typography.Text>
              <Input.TextArea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Reason for off-cycle run"
                rows={2}
              />
              <Typography.Text style={{ marginTop: 12 }}>
                Employees: <Typography.Text type="danger">*</Typography.Text>
              </Typography.Text>
              <Select
                mode="multiple"
                style={{ width: '100%' }}
                placeholder="Select employees"
                value={selectedEmployees}
                onChange={setSelectedEmployees}
                options={employees.map((e) => ({
                  value: e.id,
                  label: `${e.employeeNo} ${e.lastName} ${e.firstName}`,
                }))}
                filterOption={(input, option) =>
                  (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
              />
            </>
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12, marginTop: 12 }}>
            Jurisdiction AZ — Azerbaijan 2026 tax brackets and DSMF rules apply automatically.
          </Typography.Text>
        </Space>
      </Modal>
    </Card>
  )
}
