import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { payrollApi, type PayrollRun, type PayrollRunStatus } from '../api/payroll'
import { useAuth } from '../auth/AuthContext'

const STATUS_COLOR: Record<PayrollRunStatus, string> = {
  DRAFT: 'default',
  CALCULATED: 'gold',
  UNDER_REVIEW: 'orange',
  APPROVED: 'cyan',
  PAID: 'green',
  CLOSED: 'blue',
  REOPENED: 'magenta',
}

export function PayrollRunsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canCreate = hasRole('HR_ADMIN', 'SYSTEM_ADMIN', 'PAYROLL_SPECIALIST')

  const [rows, setRows] = useState<PayrollRun[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [createPeriod, setCreatePeriod] = useState<dayjs.Dayjs>(dayjs())
  const [creating, setCreating] = useState(false)

  const load = () => {
    setLoading(true)
    payrollApi
      .runs()
      .then(setRows)
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
    setCreating(true)
    try {
      const r = await payrollApi.create(createPeriod.year(), createPeriod.month() + 1)
      message.success(`Created ${r.runNo}`)
      setCreateOpen(false)
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
        onCancel={() => setCreateOpen(false)}
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
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Jurisdiction AZ — Azerbaijan 2026 tax brackets and DSMF rules apply automatically.
          </Typography.Text>
        </Space>
      </Modal>
    </Card>
  )
}
