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
  CLAIM_STATUS_COLOR,
  COVERAGE_TIER_LABEL,
  ENROLLMENT_STATUS_COLOR,
  LIFE_EVENT_LABEL,
  LIFE_EVENT_STATUS_COLOR,
  PROVIDER_TYPE_LABEL,
  benefitCategoriesApi,
  benefitPlanConfigApi,
  benefitProvidersApi,
  benefitReconcileApi,
  benefitsApi,
  claimsApi,
  lifeEventsApi,
  openEnrollmentApi,
  type BenefitCategoryRequest,
  type BenefitCategoryResponse,
  type BenefitDashboard,
  type BenefitProviderRequest,
  type BenefitProviderResponse,
  type BenefitProviderType,
  type BenefitType,
  type ClaimItemRequest,
  type ClaimRequest,
  type ClaimResponse,
  type CoverageTier,
  type EligibilityRule,
  type EnrollmentRequest,
  type EnrollmentResponse,
  type EnrollmentStatus,
  type LifeEventRequest,
  type LifeEventResponse,
  type LifeEventType,
  type OpenEnrollmentWindowRequest,
  type OpenEnrollmentWindowResponse,
  type PlanRequest,
  type PlanResponse,
  type PlanTier,
  type ReconcileResponse,
} from '../api/benefits'
import { profileTabsApi, type Dependent } from '../api/profileTabs'
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

function ReconcileModal({
  providers,
  open,
  onClose,
}: {
  providers: BenefitProviderResponse[]
  open: boolean
  onClose: () => void
}) {
  const { message } = AntdApp.useApp()
  const [providerId, setProviderId] = useState<string | undefined>(undefined)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ReconcileResponse | null>(null)

  const run = async () => {
    if (!providerId) { message.error('Pick a provider'); return }
    // Parse "reference,amount" lines (CSV/paste from the provider roster).
    const parsed = text.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => {
      const [ref, amt] = l.split(/[,;\t]/)
      return { reference: (ref ?? '').trim(), amount: Number((amt ?? '0').trim()) || 0 }
    }).filter((r) => r.reference)
    if (!parsed.length) { message.error('Paste at least one "employeeNo,amount" line'); return }
    setBusy(true)
    try { setResult(await benefitReconcileApi.reconcile(providerId, parsed)) }
    catch (e) { message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Reconcile failed') }
    finally { setBusy(false) }
  }

  const RESULT_COLOR: Record<string, string> = {
    MATCHED: 'green', AMOUNT_MISMATCH: 'orange', MISSING_IN_FILE: 'red', EXTRA_IN_FILE: 'gold',
  }
  const cols: ColumnsType<ReconcileResponse['lines'][number]> = [
    { title: 'Employee #', dataIndex: 'reference', width: 130 },
    { title: 'Name', dataIndex: 'employeeName', render: (v) => v ?? '—' },
    { title: 'System', align: 'right', width: 110, render: (_, r) => r.systemAmount != null ? fmt(r.systemAmount) : '—' },
    { title: 'File', align: 'right', width: 110, render: (_, r) => r.fileAmount != null ? fmt(r.fileAmount) : '—' },
    { title: 'Result', width: 160, render: (_, r) => <Tag color={RESULT_COLOR[r.result]}>{r.result.replace(/_/g, ' ')}</Tag> },
  ]

  return (
    <Modal open={open} title="Reconcile provider roster file" width={760}
      onCancel={() => { setResult(null); onClose() }} onOk={run} confirmLoading={busy} okText="Reconcile"
      cancelText="Close">
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Select style={{ width: '100%' }} placeholder="Provider" value={providerId} onChange={setProviderId}
          showSearch optionFilterProp="label"
          options={providers.map((p) => ({ value: p.id, label: p.name }))} />
        <div>
          <Text type="secondary">Paste the provider's roster, one member per line: <code>employeeNo,amount</code></Text>
          <Input.TextArea rows={5} value={text} onChange={(e) => setText(e.target.value)}
            placeholder={'E-00012,150\nE-00015,190'} />
        </div>
        {result && (
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            <Space wrap>
              <Tag color="green">Matched {result.matched}</Tag>
              <Tag color="orange">Amount mismatch {result.amountMismatch}</Tag>
              <Tag color="red">Missing in file {result.missingInFile}</Tag>
              <Tag color="gold">Extra in file {result.extraInFile}</Tag>
              <Text type="secondary">System total {fmt(result.systemTotal)} · File total {fmt(result.fileTotal)}</Text>
            </Space>
            <Table rowKey="reference" columns={cols} dataSource={result.lines} size="small" pagination={{ pageSize: 10 }} />
          </Space>
        )}
      </Space>
    </Modal>
  )
}

function ProvidersTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [reconcileOpen, setReconcileOpen] = useState(false)
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
        {canEdit && <Button onClick={() => setReconcileOpen(true)}>Reconcile file…</Button>}
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

      <ReconcileModal providers={rows} open={reconcileOpen} onClose={() => setReconcileOpen(false)} />
    </Space>
  )
}

// ─── Plan config modal: coverage tiers + eligibility rules (HCM_11 M375) ─────

const COVERAGE_TIER_OPTIONS: { value: CoverageTier; label: string }[] = (
  Object.keys(COVERAGE_TIER_LABEL) as CoverageTier[]
).map((k) => ({ value: k, label: COVERAGE_TIER_LABEL[k] }))

function PlanConfigModal({
  plan,
  open,
  canEdit,
  onClose,
}: {
  plan: PlanResponse | null
  open: boolean
  canEdit: boolean
  onClose: () => void
}) {
  const { message } = AntdApp.useApp()
  const [tiers, setTiers] = useState<PlanTier[]>([])
  const [rules, setRules] = useState<EligibilityRule[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!plan || !open) return
    setLoading(true)
    Promise.all([
      benefitPlanConfigApi.listTiers(plan.id),
      benefitPlanConfigApi.listRules(plan.id),
    ])
      .then(([t, r]) => { setTiers(t); setRules(r) })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load plan config'))
      .finally(() => setLoading(false))
  }, [plan, open, message])

  const addTier = () =>
    setTiers((cur) => [
      ...cur,
      { tierCode: 'EMPLOYEE_ONLY', employerContribution: 0, employeeContribution: 0, active: true },
    ])
  const patchTier = (i: number, patch: Partial<PlanTier>) =>
    setTiers((cur) => cur.map((t, idx) => (idx === i ? { ...t, ...patch } : t)))
  const removeTier = (i: number) => setTiers((cur) => cur.filter((_, idx) => idx !== i))

  const addRule = () => setRules((cur) => [...cur, { active: true }])
  const patchRule = (i: number, patch: Partial<EligibilityRule>) =>
    setRules((cur) => cur.map((r, idx) => (idx === i ? { ...r, ...patch } : r)))
  const removeRule = (i: number) => setRules((cur) => cur.filter((_, idx) => idx !== i))

  const save = async () => {
    if (!plan) return
    const codes = tiers.map((t) => t.tierCode)
    if (new Set(codes).size !== codes.length) {
      message.error('Each coverage tier code must be unique')
      return
    }
    setSaving(true)
    try {
      await benefitPlanConfigApi.replaceTiers(plan.id, tiers)
      await benefitPlanConfigApi.replaceRules(plan.id, rules)
      message.success('Plan configuration saved')
      onClose()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={plan ? `Coverage tiers & eligibility — ${plan.code}` : ''}
      width={840}
      onCancel={onClose}
      onOk={canEdit ? save : onClose}
      confirmLoading={saving}
      okText={canEdit ? 'Save' : 'Close'}
      cancelButtonProps={{ style: { display: canEdit ? undefined : 'none' } }}
    >
      {loading ? (
        <Spin />
      ) : (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>Coverage tiers</Text>
              {canEdit && <Button size="small" onClick={addTier}>Add tier</Button>}
            </Space>
            <Paragraph type="secondary" style={{ fontSize: 12, margin: '4px 0' }}>
              Per-tier employer/employee split. No tiers = the plan's flat contribution applies.
            </Paragraph>
            {tiers.length === 0 && <Empty description="No tiers — flat contribution used" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
            {tiers.map((t, i) => (
              <Row gutter={8} key={i} align="middle" style={{ marginBottom: 6 }}>
                <Col span={6}>
                  <Select
                    style={{ width: '100%' }}
                    value={t.tierCode}
                    disabled={!canEdit}
                    options={COVERAGE_TIER_OPTIONS}
                    onChange={(v) => patchTier(i, { tierCode: v })}
                  />
                </Col>
                <Col span={5}>
                  <InputNumber
                    style={{ width: '100%' }} min={0} precision={2} disabled={!canEdit}
                    addonBefore="Er" value={t.employerContribution}
                    onChange={(v) => patchTier(i, { employerContribution: v ?? 0 })}
                  />
                </Col>
                <Col span={5}>
                  <InputNumber
                    style={{ width: '100%' }} min={0} precision={2} disabled={!canEdit}
                    addonBefore="Ee" value={t.employeeContribution}
                    onChange={(v) => patchTier(i, { employeeContribution: v ?? 0 })}
                  />
                </Col>
                <Col span={6}>
                  <InputNumber
                    style={{ width: '100%' }} min={0} precision={2} disabled={!canEdit}
                    addonBefore="Cover" placeholder="sum insured"
                    value={t.coverageAmount ?? undefined}
                    onChange={(v) => patchTier(i, { coverageAmount: v })}
                  />
                </Col>
                <Col span={2}>
                  {canEdit && <Button size="small" danger onClick={() => removeTier(i)}>✕</Button>}
                </Col>
              </Row>
            ))}
          </div>

          <div>
            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>Eligibility rules</Text>
              {canEdit && <Button size="small" onClick={addRule}>Add rule</Button>}
            </Space>
            <Paragraph type="secondary" style={{ fontSize: 12, margin: '4px 0' }}>
              Each rule is a set of AND-ed conditions; an employee is eligible if ANY rule matches.
              No rules = open to everyone. Blank field = "any".
            </Paragraph>
            {rules.length === 0 && <Empty description="No rules — open to all" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
            {rules.map((r, i) => (
              <Row gutter={8} key={i} align="middle" style={{ marginBottom: 6 }}>
                <Col span={6}>
                  <Input
                    placeholder="Employment type" disabled={!canEdit}
                    value={r.employmentType ?? undefined}
                    onChange={(e) => patchRule(i, { employmentType: e.target.value })}
                  />
                </Col>
                <Col span={6}>
                  <Input
                    placeholder="Employee category" disabled={!canEdit}
                    value={r.employeeCategory ?? undefined}
                    onChange={(e) => patchRule(i, { employeeCategory: e.target.value })}
                  />
                </Col>
                <Col span={5}>
                  <InputNumber
                    style={{ width: '100%' }} min={0} disabled={!canEdit}
                    addonAfter="mo" placeholder="min service"
                    value={r.minServiceMonths ?? undefined}
                    onChange={(v) => patchRule(i, { minServiceMonths: v })}
                  />
                </Col>
                <Col span={5}>
                  <Input
                    placeholder="Note" disabled={!canEdit}
                    value={r.description ?? undefined}
                    onChange={(e) => patchRule(i, { description: e.target.value })}
                  />
                </Col>
                <Col span={2}>
                  {canEdit && <Button size="small" danger onClick={() => removeRule(i)}>✕</Button>}
                </Col>
              </Row>
            ))}
          </div>
        </Space>
      )}
    </Modal>
  )
}

// ─── Plans tab ───────────────────────────────────────────────────────────────

function PlansTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [plans, setPlans] = useState<PlanResponse[]>([])
  const [categories, setCategories] = useState<BenefitCategoryResponse[]>([])
  const [configPlan, setConfigPlan] = useState<PlanResponse | null>(null)
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
    categoryId?: string | null
    planYear?: number | null
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
    benefitCategoriesApi.list(true).then(setCategories).catch(() => setCategories([]))
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
      categoryId: plan.categoryId ?? undefined,
      planYear: plan.planYear ?? undefined,
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
      categoryId: v.categoryId ?? null,
      planYear: v.planYear ?? null,
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
    {
      title: 'Category',
      width: 140,
      render: (_, r) => (r.categoryName ? <Tag color="geekblue">{r.categoryName}</Tag> : '—'),
    },
    { title: 'Year', dataIndex: 'planYear', width: 70, align: 'center', render: (v) => v ?? '—' },
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
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => setConfigPlan(r)}>Tiers…</Button>
      ),
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
            <Col span={16}>
              <Form.Item name="categoryId" label="Category"
                tooltip="Tenant-configured category — drives tax treatment & provider requirement.">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="Pick a category…"
                  options={categories.map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="planYear" label="Plan year">
                <InputNumber min={2000} max={2100} style={{ width: '100%' }} placeholder="2026" />
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

      <PlanConfigModal
        plan={configPlan}
        open={!!configPlan}
        canEdit={canEdit}
        onClose={() => setConfigPlan(null)}
      />
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
    coverageTierCode?: CoverageTier
    dependentIds?: string[]
    dependentsCovered?: number
    notes?: string
  }>()
  const [saving, setSaving] = useState(false)
  const [enrolTiers, setEnrolTiers] = useState<PlanTier[]>([])
  const [enrolDeps, setEnrolDeps] = useState<Dependent[]>([])
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
    setEnrolTiers([])
    setEnrolDeps([])
    setOpen(true)
  }

  const onPlanChange = (planId?: string) => {
    enrolForm.setFieldsValue({ coverageTierCode: undefined })
    if (!planId) { setEnrolTiers([]); return }
    benefitPlanConfigApi.listTiers(planId)
      .then((t) => setEnrolTiers(t.filter((x) => x.active !== false)))
      .catch(() => setEnrolTiers([]))
  }

  const loadDeps = (employeeId?: string) => {
    enrolForm.setFieldsValue({ dependentIds: [] })
    const id = (employeeId ?? '').trim()
    if (!/^[0-9a-fA-F-]{36}$/.test(id)) { setEnrolDeps([]); return }
    profileTabsApi.listDependents(id, true)
      .then((d) => setEnrolDeps(d.filter((x) => x.benefitEligible || x.insuranceEligible)))
      .catch(() => setEnrolDeps([]))
  }

  const submitEnrol = async () => {
    const v = await enrolForm.validateFields()
    const req: EnrollmentRequest = {
      planId: v.planId,
      employeeId: v.employeeId,
      startDate: v.startDate.format('YYYY-MM-DD'),
      status: v.status,
      coverageTierCode: v.coverageTierCode ?? null,
      dependentIds: v.dependentIds ?? [],
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

  const doSuspendResume = async (r: EnrollmentResponse, action: 'suspend' | 'resume') => {
    try {
      await (action === 'suspend' ? benefitsApi.suspend(r.id) : benefitsApi.resume(r.id))
      message.success(action === 'suspend' ? 'Enrolment suspended' : 'Enrolment resumed')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    }
  }

  const doSubmitCancel = async (r: EnrollmentResponse, action: 'submit' | 'cancel') => {
    try {
      await (action === 'submit' ? benefitsApi.submit(r.id) : benefitsApi.cancel(r.id))
      message.success(action === 'submit' ? 'Submitted for approval' : 'Enrolment cancelled')
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
      title: 'Tier',
      width: 150,
      render: (_, r) => r.coverageTierCode
        ? <Tag>{COVERAGE_TIER_LABEL[r.coverageTierCode]}</Tag> : <Text type="secondary">flat</Text>,
    },
    {
      title: 'Deps',
      width: 70,
      align: 'center',
      render: (_, r) => {
        const names = (r.coveredDependents ?? []).map((d) => d.name).filter(Boolean).join(', ')
        return names
          ? <Tooltip title={names}><span>{r.dependentsCovered}</span></Tooltip>
          : r.dependentsCovered
      },
    },
    {
      title: 'Employer / mo',
      align: 'right',
      width: 130,
      render: (_, r) => fmt(r.employerContribution),
    },
    {
      title: 'Employee / mo',
      align: 'right',
      width: 130,
      render: (_, r) => fmt(r.employeeContribution),
    },
    {
      title: '',
      width: 200,
      render: (_, r) => {
        if (!canEnrol) return null
        const ended = r.status === 'TERMINATED' || r.status === 'CANCELLED' || r.status === 'REJECTED'
        return (
          <Space size={4}>
            {r.status === 'DRAFT' && (
              <Button size="small" type="primary" onClick={() => doSubmitCancel(r, 'submit')}>Submit</Button>
            )}
            {(r.status === 'DRAFT' || r.status === 'PENDING_APPROVAL') && (
              <Popconfirm title="Cancel this enrolment?" onConfirm={() => doSubmitCancel(r, 'cancel')}>
                <Button size="small">Cancel</Button>
              </Popconfirm>
            )}
            {r.status === 'ENROLLED' && (
              <Popconfirm title="Suspend this enrolment?" onConfirm={() => doSuspendResume(r, 'suspend')}>
                <Button size="small">Suspend</Button>
              </Popconfirm>
            )}
            {r.status === 'SUSPENDED' && (
              <Button size="small" onClick={() => doSuspendResume(r, 'resume')}>Resume</Button>
            )}
            {!ended && (
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
            )}
          </Space>
        )
      },
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
              { value: 'PENDING_APPROVAL', label: 'Pending approval' },
              { value: 'SUSPENDED', label: 'Suspended' },
              { value: 'WAIVED', label: 'Waived' },
              { value: 'TERMINATED', label: 'Terminated' },
              { value: 'CANCELLED', label: 'Cancelled' },
              { value: 'REJECTED', label: 'Rejected' },
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
              onChange={(v) => onPlanChange(v)}
              options={plans.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.name} (${BENEFIT_TYPE_LABEL[p.benefitType]})`,
              }))}
              placeholder="Pick a plan"
            />
          </Form.Item>
          <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}
            extra="Paste the employee UUID from their profile page; eligible dependants load below.">
            <Input placeholder="00000000-0000-0000-0000-000000000000"
              onChange={(e) => loadDeps(e.target.value)} />
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
                  { value: 'ENROLLED', label: 'Enrolled (active now)' },
                  { value: 'DRAFT', label: 'Draft (submit for approval)' },
                  { value: 'WAIVED', label: 'Waived (offered, declined)' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="coverageTierCode" label="Coverage tier"
                tooltip="Sets the employer/employee split. Leave blank to use the plan's flat rate.">
                <Select
                  allowClear
                  disabled={enrolTiers.length === 0}
                  placeholder={enrolTiers.length ? 'Pick a tier' : 'No tiers on this plan'}
                  options={enrolTiers.map((t) => ({
                    value: t.tierCode,
                    label: `${COVERAGE_TIER_LABEL[t.tierCode]} — Er ${fmt(t.employerContribution)} / Ee ${fmt(t.employeeContribution)}`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="dependentIds" label="Covered dependants"
            extra={enrolDeps.length ? undefined : 'Enter the employee ID above to load their benefit-eligible dependants.'}>
            <Select
              mode="multiple"
              allowClear
              disabled={enrolDeps.length === 0}
              placeholder={enrolDeps.length ? 'Select dependants to cover' : 'No eligible dependants'}
              options={enrolDeps.map((d) => ({
                value: d.id,
                label: `${d.firstName} ${d.lastName} (${d.relationshipType})`,
              }))}
            />
          </Form.Item>
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

// ─── Open enrollment tab (HCM_11 M379) ───────────────────────────────────────

const LIFE_EVENT_OPTIONS: { value: LifeEventType; label: string }[] = (
  Object.keys(LIFE_EVENT_LABEL) as LifeEventType[]
).map((k) => ({ value: k, label: LIFE_EVENT_LABEL[k] }))

function OpenEnrollmentTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<OpenEnrollmentWindowResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<OpenEnrollmentWindowResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{
    planYear: number
    name: string
    window: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
    notes?: string
    active: boolean
  }>()

  const load = () => {
    setLoading(true)
    openEnrollmentApi.listWindows(false)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load windows'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ planYear: dayjs().year(), active: true })
    setOpen(true)
  }
  const startEdit = (w: OpenEnrollmentWindowResponse) => {
    setEditing(w)
    form.setFieldsValue({
      planYear: w.planYear,
      name: w.name,
      window: [dayjs(w.startDate), dayjs(w.endDate)],
      notes: w.notes ?? undefined,
      active: w.active,
    })
    setOpen(true)
  }
  const submit = async () => {
    const v = await form.validateFields()
    const req: OpenEnrollmentWindowRequest = {
      planYear: v.planYear,
      name: v.name,
      startDate: v.window[0].format('YYYY-MM-DD'),
      endDate: v.window[1].format('YYYY-MM-DD'),
      notes: v.notes,
      active: v.active,
    }
    setSaving(true)
    try {
      if (editing) { await openEnrollmentApi.updateWindow(editing.id, req); message.success('Window updated') }
      else { await openEnrollmentApi.createWindow(req); message.success('Window created') }
      setOpen(false); load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed')
    } finally { setSaving(false) }
  }

  const cols: ColumnsType<OpenEnrollmentWindowResponse> = [
    { title: 'Plan year', dataIndex: 'planYear', width: 100, align: 'center' },
    { title: 'Name', dataIndex: 'name', render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a> },
    { title: 'Window', render: (_, r) => `${r.startDate} → ${r.endDate}` },
    {
      title: 'Open now',
      width: 110,
      align: 'center',
      render: (_, r) => (r.openNow ? <Tag color="green">OPEN</Tag> : <Tag>closed</Tag>),
    },
    {
      title: 'Status',
      width: 90,
      align: 'center',
      render: (_, r) => (r.active ? <Tag color="blue">Active</Tag> : <Tag>Inactive</Tag>),
    },
  ]

  if (loading) return <Spin />
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Open-enrollment windows define when employees may elect / change benefits for a plan year.
        HR can always enrol; this governs employee self-election.
      </Paragraph>
      {canEdit && <div><Button type="primary" onClick={startCreate}>New window…</Button></div>}
      <Card>
        <Table rowKey="id" columns={cols} dataSource={rows} size="small" pagination={false}
          locale={{ emptyText: <Empty description="No open-enrollment windows" /> }} />
      </Card>
      <Modal open={open} title={editing ? `Edit window — ${editing.name}` : 'New open-enrollment window'}
        onCancel={() => setOpen(false)} onOk={submit} confirmLoading={saving} okText={editing ? 'Save' : 'Create'}>
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="planYear" label="Plan year" rules={[{ required: true }]}>
                <InputNumber min={2000} max={2100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="2026 Annual Open Enrollment" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="window" label="Window (start / end)" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={18}><Form.Item name="notes" label="Notes"><Input.TextArea rows={2} /></Form.Item></Col>
            <Col span={6}><Form.Item name="active" label="Active" valuePropName="checked"><Switch /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Life events tab (HCM_11 M380) ───────────────────────────────────────────

function LifeEventsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_WRITE)

  const [rows, setRows] = useState<LifeEventResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined)
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{
    employeeId: string
    eventType: LifeEventType
    eventDate: ReturnType<typeof dayjs>
    windowDays?: number
    notes?: string
  }>()

  const load = () => {
    setLoading(true)
    lifeEventsApi.list({ status: filterStatus })
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load life events'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [filterStatus])

  const startReport = () => {
    form.resetFields()
    form.setFieldsValue({ eventType: 'MARRIAGE', eventDate: dayjs(), windowDays: 30 })
    setOpen(true)
  }
  const submit = async () => {
    const v = await form.validateFields()
    const req: LifeEventRequest = {
      employeeId: v.employeeId,
      eventType: v.eventType,
      eventDate: v.eventDate.format('YYYY-MM-DD'),
      windowDays: v.windowDays ?? 30,
      notes: v.notes,
    }
    setSaving(true)
    try { await lifeEventsApi.report(req); message.success('Life event reported'); setOpen(false); load() }
    catch (e) { message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed') }
    finally { setSaving(false) }
  }
  const review = async (r: LifeEventResponse, approve: boolean) => {
    try {
      await (approve ? lifeEventsApi.approve(r.id) : lifeEventsApi.reject(r.id))
      message.success(approve ? 'Approved — special window open' : 'Rejected')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    }
  }

  const cols: ColumnsType<LifeEventResponse> = [
    { title: 'Employee', render: (_, r) => r.employeeName ?? r.employeeId.slice(0, 8) },
    { title: 'Event', width: 140, render: (_, r) => <Tag>{LIFE_EVENT_LABEL[r.eventType]}</Tag> },
    { title: 'Event date', dataIndex: 'eventDate', width: 120 },
    { title: 'Window ends', dataIndex: 'windowEnd', width: 120 },
    {
      title: 'Special window',
      width: 130,
      align: 'center',
      render: (_, r) => (r.windowOpenNow ? <Tag color="green">OPEN</Tag> : <Tag>closed</Tag>),
    },
    {
      title: 'Status',
      width: 110,
      render: (_, r) => <Tag color={LIFE_EVENT_STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    {
      title: '',
      width: 160,
      render: (_, r) => canEdit && r.status === 'PENDING' ? (
        <Space size={4}>
          <Button size="small" type="primary" onClick={() => review(r, true)}>Approve</Button>
          <Popconfirm title="Reject this event?" onConfirm={() => review(r, false)}>
            <Button size="small" danger>Reject</Button>
          </Popconfirm>
        </Space>
      ) : null,
    },
  ]

  if (loading) return <Spin />
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        A qualifying life event (marriage, birth, …) opens a special enrolment window so the employee
        can change benefits outside open enrolment. HR approves the reported event.
      </Paragraph>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Select style={{ width: 180 }} allowClear placeholder="All statuses" value={filterStatus}
          onChange={setFilterStatus}
          options={[
            { value: 'PENDING', label: 'Pending' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
          ]} />
        {canEdit && <Button type="primary" onClick={startReport}>Report life event…</Button>}
      </Space>
      <Card>
        <Table rowKey="id" columns={cols} dataSource={rows} size="small" pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No life events" /> }} />
      </Card>
      <Modal open={open} title="Report a qualifying life event" onCancel={() => setOpen(false)}
        onOk={submit} confirmLoading={saving} okText="Report">
        <Form form={form} layout="vertical">
          <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}
            extra="Paste the employee UUID from their profile page.">
            <Input placeholder="00000000-0000-0000-0000-000000000000" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="eventType" label="Event type" rules={[{ required: true }]}>
                <Select options={LIFE_EVENT_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="eventDate" label="Event date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="windowDays" label="Window (days)">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Claims tab (HCM_11 M381/M382) ───────────────────────────────────────────

function ClaimsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_WRITE)

  const [rows, setRows] = useState<ClaimResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined)
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [items, setItems] = useState<ClaimItemRequest[]>([{ description: '', amount: 0 }])
  const [form] = Form.useForm<{
    employeeId: string
    claimDate: ReturnType<typeof dayjs>
    description?: string
  }>()

  const load = () => {
    setLoading(true)
    claimsApi.list({ status: filterStatus })
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load claims'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [filterStatus])

  const startCreate = () => {
    form.resetFields()
    form.setFieldsValue({ claimDate: dayjs() })
    setItems([{ description: '', amount: 0 }])
    setOpen(true)
  }
  const total = useMemo(() => items.reduce((s, i) => s + (Number(i.amount) || 0), 0), [items])
  const submit = async () => {
    const v = await form.validateFields()
    const valid = items.filter((i) => i.description.trim())
    if (!valid.length) { message.error('Add at least one line item'); return }
    const req: ClaimRequest = {
      employeeId: v.employeeId,
      claimDate: v.claimDate.format('YYYY-MM-DD'),
      description: v.description,
      items: valid,
    }
    setSaving(true)
    try { await claimsApi.create(req); message.success('Claim created (draft)'); setOpen(false); load() }
    catch (e) { message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed') }
    finally { setSaving(false) }
  }
  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); message.success(ok); load() }
    catch (e) { message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed') }
  }

  const cols: ColumnsType<ClaimResponse> = [
    { title: 'Claim #', dataIndex: 'claimNo', width: 110 },
    { title: 'Employee', render: (_, r) => r.employeeName ?? r.employeeId.slice(0, 8) },
    { title: 'Plan', render: (_, r) => r.planName ?? '—' },
    { title: 'Date', dataIndex: 'claimDate', width: 110 },
    { title: 'Total', align: 'right', width: 110, render: (_, r) => `${fmt(r.totalAmount)} ${r.currency}` },
    { title: 'Approved', align: 'right', width: 110, render: (_, r) => r.approvedAmount != null ? fmt(r.approvedAmount) : '—' },
    { title: 'Status', width: 110, render: (_, r) => <Tag color={CLAIM_STATUS_COLOR[r.status]}>{r.status}</Tag> },
    {
      title: '',
      width: 230,
      render: (_, r) => {
        if (!canEdit) return null
        return (
          <Space size={4} wrap>
            {r.status === 'DRAFT' && <Button size="small" type="primary" onClick={() => act(() => claimsApi.submit(r.id), 'Submitted')}>Submit</Button>}
            {r.status === 'SUBMITTED' && <Button size="small" type="primary" onClick={() => act(() => claimsApi.approve(r.id), 'Approved')}>Approve</Button>}
            {r.status === 'SUBMITTED' && (
              <Popconfirm title="Reject claim?" onConfirm={() => act(() => claimsApi.reject(r.id), 'Rejected')}>
                <Button size="small" danger>Reject</Button>
              </Popconfirm>
            )}
            {r.status === 'APPROVED' && (
              <Popconfirm title="Mark this claim paid?" description="Records the payment (tracking only)."
                onConfirm={() => act(() => claimsApi.pay(r.id, `PAY-${r.claimNo}`), 'Marked paid')}>
                <Button size="small">Mark paid</Button>
              </Popconfirm>
            )}
            {(r.status === 'DRAFT' || r.status === 'SUBMITTED') && (
              <Popconfirm title="Cancel claim?" onConfirm={() => act(() => claimsApi.cancel(r.id), 'Cancelled')}>
                <Button size="small">Cancel</Button>
              </Popconfirm>
            )}
          </Space>
        )
      },
    },
  ]

  if (loading) return <Spin />
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Benefit reimbursement claims: DRAFT → SUBMITTED → APPROVED / REJECTED → PAID. Payment is
        recorded for tracking (not auto-pushed to payroll).
      </Paragraph>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Select style={{ width: 180 }} allowClear placeholder="All statuses" value={filterStatus}
          onChange={setFilterStatus}
          options={['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED'].map((s) => ({ value: s, label: s }))} />
        {canEdit && <Button type="primary" onClick={startCreate}>New claim…</Button>}
      </Space>
      <Card>
        <Table
          rowKey="id" columns={cols} dataSource={rows} size="small" pagination={{ pageSize: 25 }}
          expandable={{
            expandedRowRender: (r) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {r.items.map((it) => (
                  <Text key={it.id}>
                    • {it.serviceDate ?? '—'} — {it.description}: {fmt(it.amount)} {r.currency}
                  </Text>
                ))}
                {r.reviewNotes && <Text type="secondary">Review: {r.reviewNotes}</Text>}
                {r.paymentReference && <Text type="secondary">Payment ref: {r.paymentReference}</Text>}
              </Space>
            ),
          }}
          locale={{ emptyText: <Empty description="No claims" /> }}
        />
      </Card>

      <Modal open={open} title="New benefit claim" width={680} onCancel={() => setOpen(false)}
        onOk={submit} confirmLoading={saving} okText="Create draft">
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}
                extra="Paste the employee UUID from their profile page.">
                <Input placeholder="00000000-0000-0000-0000-000000000000" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="claimDate" label="Claim date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description"><Input placeholder="e.g. Q3 outpatient reimbursement" /></Form.Item>
          <Text strong>Line items</Text>
          {items.map((it, i) => (
            <Row gutter={8} key={i} align="middle" style={{ marginTop: 6 }}>
              <Col span={7}>
                <DatePicker style={{ width: '100%' }} placeholder="Service date"
                  value={it.serviceDate ? dayjs(it.serviceDate) : undefined}
                  onChange={(d) => setItems((c) => c.map((x, idx) => idx === i ? { ...x, serviceDate: d ? d.format('YYYY-MM-DD') : undefined } : x))} />
              </Col>
              <Col span={10}>
                <Input placeholder="Description" value={it.description}
                  onChange={(e) => setItems((c) => c.map((x, idx) => idx === i ? { ...x, description: e.target.value } : x))} />
              </Col>
              <Col span={5}>
                <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="Amount" value={it.amount}
                  onChange={(v) => setItems((c) => c.map((x, idx) => idx === i ? { ...x, amount: v ?? 0 } : x))} />
              </Col>
              <Col span={2}>
                {items.length > 1 && <Button size="small" danger onClick={() => setItems((c) => c.filter((_, idx) => idx !== i))}>✕</Button>}
              </Col>
            </Row>
          ))}
          <div style={{ marginTop: 8 }}>
            <Button size="small" onClick={() => setItems((c) => [...c, { description: '', amount: 0 }])}>Add item</Button>
            <Text strong style={{ float: 'right' }}>Total: {fmt(total)}</Text>
          </div>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Dashboard tab (HCM_11 M386) ─────────────────────────────────────────────

function DashboardTab() {
  const { message } = AntdApp.useApp()
  const [d, setD] = useState<BenefitDashboard | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    benefitReconcileApi.dashboard()
      .then(setD)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load dashboard'))
      .finally(() => setLoading(false))
  }, [message])

  if (loading) return <Spin />
  if (!d) return <Empty description="No dashboard data" />

  const catCols: ColumnsType<BenefitDashboard['byCategory'][number]> = [
    { title: 'Category', dataIndex: 'category' },
    { title: 'Enrolments', dataIndex: 'enrollments', align: 'center', width: 110 },
    { title: 'Employer / mo', align: 'right', width: 130, render: (_, r) => fmt(r.employerMonthly) },
    { title: 'Employee / mo', align: 'right', width: 130, render: (_, r) => fmt(r.employeeMonthly) },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={16}>
        <Col span={6}><Card size="small"><Statistic title="Active enrolments" value={d.activeEnrollments} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Active plans" value={d.activePlans} suffix={`/ ${d.totalPlans}`} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Pending approvals" value={d.pendingApprovals} valueStyle={{ color: d.pendingApprovals ? '#d48806' : undefined }} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Expiring plans (60d)" value={d.expiringPlans} valueStyle={{ color: d.expiringPlans ? '#cf1322' : undefined }} /></Card></Col>
      </Row>
      <Row gutter={16}>
        <Col span={6}><Card size="small"><Statistic title="Employer / month" value={d.employerMonthly} precision={2} suffix="AZN" /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Employer / year" value={d.employerAnnual} precision={2} suffix="AZN" /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Employee / month" value={d.employeeMonthly} precision={2} suffix="AZN" /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="Open life-event windows" value={d.openLifeEventWindows} /></Card></Col>
      </Row>

      <Card size="small" title="Employer spend by category">
        <Table rowKey="category" columns={catCols} dataSource={d.byCategory} size="small" pagination={false}
          locale={{ emptyText: <Empty description="No active enrolments" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }} />
      </Card>

      <Row gutter={16}>
        <Col span={12}>
          <Card size="small" title="Enrolments by status">
            <Space wrap>
              {Object.entries(d.enrollmentsByStatus).map(([k, v]) => (
                <Tag key={k} color={ENROLLMENT_STATUS_COLOR[k as EnrollmentStatus] ?? 'default'}>{k}: {v}</Tag>
              ))}
              {!Object.keys(d.enrollmentsByStatus).length && <Text type="secondary">—</Text>}
            </Space>
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="Claims">
            <Space direction="vertical">
              <Space wrap>
                {Object.entries(d.claimsByStatus).map(([k, v]) => (
                  <Tag key={k} color={CLAIM_STATUS_COLOR[k as keyof typeof CLAIM_STATUS_COLOR] ?? 'default'}>{k}: {v}</Tag>
                ))}
                {!Object.keys(d.claimsByStatus).length && <Text type="secondary">No claims</Text>}
              </Space>
              <Text>Total paid: <Text strong>{fmt(d.claimsPaidTotal)} AZN</Text></Text>
            </Space>
          </Card>
        </Col>
      </Row>
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
        { key: 'dashboard', label: 'Dashboard', children: <DashboardTab /> },
        { key: 'categories', label: 'Categories', children: <CategoriesTab /> },
        { key: 'providers', label: 'Providers', children: <ProvidersTab /> },
        { key: 'plans', label: 'Plans catalog', children: <PlansTab /> },
        { key: 'enrolments', label: 'Enrolments', children: <EnrolmentsTab /> },
        { key: 'openEnrollment', label: 'Open enrollment', children: <OpenEnrollmentTab /> },
        { key: 'lifeEvents', label: 'Life events', children: <LifeEventsTab /> },
        { key: 'claims', label: 'Claims', children: <ClaimsTab /> },
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
      <Tabs items={items} defaultActiveKey={isHr ? 'dashboard' : 'me'} />
    </Space>
  )
}
