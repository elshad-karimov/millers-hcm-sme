// M108 — Benefits administration.
// HR page with three tabs:
//   1. Plans catalog (admin can CRUD)
//   2. Enrolments (HR_WRITE can enrol / waive / terminate)
//   3. My benefits (self-service — every authenticated employee)

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  BENEFIT_TYPE_COLOR,
  BENEFIT_TYPE_LABEL,
  ENROLLMENT_STATUS_COLOR,
  PROVIDER_TYPE_LABEL,
  benefitCategoriesApi,
  benefitProvidersApi,
  benefitsApi,
  type BenefitCategoryRequest,
  type BenefitCategoryResponse,
  type BenefitProviderRequest,
  type BenefitProviderResponse,
  type BenefitProviderType,
  type BenefitType,
  type EnrollmentRequest,
  type EnrollmentResponse,
  type EnrollmentStatus,
  type PlanRequest,
  type PlanResponse,
} from '../api/benefits'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const BENEFIT_TYPE_OPTIONS: { value: BenefitType; label: string }[] = (
  Object.keys(BENEFIT_TYPE_LABEL) as BenefitType[]
).map((k) => ({ value: k, label: BENEFIT_TYPE_LABEL[k] }))

function fmt(n?: number | null) {
  if (n == null) return '—'
  return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ─── Categories tab (HCM_11 M373) ────────────────────────────────────────────

function CategoriesTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<BenefitCategoryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<BenefitCategoryResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<BenefitCategoryRequest>()

  const load = () => {
    setLoading(true)
    benefitCategoriesApi
      .list(activeOnly)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load categories'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      taxable: false,
      requiresProvider: false,
      displayOrder: (rows.at(-1)?.displayOrder ?? 0) + 1,
      active: true,
    })
    setOpen(true)
  }
  const startEdit = (c: BenefitCategoryResponse) => {
    setEditing(c)
    form.setFieldsValue({
      code: c.code,
      name: c.name,
      description: c.description ?? undefined,
      taxable: c.taxable,
      requiresProvider: c.requiresProvider,
      displayOrder: c.displayOrder,
      active: c.active,
    })
    setOpen(true)
  }
  const submit = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await benefitCategoriesApi.update(editing.id, v)
        message.success('Category updated')
      } else {
        await benefitCategoriesApi.create(v)
        message.success('Category created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const cols: ColumnsType<BenefitCategoryResponse> = [
    { title: '#', dataIndex: 'displayOrder', width: 50, align: 'center' },
    {
      title: 'Code',
      dataIndex: 'code',
      width: 130,
      render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Description', dataIndex: 'description', render: (v) => v ?? '—' },
    {
      title: 'Taxable',
      dataIndex: 'taxable',
      width: 90,
      align: 'center',
      render: (v: boolean) => (v ? <Tag color="orange">Taxable</Tag> : <Tag>Exempt</Tag>),
    },
    {
      title: 'Provider',
      dataIndex: 'requiresProvider',
      width: 110,
      align: 'center',
      render: (v: boolean) => (v ? <Tag color="blue">Required</Tag> : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      align: 'center',
      render: (v: boolean) => (v ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>),
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Benefit categories classify plans and drive tax treatment and provider requirements.
        Eight Azerbaijan defaults are seeded; add your own or deactivate ones you don't use.
      </Paragraph>
      <Space>
        <Switch
          checked={activeOnly}
          onChange={setActiveOnly}
          checkedChildren="Active only"
          unCheckedChildren="All"
        />
        {canEdit && <Button type="primary" onClick={startCreate}>New category…</Button>}
      </Space>
      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={rows}
          size="small"
          pagination={false}
          locale={{ emptyText: <Empty description="No categories" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit category — ${editing.code}` : 'New benefit category'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        okText={editing ? 'Save' : 'Create'}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="code" label="Code"
                rules={[{ required: true, message: 'Required' }, { max: 40 }]}>
                <Input placeholder="HEALTH" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={14}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Health Insurance" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="What kind of benefit is this?" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="taxable" label="Taxable benefit-in-kind" valuePropName="checked"
                tooltip="Employer-paid portion is taxed (cash-like allowances).">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="requiresProvider" label="Requires provider" valuePropName="checked"
                tooltip="Plans in this category need an external insurer/vendor.">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="displayOrder" label="Order">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Providers tab (HCM_11 M374) ─────────────────────────────────────────────

const PROVIDER_TYPE_OPTIONS: { value: BenefitProviderType; label: string }[] = (
  Object.keys(PROVIDER_TYPE_LABEL) as BenefitProviderType[]
).map((k) => ({ value: k, label: PROVIDER_TYPE_LABEL[k] }))

function ProvidersTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<BenefitProviderResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<BenefitProviderResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{
    code: string
    name: string
    providerType: BenefitProviderType
    contactName?: string
    contactEmail?: string
    contactPhone?: string
    website?: string
    contractNo?: string
    contract?: [ReturnType<typeof dayjs> | undefined, ReturnType<typeof dayjs> | undefined]
    notes?: string
    active: boolean
  }>()

  const load = () => {
    setLoading(true)
    benefitProvidersApi
      .list(activeOnly)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load providers'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ providerType: 'INSURER', active: true })
    setOpen(true)
  }
  const startEdit = (p: BenefitProviderResponse) => {
    setEditing(p)
    form.setFieldsValue({
      code: p.code,
      name: p.name,
      providerType: p.providerType,
      contactName: p.contactName ?? undefined,
      contactEmail: p.contactEmail ?? undefined,
      contactPhone: p.contactPhone ?? undefined,
      website: p.website ?? undefined,
      contractNo: p.contractNo ?? undefined,
      contract: [
        p.contractStart ? dayjs(p.contractStart) : undefined,
        p.contractEnd ? dayjs(p.contractEnd) : undefined,
      ],
      notes: p.notes ?? undefined,
      active: p.active,
    })
    setOpen(true)
  }
  const submit = async () => {
    const v = await form.validateFields()
    const [start, end] = v.contract ?? [undefined, undefined]
    const req: BenefitProviderRequest = {
      code: v.code,
      name: v.name,
      providerType: v.providerType,
      contactName: v.contactName,
      contactEmail: v.contactEmail,
      contactPhone: v.contactPhone,
      website: v.website,
      contractNo: v.contractNo,
      contractStart: start ? start.format('YYYY-MM-DD') : undefined,
      contractEnd: end ? end.format('YYYY-MM-DD') : undefined,
      notes: v.notes,
      active: v.active,
    }
    setSaving(true)
    try {
      if (editing) {
        await benefitProvidersApi.update(editing.id, req)
        message.success('Provider updated')
      } else {
        await benefitProvidersApi.create(req)
        message.success('Provider created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const cols: ColumnsType<BenefitProviderResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 120,
      render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'providerType',
      width: 130,
      render: (t: BenefitProviderType) => <Tag>{PROVIDER_TYPE_LABEL[t]}</Tag>,
    },
    { title: 'Contact', dataIndex: 'contactName', width: 150, render: (v) => v ?? '—' },
    { title: 'Contract #', dataIndex: 'contractNo', width: 130, render: (v) => v ?? '—' },
    {
      title: 'Contract window',
      width: 190,
      render: (_, r) => (
        <Text style={{ fontSize: 12 }}>
          {r.contractStart ?? '—'} → {r.contractEnd ?? 'open'}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      align: 'center',
      render: (v: boolean) => (v ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>),
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Insurers, pension funds, clinics and vendors that supply benefit plans. Plans link to a
        provider so you can track contracts and reconcile against provider files.
      </Paragraph>
      <Space>
        <Switch
          checked={activeOnly}
          onChange={setActiveOnly}
          checkedChildren="Active only"
          unCheckedChildren="All"
        />
        {canEdit && <Button type="primary" onClick={startCreate}>New provider…</Button>}
      </Space>
      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={rows}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No providers yet" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit provider — ${editing.code}` : 'New benefit provider'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        width={680}
        okText={editing ? 'Save' : 'Create'}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={7}>
              <Form.Item name="code" label="Code"
                rules={[{ required: true, message: 'Required' }, { max: 40 }]}>
                <Input placeholder="PASHA-INS" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={11}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Pasha Insurance OJSC" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="providerType" label="Type" rules={[{ required: true }]}>
                <Select options={PROVIDER_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="contactName" label="Contact name">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="contactEmail" label="Contact email"
                rules={[{ type: 'email', message: 'Invalid email' }]}>
                <Input placeholder="account@provider.az" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="contactPhone" label="Contact phone">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="website" label="Website">
                <Input placeholder="https://…" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="contractNo" label="Contract number">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="contract" label="Contract window (start / end)">
                <DatePicker.RangePicker style={{ width: '100%' }} allowEmpty={[true, true]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Plans tab ───────────────────────────────────────────────────────────────

function PlansTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [plans, setPlans] = useState<PlanResponse[]>([])
  const [providers, setProviders] = useState<BenefitProviderResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PlanResponse | null>(null)
  const [form] = Form.useForm<{
    code: string
    name: string
    description?: string
    benefitType: BenefitType
    provider?: string
    providerId?: string | null
    coverageDetails?: string
    eligibility?: string
    employerContribution?: number
    employeeContribution?: number
    currency?: string
    window: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs> | undefined]
    active: boolean
  }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    benefitsApi
      .listPlans(activeOnly)
      .then(setPlans)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load plans'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])
  useEffect(() => {
    benefitProvidersApi.list(true).then(setProviders).catch(() => setProviders([]))
  }, [])

  const totals = useMemo(() => {
    const active = plans.filter((p) => p.active).length
    const enrolments = plans.reduce((s, p) => s + p.activeEnrolments, 0)
    const monthly = plans.reduce(
      (s, p) => s + (p.activeEnrolments * (p.employerContribution ?? 0)), 0)
    return { active, enrolments, monthly }
  }, [plans])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      benefitType: 'HEALTH',
      currency: 'AZN',
      active: true,
      employerContribution: 0,
      employeeContribution: 0,
      window: [dayjs().startOf('year'), undefined],
    })
    setOpen(true)
  }

  const startEdit = (plan: PlanResponse) => {
    setEditing(plan)
    form.setFieldsValue({
      code: plan.code,
      name: plan.name,
      description: plan.description ?? undefined,
      benefitType: plan.benefitType,
      provider: plan.provider ?? undefined,
      providerId: plan.providerId ?? undefined,
      coverageDetails: plan.coverageDetails ?? undefined,
      eligibility: plan.eligibility ?? undefined,
      employerContribution: plan.employerContribution,
      employeeContribution: plan.employeeContribution,
      currency: plan.currency,
      window: [dayjs(plan.effectiveFrom), plan.effectiveTo ? dayjs(plan.effectiveTo) : undefined],
      active: plan.active,
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const [from, to] = v.window
    const req: PlanRequest = {
      code: v.code,
      name: v.name,
      description: v.description,
      benefitType: v.benefitType,
      provider: v.provider,
      providerId: v.providerId ?? null,
      coverageDetails: v.coverageDetails,
      eligibility: v.eligibility,
      employerContribution: v.employerContribution ?? 0,
      employeeContribution: v.employeeContribution ?? 0,
      currency: v.currency || 'AZN',
      effectiveFrom: from.format('YYYY-MM-DD'),
      effectiveTo: to ? to.format('YYYY-MM-DD') : undefined,
      active: v.active,
    }
    setSaving(true)
    try {
      if (editing) {
        await benefitsApi.updatePlan(editing.id, req)
        message.success('Plan updated')
      } else {
        await benefitsApi.createPlan(req)
        message.success('Plan created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const cols: ColumnsType<PlanResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 110,
      render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'benefitType',
      width: 130,
      render: (t: BenefitType) => (
        <Tag color={BENEFIT_TYPE_COLOR[t]}>{BENEFIT_TYPE_LABEL[t]}</Tag>
      ),
    },
    { title: 'Provider', width: 170, render: (_, r) => r.providerName ?? r.provider ?? '—' },
    {
      title: 'Employer / mo',
      align: 'right',
      width: 130,
      render: (_, r) => `${fmt(r.employerContribution)} ${r.currency}`,
    },
    {
      title: 'Employee / mo',
      align: 'right',
      width: 130,
      render: (_, r) => `${fmt(r.employeeContribution)} ${r.currency}`,
    },
    {
      title: 'Enrolled',
      align: 'center',
      width: 100,
      render: (_, r) => <Tag color="blue">{r.activeEnrolments}</Tag>,
    },
    {
      title: 'Window',
      width: 200,
      render: (_, r) => (
        <Text style={{ fontSize: 12 }}>
          {dayjs(r.effectiveFrom).format('YYYY-MM-DD')} → {r.effectiveTo ? dayjs(r.effectiveTo).format('YYYY-MM-DD') : 'open'}
        </Text>
      ),
    },
    {
      title: 'Status',
      width: 90,
      align: 'center',
      render: (_, r) => r.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col span={6}><Statistic title="Total plans" value={plans.length} /></Col>
        <Col span={6}><Statistic title="Active plans" value={totals.active} /></Col>
        <Col span={6}><Statistic title="Active enrolments" value={totals.enrolments} /></Col>
        <Col span={6}>
          <Statistic
            title="Monthly employer spend"
            value={totals.monthly}
            precision={2}
            suffix="AZN"
          />
        </Col>
      </Row>

      <Space>
        <Switch
          checked={activeOnly}
          onChange={setActiveOnly}
          checkedChildren="Active only"
          unCheckedChildren="All plans"
        />
        {canEdit && <Button type="primary" onClick={startCreate}>New plan…</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={plans}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No benefit plans" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit plan — ${editing.code}` : 'New benefit plan'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        width={680}
        okText={editing ? 'Save' : 'Create'}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="code" label="Code"
                rules={[{ required: true, message: 'Required' }, { max: 40 }]}>
                <Input placeholder="HEALTH-FAM-26" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Family health insurance" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="benefitType" label="Type" rules={[{ required: true }]}>
                <Select options={BENEFIT_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="providerId" label="Provider (from master)"
                tooltip="Link to the provider master for contract tracking & reconciliation.">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="Pick a provider…"
                  options={providers.map((p) => ({
                    value: p.id,
                    label: `${p.name} (${PROVIDER_TYPE_LABEL[p.providerType]})`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="provider" label="Provider (free text)"
                tooltip="Legacy free-text name; used when there's no provider-master entry.">
                <Input placeholder="Pasha Insurance" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="One-liner — what does this plan cover?" />
          </Form.Item>

          <Form.Item name="coverageDetails" label="Coverage details">
            <Input.TextArea rows={3} placeholder="Inpatient + outpatient + dependants…" />
          </Form.Item>

          <Form.Item name="eligibility" label="Eligibility">
            <Input.TextArea rows={2} placeholder="After 3 months tenure, full-time only." />
          </Form.Item>

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="employerContribution" label="Employer / mo"
                rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="employeeContribution" label="Employee / mo"
                rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="currency" label="Currency">
                <Input placeholder="AZN" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="window" label="Effective window (from / to — leave blank for open-ended)"
                rules={[{ required: true, message: 'Pick start date' }]}>
                <DatePicker.RangePicker style={{ width: '100%' }} allowEmpty={[false, true]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Enrolments tab ──────────────────────────────────────────────────────────

function EnrolmentsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEnrol = hasRole(...RoleSets.HR_WRITE)

  const [rows, setRows] = useState<EnrollmentResponse[]>([])
  const [plans, setPlans] = useState<PlanResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState<EnrollmentStatus | undefined>('ENROLLED')
  const [open, setOpen] = useState(false)
  const [enrolForm] = Form.useForm<{
    planId: string
    employeeId: string
    startDate: ReturnType<typeof dayjs>
    status: EnrollmentStatus
    dependentsCovered?: number
    notes?: string
  }>()
  const [saving, setSaving] = useState(false)
  const [terminating, setTerminating] = useState<EnrollmentResponse | null>(null)
  const [termForm] = Form.useForm<{
    endDate: ReturnType<typeof dayjs>
    terminationReason?: string
  }>()

  const load = () => {
    setLoading(true)
    Promise.all([
      benefitsApi.listEnrolments({ status: filterStatus }),
      benefitsApi.listPlans(true),
    ])
      .then(([r, p]) => { setRows(r); setPlans(p) })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load enrolments'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [filterStatus])

  const startEnrol = () => {
    enrolForm.resetFields()
    enrolForm.setFieldsValue({
      status: 'ENROLLED',
      startDate: dayjs(),
      dependentsCovered: 0,
    })
    setOpen(true)
  }

  const submitEnrol = async () => {
    const v = await enrolForm.validateFields()
    const req: EnrollmentRequest = {
      planId: v.planId,
      employeeId: v.employeeId,
      startDate: v.startDate.format('YYYY-MM-DD'),
      status: v.status,
      dependentsCovered: v.dependentsCovered ?? 0,
      notes: v.notes,
    }
    setSaving(true)
    try {
      await benefitsApi.enrol(req)
      message.success('Enrolment recorded')
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const submitTerminate = async () => {
    if (!terminating) return
    const v = await termForm.validateFields()
    try {
      await benefitsApi.terminate(terminating.id, {
        endDate: v.endDate.format('YYYY-MM-DD'),
        terminationReason: v.terminationReason,
      })
      message.success('Enrolment terminated')
      setTerminating(null)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    }
  }

  const cols: ColumnsType<EnrollmentResponse> = [
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.employeeName ?? '—'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            <code>{r.employeeId.slice(0, 8)}</code>
          </Text>
        </Space>
      ),
    },
    {
      title: 'Plan',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text>{r.planName ?? '—'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.planCode}</Text>
        </Space>
      ),
    },
    {
      title: 'Type',
      width: 130,
      render: (_, r) => r.benefitType ? (
        <Tag color={BENEFIT_TYPE_COLOR[r.benefitType]}>{BENEFIT_TYPE_LABEL[r.benefitType]}</Tag>
      ) : '—',
    },
    {
      title: 'Status',
      width: 110,
      render: (_, r) => (
        <Tag color={ENROLLMENT_STATUS_COLOR[r.status]}>{r.status}</Tag>
      ),
    },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    { title: 'End', dataIndex: 'endDate', width: 110, render: (v) => v ?? '—' },
    {
      title: 'Deps',
      dataIndex: 'dependentsCovered',
      width: 70,
      align: 'center',
    },
    {
      title: 'Employer / mo',
      align: 'right',
      width: 130,
      render: (_, r) => fmt(r.employerContribution),
    },
    {
      title: '',
      width: 110,
      render: (_, r) => canEnrol && r.status !== 'TERMINATED' ? (
        <Popconfirm
          title="Terminate enrolment?"
          description="You'll enter the end date next."
          onConfirm={() => {
            termForm.resetFields()
            termForm.setFieldsValue({ endDate: dayjs() })
            setTerminating(r)
          }}
        >
          <Button size="small" danger>Terminate</Button>
        </Popconfirm>
      ) : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space>
          <Text>Filter:</Text>
          <Select
            style={{ width: 160 }}
            value={filterStatus}
            allowClear
            onChange={(v) => setFilterStatus(v as EnrollmentStatus | undefined)}
            options={[
              { value: 'ENROLLED', label: 'Enrolled' },
              { value: 'WAIVED', label: 'Waived' },
              { value: 'TERMINATED', label: 'Terminated' },
            ]}
            placeholder="All statuses"
          />
          <Text type="secondary">{rows.length} row{rows.length === 1 ? '' : 's'}</Text>
        </Space>
        {canEnrol && <Button type="primary" onClick={startEnrol}>Enrol employee…</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={rows}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No enrolments" /> }}
        />
      </Card>

      <Modal
        open={open}
        title="Enrol employee in a benefit plan"
        onCancel={() => setOpen(false)}
        onOk={submitEnrol}
        confirmLoading={saving}
        okText="Enrol"
      >
        <Form form={enrolForm} layout="vertical">
          <Form.Item name="planId" label="Plan" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={plans.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.name} (${BENEFIT_TYPE_LABEL[p.benefitType]})`,
              }))}
              placeholder="Pick a plan"
            />
          </Form.Item>
          <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}
            extra="Paste the employee UUID from their profile page.">
            <Input placeholder="00000000-0000-0000-0000-000000000000" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="startDate" label="Start date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="Status">
                <Select options={[
                  { value: 'ENROLLED', label: 'Enrolled' },
                  { value: 'WAIVED', label: 'Waived (offered, declined)' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="dependentsCovered" label="Dependants">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={2} placeholder="Special arrangement, custom coverage, etc." />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!terminating}
        title={terminating ? `Terminate enrolment — ${terminating.employeeName} / ${terminating.planCode}` : ''}
        onCancel={() => setTerminating(null)}
        onOk={submitTerminate}
        okText="Terminate"
        okButtonProps={{ danger: true }}
      >
        <Form form={termForm} layout="vertical">
          <Form.Item name="endDate" label="End date" rules={[{ required: true }]}
            extra="Last day of coverage.">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="terminationReason" label="Reason">
            <Input.TextArea rows={3} placeholder="Resignation, plan change, etc." />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── My benefits tab ─────────────────────────────────────────────────────────

function MyBenefitsTab() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<EnrollmentResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    benefitsApi
      .myEnrolments()
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load benefits'))
      .finally(() => setLoading(false))
  }, [message])

  const active = useMemo(() => rows.filter((r) => r.status === 'ENROLLED'), [rows])
  const totalEmployee = useMemo(
    () => active.reduce((s, r) => s + (r.employeeContribution ?? 0), 0),
    [active],
  )
  const totalEmployer = useMemo(
    () => active.reduce((s, r) => s + (r.employerContribution ?? 0), 0),
    [active],
  )

  if (loading) return <Spin />

  if (rows.length === 0) {
    return <Empty description="You aren't enrolled in any benefits yet. Ask HR if you think this is wrong." />
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col span={8}>
          <Statistic title="Active plans" value={active.length} />
        </Col>
        <Col span={8}>
          <Statistic title="Your monthly contribution" value={totalEmployee} precision={2} suffix="AZN" />
        </Col>
        <Col span={8}>
          <Statistic title="Employer monthly contribution" value={totalEmployer} precision={2} suffix="AZN" />
        </Col>
      </Row>

      {rows.map((r) => (
        <Card
          key={r.id}
          size="small"
          title={
            <Space>
              {r.benefitType && (
                <Tag color={BENEFIT_TYPE_COLOR[r.benefitType]}>{BENEFIT_TYPE_LABEL[r.benefitType]}</Tag>
              )}
              <Text strong>{r.planName ?? '—'}</Text>
              <Tag color={ENROLLMENT_STATUS_COLOR[r.status]}>{r.status}</Tag>
            </Space>
          }
        >
          <Row gutter={16}>
            <Col span={6}><Statistic title="Start date" value={r.startDate} /></Col>
            <Col span={6}><Statistic title="End date" value={r.endDate ?? '—'} /></Col>
            <Col span={6}>
              <Statistic
                title="Your share / mo"
                value={r.employeeContribution ?? 0}
                precision={2}
                suffix="AZN"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Employer / mo"
                value={r.employerContribution ?? 0}
                precision={2}
                suffix="AZN"
              />
            </Col>
          </Row>
          {r.notes && (
            <Tooltip title={r.notes}>
              <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }} ellipsis={{ rows: 1 }}>
                Note: {r.notes}
              </Paragraph>
            </Tooltip>
          )}
        </Card>
      ))}
    </Space>
  )
}

// ─── Page shell ──────────────────────────────────────────────────────────────

export function BenefitsPage() {
  const { hasRole } = useAuth()
  const isHr = hasRole(...RoleSets.HR_PLUS_MANAGERS_READ)

  // For non-HR employees, only show the self-service tab.
  const items = isHr
    ? [
        { key: 'categories', label: 'Categories', children: <CategoriesTab /> },
        { key: 'providers', label: 'Providers', children: <ProvidersTab /> },
        { key: 'plans', label: 'Plans catalog', children: <PlansTab /> },
        { key: 'enrolments', label: 'Enrolments', children: <EnrolmentsTab /> },
        { key: 'me', label: 'My benefits', children: <MyBenefitsTab /> },
      ]
    : [{ key: 'me', label: 'My benefits', children: <MyBenefitsTab /> }]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Benefits administration</Title>
      <Text type="secondary">
        Health, pension, life-insurance and other plans, plus per-employee enrolment.
        HR Admin manages the catalog; HR specialists handle day-to-day enrol/waive/terminate.
      </Text>
      <Tabs items={items} defaultActiveKey={isHr ? 'plans' : 'me'} />
    </Space>
  )
}
