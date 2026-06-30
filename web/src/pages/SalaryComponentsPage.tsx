import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import { LockOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  payrollApi,
  type SalaryComponent,
  type SalaryComponentRequest,
  type ComponentKind,
  type CalculationMethod,
} from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const COMPONENT_KIND_COLOR: Record<ComponentKind, string> = {
  EARNING: 'green',
  DEDUCTION: 'red',
}

interface FormValues {
  code: string
  name: string
  kind: ComponentKind
  calculationMethod: CalculationMethod
  defaultAmount?: number
  percentage?: number
  isTaxable: boolean
  contributionExempt: boolean
}

export function SalaryComponentsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.PAYROLL_WRITE)

  const [rows, setRows] = useState<SalaryComponent[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<SalaryComponent | null>(null)
  const [form] = Form.useForm<FormValues>()

  const calculationMethod = Form.useWatch('calculationMethod', form)
  const isTaxable = Form.useWatch('isTaxable', form)

  const load = () => {
    setLoading(true)
    payrollApi
      .components()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load components'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ isTaxable: true, contributionExempt: false })
    setDrawerOpen(true)
  }

  const openEdit = (c: SalaryComponent) => {
    if (c.isStatutory) {
      message.warning('Statutory components cannot be edited')
      return
    }
    setEditing(c)
    form.setFieldsValue({
      code: c.code,
      name: c.name,
      kind: c.kind,
      calculationMethod: c.calculationMethod,
      defaultAmount: c.defaultAmount ?? undefined,
      percentage: c.percentage ?? undefined,
      isTaxable: c.isTaxable,
      contributionExempt: c.contributionExempt,
    })
    setDrawerOpen(true)
  }

  const submit = async (v: FormValues) => {
    const payload: SalaryComponentRequest = {
      code: v.code,
      name: v.name,
      kind: v.kind,
      calculationMethod: v.calculationMethod,
      isTaxable: v.isTaxable,
      contributionExempt: v.contributionExempt,
    }
    if (v.calculationMethod === 'PERCENTAGE_OF_BASE') {
      payload.percentage = v.percentage
    } else if (
      v.calculationMethod === 'FIXED_AMOUNT' ||
      v.calculationMethod === 'FLAT_RATE'
    ) {
      payload.defaultAmount = v.defaultAmount
    }

    try {
      if (editing) {
        await payrollApi.updateComponent(editing.id, payload)
        message.success('Component updated')
      } else {
        await payrollApi.createComponent(payload)
        message.success('Component created')
      }
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const handleDelete = async (c: SalaryComponent) => {
    if (c.isStatutory) {
      message.error('Statutory components cannot be deleted')
      return
    }
    try {
      await payrollApi.deleteComponent(c.id)
      message.success('Component deleted')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Delete failed',
      )
    }
  }

  const columns: ColumnsType<SalaryComponent> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Kind',
      dataIndex: 'kind',
      width: 120,
      render: (k: ComponentKind) => <Tag color={COMPONENT_KIND_COLOR[k]}>{k}</Tag>,
    },
    {
      title: 'Calculation',
      dataIndex: 'calculationMethod',
      width: 180,
      render: (m: CalculationMethod) => m.replace(/_/g, ' '),
    },
    {
      title: 'Taxable',
      dataIndex: 'isTaxable',
      width: 80,
      align: 'center',
      render: (v: boolean) =>
        v ? (
          <CheckOutlined style={{ color: 'green' }} />
        ) : (
          <CloseOutlined style={{ color: 'red' }} />
        ),
    },
    {
      title: 'DSMF Exempt',
      dataIndex: 'contributionExempt',
      width: 120,
      align: 'center',
      render: (v: boolean) =>
        v ? (
          <CheckOutlined style={{ color: 'green' }} />
        ) : (
          <CloseOutlined style={{ color: 'red' }} />
        ),
    },
    {
      title: 'Statutory',
      dataIndex: 'isStatutory',
      width: 100,
      align: 'center',
      render: (v: boolean) => (v ? <LockOutlined style={{ color: 'orange' }} /> : null),
    },
    {
      title: 'Active',
      dataIndex: 'isActive',
      width: 80,
      align: 'center',
      render: (v: boolean) =>
        v ? (
          <CheckOutlined style={{ color: 'green' }} />
        ) : (
          <CloseOutlined style={{ color: 'default' }} />
        ),
    },
    {
      title: 'Actions',
      width: 140,
      render: (_, c) =>
        canWrite && !c.isStatutory ? (
          <>
            <Button size="small" onClick={() => openEdit(c)}>
              Edit
            </Button>
            <Button size="small" danger onClick={() => handleDelete(c)} style={{ marginLeft: 8 }}>
              Delete
            </Button>
          </>
        ) : null,
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Salary Components</Typography.Title>}
      extra={
        canWrite && (
          <Button type="primary" onClick={openCreate}>
            Create Component
          </Button>
        )
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Drawer
        open={drawerOpen}
        title={editing ? 'Edit Component' : 'Create Component'}
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
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Code is required' }]}
          >
            <Input disabled={!!editing} maxLength={20} />
          </Form.Item>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="kind"
            label="Kind"
            rules={[{ required: true, message: 'Kind is required' }]}
          >
            <Select
              options={[
                { value: 'EARNING', label: 'Earning' },
                { value: 'DEDUCTION', label: 'Deduction' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="calculationMethod"
            label="Calculation Method"
            rules={[{ required: true, message: 'Calculation method is required' }]}
          >
            <Select
              options={[
                { value: 'FIXED_AMOUNT', label: 'Fixed Amount' },
                { value: 'PERCENTAGE_OF_BASE', label: 'Percentage of Base' },
                { value: 'FLAT_RATE', label: 'Flat Rate' },
              ]}
            />
          </Form.Item>
          {calculationMethod === 'PERCENTAGE_OF_BASE' && (
            <Form.Item
              name="percentage"
              label="Percentage (%)"
              rules={[{ required: true, message: 'Percentage is required' }]}
            >
              <InputNumber min={0} max={100} step={0.01} style={{ width: '100%' }} />
            </Form.Item>
          )}
          {(calculationMethod === 'FIXED_AMOUNT' || calculationMethod === 'FLAT_RATE') && (
            <Form.Item name="defaultAmount" label="Default Amount">
              <InputNumber min={0} step={10} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item name="isTaxable" label="Is Taxable" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item
            name="contributionExempt"
            label={
              <Tooltip
                title={
                  isTaxable
                    ? 'Taxable components are always included in DSMF base'
                    : ''
                }
              >
                Contribution Exempt
              </Tooltip>
            }
            valuePropName="checked"
          >
            <Switch disabled={isTaxable} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  )
}
