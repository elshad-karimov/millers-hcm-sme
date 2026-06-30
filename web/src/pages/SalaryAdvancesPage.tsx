import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  payrollApi,
  type SalaryAdvance,
  type AdvanceStatus,
  type ApproveAdvanceRequest,
  type RejectAdvanceRequest,
} from '../api/payroll'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const ADVANCE_STATUS_COLOR: Record<AdvanceStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'cyan',
  REJECTED: 'red',
  DEDUCTED: 'green',
  CANCELLED: 'default',
}

export function SalaryAdvancesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canApprove = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<SalaryAdvance[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<AdvanceStatus | undefined>()

  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [current, setCurrent] = useState<SalaryAdvance | null>(null)
  const [approveForm] = Form.useForm<ApproveAdvanceRequest>()
  const [rejectForm] = Form.useForm<RejectAdvanceRequest>()

  const load = () => {
    setLoading(true)
    payrollApi
      .advances({ status: statusFilter })
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load advances'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const openApprove = (adv: SalaryAdvance) => {
    setCurrent(adv)
    approveForm.setFieldsValue({
      approvedAmount: adv.requestedAmount,
      repaymentYear: dayjs().year(),
      repaymentMonth: dayjs().month() + 2, // next month
    })
    setApproveOpen(true)
  }

  const openReject = (adv: SalaryAdvance) => {
    setCurrent(adv)
    rejectForm.resetFields()
    setRejectOpen(true)
  }

  const handleApprove = async (v: ApproveAdvanceRequest) => {
    if (!current) return
    try {
      await payrollApi.approveAdvance(current.id, v)
      message.success('Advance approved')
      setApproveOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Approve failed',
      )
    }
  }

  const handleReject = async (v: RejectAdvanceRequest) => {
    if (!current) return
    try {
      await payrollApi.rejectAdvance(current.id, v)
      message.success('Advance rejected')
      setRejectOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Reject failed',
      )
    }
  }

  const handleCancel = async (adv: SalaryAdvance) => {
    try {
      await payrollApi.cancelAdvance(adv.id)
      message.success('Advance cancelled')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<SalaryAdvance> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} - ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Requested',
      dataIndex: 'requestedAmount',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Approved',
      dataIndex: 'approvedAmount',
      align: 'right',
      render: (v: number | null) => (v ? `${v.toFixed(2)} AZN` : '—'),
    },
    {
      title: 'Repayment',
      render: (_, r) =>
        r.repaymentYear && r.repaymentMonth
          ? `${r.repaymentYear}/${String(r.repaymentMonth).padStart(2, '0')}`
          : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: AdvanceStatus) => <Tag color={ADVANCE_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Requested At',
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleDateString(),
    },
    {
      title: 'Actions',
      width: 200,
      render: (_, r) => {
        if (r.status === 'PENDING' && canApprove) {
          return (
            <Space>
              <Button size="small" type="primary" onClick={() => openApprove(r)}>
                Approve
              </Button>
              <Button size="small" danger onClick={() => openReject(r)}>
                Reject
              </Button>
            </Space>
          )
        }
        if (
          (r.status === 'PENDING' || r.status === 'APPROVED') &&
          canApprove
        ) {
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

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Salary Advances</Typography.Title>}
    >
      <Space style={{ marginBottom: 16 }}>
        <Select
          placeholder="Filter by status"
          style={{ width: 160 }}
          allowClear
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'PENDING', label: 'Pending' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
            { value: 'DEDUCTED', label: 'Deducted' },
            { value: 'CANCELLED', label: 'Cancelled' },
          ]}
        />
      </Space>
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Modal
        open={approveOpen}
        title="Approve Advance"
        onCancel={() => setApproveOpen(false)}
        onOk={() => approveForm.submit()}
        okText="Approve"
      >
        {current && (
          <>
            <Typography.Text>
              Employee: {employeeMap.get(current.employeeId)?.employeeNo} -{' '}
              {employeeMap.get(current.employeeId)?.firstName}{' '}
              {employeeMap.get(current.employeeId)?.lastName}
            </Typography.Text>
            <br />
            <Typography.Text>Requested: {current.requestedAmount.toFixed(2)} AZN</Typography.Text>
          </>
        )}
        <Form form={approveForm} layout="vertical" onFinish={handleApprove} style={{ marginTop: 16 }}>
          <Form.Item
            name="approvedAmount"
            label="Approved Amount"
            rules={[{ required: true, message: 'Approved amount is required' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="repaymentYear"
            label="Repayment Year"
            rules={[{ required: true, message: 'Year is required' }]}
          >
            <InputNumber min={2020} max={2050} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="repaymentMonth"
            label="Repayment Month"
            rules={[{ required: true, message: 'Month is required' }]}
          >
            <InputNumber min={1} max={12} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={rejectOpen}
        title="Reject Advance"
        onCancel={() => setRejectOpen(false)}
        onOk={() => rejectForm.submit()}
        okText="Reject"
        okButtonProps={{ danger: true }}
      >
        <Form form={rejectForm} layout="vertical" onFinish={handleReject}>
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
