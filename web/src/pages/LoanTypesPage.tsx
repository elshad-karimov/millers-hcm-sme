// M460 — Loan type CRUD (HR_ADMIN).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'

interface LoanType {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string
  maxAmount?: number
  maxMultipleOfNet: number
  maxMonths: number
  interestRatePct: number
  minTenureMonths: number
  maxActiveLoans: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export function LoanTypesPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<LoanType[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [current, setCurrent] = useState<LoanType | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    api
      .get<LoanType[]>('/payroll/loan-types')
      .then((r) => setRows(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load loan types'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({
      maxMultipleOfNet: 3.0,
      maxMonths: 24,
      interestRatePct: 0,
      minTenureMonths: 6,
      maxActiveLoans: 1,
      active: true,
    })
    setCurrent(null)
    setModalOpen(true)
  }

  const openEdit = (type: LoanType) => {
    setCurrent(type)
    form.setFieldsValue(type)
    setModalOpen(true)
  }

  const submit = async () => {
    try {
      const values = await form.validateFields()
      if (current) {
        await api.put(`/payroll/loan-types/${current.id}`, values)
        message.success('Loan type updated')
      } else {
        await api.post('/payroll/loan-types', values)
        message.success('Loan type created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const columns: ColumnsType<LoanType> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Max Amount',
      dataIndex: 'maxAmount',
      width: 130,
      align: 'right',
      render: (v?: number) => (v ? `${v.toFixed(2)} AZN` : 'Unlimited'),
    },
    {
      title: 'Max × Net',
      dataIndex: 'maxMultipleOfNet',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(1)}×`,
    },
    {
      title: 'Max Months',
      dataIndex: 'maxMonths',
      width: 110,
      align: 'right',
    },
    {
      title: 'Interest %',
      dataIndex: 'interestRatePct',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Min Tenure',
      dataIndex: 'minTenureMonths',
      width: 110,
      align: 'right',
      render: (v: number) => `${v} months`,
    },
    {
      title: 'Max Active',
      dataIndex: 'maxActiveLoans',
      width: 100,
      align: 'right',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      width: 100,
      render: (_, r) => <a onClick={() => openEdit(r)}>Edit</a>,
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Loan Types</Typography.Title>}
      extra={
        <Button type="primary" onClick={openCreate}>
          New Loan Type
        </Button>
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        open={modalOpen}
        title={current ? 'Edit Loan Type' : 'New Loan Type'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="Save"
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="Code" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="maxAmount"
            label="Max Amount (AZN)"
            tooltip="Leave empty for unlimited"
          >
            <InputNumber min={0} step={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="maxMultipleOfNet"
            label="Max Multiple of Net Salary"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} max={10} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="maxMonths" label="Max Months" rules={[{ required: true }]}>
            <InputNumber min={1} max={120} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="interestRatePct"
            label="Interest Rate %"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} max={100} step={0.1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="minTenureMonths"
            label="Minimum Tenure (months)"
            rules={[{ required: true }]}
            tooltip="Employee must have worked this many months to be eligible"
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="maxActiveLoans"
            label="Max Active Loans"
            rules={[{ required: true }]}
            tooltip="How many concurrent loans of this type can an employee have"
          >
            <InputNumber min={1} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
