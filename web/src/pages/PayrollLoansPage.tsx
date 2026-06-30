import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  payrollApi,
  type PayrollLoan,
  type LoanStatus,
  type CreateLoanRequest,
  type WriteOffLoanRequest,
} from '../api/payroll'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const LOAN_STATUS_COLOR: Record<LoanStatus, string> = {
  PENDING: 'orange',
  ACTIVE: 'blue',
  FULLY_REPAID: 'green',
  WRITTEN_OFF: 'purple',
  CANCELLED: 'default',
}

export function PayrollLoansPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.PAYROLL_WRITE)
  const canWriteOff = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<PayrollLoan[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [writeOffOpen, setWriteOffOpen] = useState(false)
  const [current, setCurrent] = useState<PayrollLoan | null>(null)
  const [form] = Form.useForm<CreateLoanRequest>()
  const [writeOffForm] = Form.useForm<WriteOffLoanRequest>()

  const principal = Form.useWatch('principalAmount', form)
  const installment = Form.useWatch('monthlyInstallment', form)

  const load = () => {
    setLoading(true)
    payrollApi
      .loans()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load loans'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setDrawerOpen(true)
  }

  const submit = async (v: CreateLoanRequest) => {
    try {
      await payrollApi.createLoan(v)
      message.success('Loan created')
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    }
  }

  const openWriteOff = (loan: PayrollLoan) => {
    setCurrent(loan)
    writeOffForm.resetFields()
    setWriteOffOpen(true)
  }

  const handleWriteOff = async (v: WriteOffLoanRequest) => {
    if (!current) return
    try {
      await payrollApi.writeOffLoan(current.id, v)
      message.success('Loan written off')
      setWriteOffOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Write-off failed',
      )
    }
  }

  const handleCancel = async (loan: PayrollLoan) => {
    try {
      await payrollApi.cancelLoan(loan.id)
      message.success('Loan cancelled')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<PayrollLoan> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} - ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Principal',
      dataIndex: 'principalAmount',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Installment/mo',
      dataIndex: 'monthlyInstallment',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Outstanding',
      dataIndex: 'outstandingBalance',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Repaid',
      dataIndex: 'amountRepaid',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: LoanStatus) => <Tag color={LOAN_STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Start',
      render: (_, r) =>
        `${r.startDeductionYear}/${String(r.startDeductionMonth).padStart(2, '0')}`,
    },
    {
      title: 'Actions',
      width: 140,
      render: (_, r) => {
        if (r.status === 'ACTIVE' && canWriteOff) {
          return (
            <Button size="small" danger onClick={() => openWriteOff(r)}>
              Write Off
            </Button>
          )
        }
        if (r.status === 'PENDING' && r.amountRepaid === 0 && canWrite) {
          return (
            <Button size="small" onClick={() => handleCancel(r)}>
              Cancel
            </Button>
          )
        }
        return null
      },
    },
  ]

  const termMonths =
    principal && installment && installment > 0
      ? Math.ceil(principal / installment)
      : 0

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Payroll Loans</Typography.Title>}
      extra={
        canWrite && (
          <Button type="primary" onClick={openCreate}>
            Create Loan
          </Button>
        )
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Drawer
        open={drawerOpen}
        title="Create Loan"
        onClose={() => setDrawerOpen(false)}
        width={400}
        extra={
          <Button type="primary" onClick={() => form.submit()}>
            Save
          </Button>
        }
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Employee is required' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} - ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="principalAmount"
            label="Principal Amount"
            rules={[{ required: true, message: 'Principal is required' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="monthlyInstallment"
            label="Monthly Installment"
            rules={[{ required: true, message: 'Installment is required' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          {termMonths > 0 && (
            <Typography.Text type="secondary">
              Computed term: {termMonths} month{termMonths > 1 ? 's' : ''}
            </Typography.Text>
          )}
          <Form.Item
            name="startDeductionYear"
            label="Start Deduction Year"
            rules={[{ required: true, message: 'Year is required' }]}
          >
            <InputNumber min={2020} max={2050} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="startDeductionMonth"
            label="Start Deduction Month"
            rules={[{ required: true, message: 'Month is required' }]}
          >
            <InputNumber min={1} max={12} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        open={writeOffOpen}
        title="Write Off Loan"
        onCancel={() => setWriteOffOpen(false)}
        onOk={() => writeOffForm.submit()}
        okText="Write Off"
        okButtonProps={{ danger: true }}
      >
        <Typography.Paragraph type="warning">
          This action will write off the outstanding loan balance and is audit-logged.
        </Typography.Paragraph>
        <Form form={writeOffForm} layout="vertical" onFinish={handleWriteOff}>
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Reason is required' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
