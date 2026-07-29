import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
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
  compBenefitsApi,
  type AllowanceCategory,
  type AllowanceStatus,
  type AllowanceType,
  type AllowanceTypeRequest,
  type EmployeeAllowance,
  type EmployeeAllowanceRequest,
} from '../api/compbenefits'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CATEGORIES: AllowanceCategory[] = [
  'TRANSPORT',
  'MEAL',
  'MOBILE',
  'HOUSING',
  'FUEL',
  'FAMILY',
  'EDUCATION',
  'MEDICAL',
  'OTHER',
]

const CATEGORY_COLOR: Record<AllowanceCategory, string> = {
  TRANSPORT: 'geekblue',
  MEAL: 'orange',
  MOBILE: 'cyan',
  HOUSING: 'purple',
  FUEL: 'red',
  FAMILY: 'magenta',
  EDUCATION: 'gold',
  MEDICAL: 'volcano',
  OTHER: 'default',
}

const STATUS_COLOR: Record<AllowanceStatus, string> = {
  ACTIVE: 'green',
  ENDED: 'default',
  CANCELLED: 'red',
}

interface NewTypeForm {
  code: string
  name: string
  description?: string
  category: AllowanceCategory
  taxable: boolean
  recurring: boolean
  defaultAmount?: number
  currency: string
  active: boolean
}

interface AssignForm {
  employeeId: string
  allowanceTypeId: string
  amount: number
  currency: string
  effectiveFrom: dayjs.Dayjs
  effectiveTo?: dayjs.Dayjs
  note?: string
}

