import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { payrollApi, type CompensationResponse } from '../api/payroll'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

interface FormValues {
  employeeId: string
  monthlyBaseSalary: number
  currency: string
  effectiveFrom: dayjs.Dayjs
  reason?: string
}

export function PayrollCompensationPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [history, setHistory] = useState<CompensationResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()

  useEffect(() => {
    if (!employeeId) {
      setHistory([])
      return
    }
    setLoading(true)
    payrollApi
      .compensationHistory(employeeId)
      .then(setHistory)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load history'),
      )
      .finally(() => setLoading(false))
  }, [employeeId, message])

  const submit = async (v: FormValues) => {
    try {
      await payrollApi.setCompensation({
        employeeId: v.employeeId,
        monthlyBaseSalary: v.monthlyBaseSalary,
        currency: v.currency || 'AZN',
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        reason: v.reason,
      })
      message.success('Compensation recorded')
      setFormOpen(false)
      form.resetFields()
      if (employeeId === v.employeeId) {
        payrollApi.compensationHistory(v.employeeId).then(setHistory)
      } else {
        setEmployeeId(v.employeeId)
      }
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const columns: ColumnsType<CompensationResponse> = [
    { title: 'Effective from', dataIndex: 'effectiveFrom', width: 130 },
    { title: 'Effective to', dataIndex: 'effectiveTo', width: 130,
      render: (v?: string | null) => v ?? <Tag color="green">current</Tag>,
    },
    {
      title: 'Monthly base',
      dataIndex: 'monthlyBaseSalary',
      align: 'right',
      render: (v: number, r) => `${v} ${r.currency}`,
    },
    { title: 'Reason', dataIndex: 'reason' },
    { title: 'Created', dataIndex: 'createdAt',
      render: (v: string, r) => `${new Date(v).toLocaleString()} · ${r.createdBy ?? '—'}`,
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Employee compensation</Typography.Title>}
      extra={
        canEdit && (
          <Button
            type="primary"
            onClick={() => {
              form.setFieldsValue({
                employeeId: employeeId ?? '',
                currency: 'AZN',
                effectiveFrom: dayjs().startOf('month'),
              })
              setFormOpen(true)
            }}
          >
            Set salary
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }}>
        <EmployeePicker
          placeholder="Select an employee"
          style={{ width: 320 }}
          value={employeeId}
          onChange={setEmployeeId}
        />
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={history}
        loading={loading}
        pagination={false}
      />

      <Modal
        open={formOpen}
        title="Set monthly salary"
        onCancel={() => setFormOpen(false)}
        onOk={() => form.submit()}
        okText="Save"
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Select an employee' }]}
          >
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Form.Item
            name="monthlyBaseSalary"
            label="Monthly base salary"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} step={50} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="currency" label="Currency" initialValue="AZN">
            <Input maxLength={3} />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label="Effective from"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="Reason">
            <Input.TextArea rows={3} placeholder="Hiring, promotion, market adjustment, …" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