export function AllowancesPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canEdit = hasRole(...RoleSets.HR_WRITE)

  const [types, setTypes] = useState<AllowanceType[]>([])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [allowances, setAllowances] = useState<EmployeeAllowance[]>([])
  const [loading, setLoading] = useState(false)

  const [typeOpen, setTypeOpen] = useState(false)
  const [typeForm] = Form.useForm<NewTypeForm>()
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignForm] = Form.useForm<AssignForm>()

  useEffect(() => {
    compBenefitsApi.allowanceTypes(false).then(setTypes)
  }, [])

  useEffect(() => {
    if (!employeeId) {
      setAllowances([])
      return
    }
    setLoading(true)
    compBenefitsApi
      .allowances(employeeId)
      .then(setAllowances)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load allowances'))
      .finally(() => setLoading(false))
  }, [employeeId, message])

  const typeMap = useMemo(() => new Map(types.map((t) => [t.id, t])), [types])

  const openAssign = () => {
    if (!employeeId) {
      message.warning('Select an employee first')
      return
    }
    assignForm.resetFields()
    assignForm.setFieldsValue({
      employeeId,
      currency: 'AZN',
      effectiveFrom: dayjs(),
    })
    setAssignOpen(true)
  }

  const onCreateType = async (v: NewTypeForm) => {
    const payload: AllowanceTypeRequest = { ...v }
    try {
      await compBenefitsApi.createAllowanceType(payload)
      message.success('Allowance type created')
      setTypeOpen(false)
      typeForm.resetFields()
      const t = await compBenefitsApi.allowanceTypes(false)
      setTypes(t)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onAssign = async (v: AssignForm) => {
    const payload: EmployeeAllowanceRequest = {
      employeeId: v.employeeId,
      allowanceTypeId: v.allowanceTypeId,
      amount: v.amount,
      currency: v.currency,
      effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo ? v.effectiveTo.format('YYYY-MM-DD') : undefined,
      note: v.note,
    }
    try {
      await compBenefitsApi.createAllowance(payload)
      message.success('Allowance assigned')
      setAssignOpen(false)
      const a = await compBenefitsApi.allowances(v.employeeId)
      setAllowances(a)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onEnd = (a: EmployeeAllowance) => {
    modal.confirm({
      title: `End allowance ${a.allowanceNo}?`,
      content: 'Sets the effective_to to today and flips status to ENDED.',
      onOk: async () => {
        try {
          await compBenefitsApi.endAllowance(a.id, dayjs().format('YYYY-MM-DD'))
          message.success('Allowance ended')
          if (employeeId) {
            const list = await compBenefitsApi.allowances(employeeId)
            setAllowances(list)
          }
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'End failed',
          )
        }
      },
    })
  }

  const typeColumns: ColumnsType<AllowanceType> = [
    { title: 'Code', dataIndex: 'code', width: 140 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 130,
      render: (c: AllowanceCategory) => <Tag color={CATEGORY_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Default',
      width: 130,
      render: (_, r) => (r.defaultAmount != null ? `${r.defaultAmount} ${r.currency}` : '—'),
    },
    { title: 'Taxable', dataIndex: 'taxable', width: 90, render: (b: boolean) => (b ? 'Yes' : 'No') },
    { title: 'Recurring', dataIndex: 'recurring', width: 100, render: (b: boolean) => (b ? 'Yes' : 'No') },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
  ]

  const allowanceColumns: ColumnsType<EmployeeAllowance> = [
    { title: 'Allowance #', dataIndex: 'allowanceNo', width: 130 },
    {
      title: 'Type',
      dataIndex: 'allowanceTypeId',
      render: (id: string) => {
        const t = typeMap.get(id)
        return t ? `${t.code} — ${t.name}` : id
      },
    },
    {
      title: 'Amount',
      render: (_, r) => `${r.amount} ${r.currency}`,
      width: 130,
    },
    { title: 'From', dataIndex: 'effectiveFrom', width: 110 },
    { title: 'To', dataIndex: 'effectiveTo', width: 110, render: (v: string | null) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: AllowanceStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    canEdit
      ? {
          title: '',
          width: 100,
          render: (_, r) =>
            r.status === 'ACTIVE' && (
              <Button size="small" onClick={() => onEnd(r)}>
                End
              </Button>
            ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Allowance types
          </Typography.Title>
        }
        extra={
          canEdit && (
            <Button type="primary" onClick={() => setTypeOpen(true)}>
              New type
            </Button>
          )
        }
      >
        <Table rowKey="id" size="small" columns={typeColumns} dataSource={types} pagination={false} />
      </Card>

      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Employee allowances
          </Typography.Title>
        }
        extra={
          canEdit && (
            <Button type="primary" onClick={openAssign} disabled={!employeeId}>
              Assign allowance
            </Button>
          )
        }
      >
        <Space style={{ marginBottom: 12 }}>
          <EmployeePicker
            placeholder="Select employee"
            style={{ width: 320 }}
            value={employeeId}
            onChange={setEmployeeId}
            allowClear
          />
        </Space>
        {employeeId ? (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            columns={allowanceColumns}
            dataSource={allowances}
            pagination={false}
            locale={{ emptyText: 'No allowances yet' }}
          />
        ) : (
          <Typography.Text type="secondary">
            Pick an employee to view and assign allowances.
          </Typography.Text>
        )}
      </Card>

      <Modal
        open={typeOpen}
        title="New allowance type"
        onCancel={() => setTypeOpen(false)}
        onOk={() => typeForm.submit()}
        okText="Create"
      >
        <Form
          form={typeForm}
          layout="vertical"
          onFinish={onCreateType}
          initialValues={{ taxable: true, recurring: true, currency: 'AZN', active: true }}
        >
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 40 }]}>
                <Input placeholder="e.g. TRANSPORT" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="category" label="Category" rules={[{ required: true }]}>
            <Select options={CATEGORIES.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="defaultAmount" label="Default amount">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="taxable" valuePropName="checked">
                <Typography.Text>Taxable</Typography.Text>
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="recurring" valuePropName="checked">
                <Typography.Text>Recurring</Typography.Text>
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" valuePropName="checked">
                <Typography.Text>Active</Typography.Text>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={assignOpen}
        title="Assign allowance"
        onCancel={() => setAssignOpen(false)}
        onOk={() => assignForm.submit()}
        okText="Assign"
      >
        <Form form={assignForm} layout="vertical" onFinish={onAssign}>
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Form.Item name="allowanceTypeId" label="Allowance type" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={types
                .filter((t) => t.active)
                .map((t) => ({
                  value: t.id,
                  label: `${t.code} — ${t.name}${t.defaultAmount != null ? ` (default ${t.defaultAmount})` : ''}`,
                }))}
              onChange={(v) => {
                const t = typeMap.get(v)
                if (t?.defaultAmount != null) {
                  assignForm.setFieldsValue({
                    amount: Number(t.defaultAmount),
                    currency: t.currency,
                  })
                }
              }}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={10}>
              <Form.Item name="amount" label="Amount" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="currency" label="Currency" rules={[{ required: true }]}>
                <Input maxLength={3} />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="effectiveFrom" label="Effective from" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="effectiveTo" label="Effective to (optional — leave blank for open-ended)">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="note" label="Note">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
