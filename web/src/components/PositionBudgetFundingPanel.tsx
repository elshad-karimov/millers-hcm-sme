// M244 — Position budget + funding panel.
//
// Single reusable component used by PositionFormPage. Renders:
//
//   1. Funding row — pill + "Edit funding" inline form (status / source /
//      owner / expiry / notes). Saves to PUT /funding.
//   2. Current budget summary (period, total monthly, total annual).
//   3. Versioned budget table (one row per effective window) + "Add
//      budget version" inline form.
//
// All amounts are entered as monthly figures; the panel computes total
// monthly / annual on the fly.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  FUNDING_STATUS_COLOR,
  FUNDING_STATUS_LABEL,
  positionBudgetApi,
  positionFundingApi,
  type BudgetRequest,
  type FundingStatus,
  type PositionBudget,
  type PositionFunding,
} from '../api/positionBudget'

const STATUS_OPTIONS: { value: FundingStatus; label: string }[] = (
  ['FUNDED', 'PARTIALLY_FUNDED', 'PENDING', 'UNFUNDED', 'EXPIRED'] as FundingStatus[]
).map((v) => ({ value: v, label: FUNDING_STATUS_LABEL[v] }))

interface Props {
  positionId: string
  /** Position currency — defaulted into new budget rows. */
  defaultCurrency?: string
  /** When false, hides write actions (read-only audit view). */
  canEdit?: boolean
}

export function PositionBudgetFundingPanel({
  positionId,
  defaultCurrency = 'AZN',
  canEdit = true,
}: Props) {
  const { message } = AntdApp.useApp()
  const [funding, setFunding] = useState<PositionFunding | null>(null)
  const [budgets, setBudgets] = useState<PositionBudget[]>([])
  const [loading, setLoading] = useState(true)
  const [fundingModal, setFundingModal] = useState(false)
  const [budgetModal, setBudgetModal] = useState<PositionBudget | 'new' | null>(null)
  // Local form-values shape; same reason as BudgetFormValues below — the
  // shared FundingRequest types `fundingExpiry` as `string`, but in the
  // form it's a Dayjs (we only serialise on submit).
  type FundingFormValues = {
    status: FundingStatus
    fundingSource?: string
    fundingOwner?: string
    fundingExpiry?: dayjs.Dayjs
    notes?: string
  }
  const [fundingForm] = Form.useForm<FundingFormValues>()
  // Local form-values shape: we keep dates as Dayjs because Ant's DatePicker
  // works that way, then serialise to YYYY-MM-DD on submit.
  type BudgetFormValues = {
    effectiveFrom: dayjs.Dayjs
    effectiveTo?: dayjs.Dayjs
    budgetedBasicSalary?: number
    budgetedAllowances?: number
    budgetedEmployerTax?: number
    budgetedBonus?: number
    budgetedOvertime?: number
    budgetedBenefits?: number
    currency: string
    budgetOwner?: string
    notes?: string
  }
  const [budgetForm] = Form.useForm<BudgetFormValues>()

  // Load funding + budget list in parallel; each fails independently so a
  // 500 on one doesn't take down the other.
  const refresh = () => {
    setLoading(true)
    positionFundingApi.get(positionId)
      .then(setFunding)
      .catch((err) =>
        message.warning(
          err?.response?.data?.message ?? 'Could not load funding state.',
        ),
      )
    positionBudgetApi.list(positionId)
      .then(setBudgets)
      .catch((err) =>
        message.warning(err?.response?.data?.message ?? 'Could not load budgets.'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [positionId])

  // ── Funding handlers ──────────────────────────────────────────────

  const openFunding = () => {
    fundingForm.setFieldsValue({
      status: funding?.status ?? 'UNFUNDED',
      fundingSource: funding?.fundingSource ?? undefined,
      fundingOwner: funding?.fundingOwner ?? undefined,
      fundingExpiry: funding?.fundingExpiry ? dayjs(funding.fundingExpiry) : undefined,
      notes: funding?.notes ?? undefined,
    })
    setFundingModal(true)
  }

  const onFundingOk = async () => {
    const v = await fundingForm.validateFields()
    try {
      const saved = await positionFundingApi.upsert(positionId, {
        status: v.status,
        fundingSource: v.fundingSource,
        fundingOwner: v.fundingOwner,
        fundingExpiry: v.fundingExpiry ? v.fundingExpiry.format('YYYY-MM-DD') : undefined,
        notes: v.notes,
      })
      setFunding(saved)
      setFundingModal(false)
      message.success(`Funding set to ${FUNDING_STATUS_LABEL[saved.status]}`)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save funding.')
    }
  }

  // ── Budget handlers ───────────────────────────────────────────────

  const openBudget = (row: PositionBudget | 'new') => {
    if (row === 'new') {
      budgetForm.resetFields()
      budgetForm.setFieldsValue({
        effectiveFrom: dayjs().startOf('month'),
        currency: defaultCurrency,
      })
    } else {
      budgetForm.setFieldsValue({
        effectiveFrom: dayjs(row.effectiveFrom),
        effectiveTo: row.effectiveTo ? dayjs(row.effectiveTo) : undefined,
        budgetedBasicSalary: row.budgetedBasicSalary,
        budgetedAllowances: row.budgetedAllowances,
        budgetedEmployerTax: row.budgetedEmployerTax,
        budgetedBonus: row.budgetedBonus,
        budgetedOvertime: row.budgetedOvertime,
        budgetedBenefits: row.budgetedBenefits,
        currency: row.currency,
        budgetOwner: row.budgetOwner ?? undefined,
        notes: row.notes ?? undefined,
      })
    }
    setBudgetModal(row)
  }

  const onBudgetOk = async () => {
    const v = await budgetForm.validateFields()
    const body: BudgetRequest = {
      effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo ? v.effectiveTo.format('YYYY-MM-DD') : undefined,
      budgetedBasicSalary: v.budgetedBasicSalary,
      budgetedAllowances: v.budgetedAllowances,
      budgetedEmployerTax: v.budgetedEmployerTax,
      budgetedBonus: v.budgetedBonus,
      budgetedOvertime: v.budgetedOvertime,
      budgetedBenefits: v.budgetedBenefits,
      currency: v.currency,
      budgetOwner: v.budgetOwner,
      notes: v.notes,
    }
    try {
      if (budgetModal === 'new') {
        await positionBudgetApi.create(positionId, body)
        message.success('Budget version added')
      } else if (budgetModal) {
        await positionBudgetApi.update(positionId, budgetModal.id, body)
        message.success('Budget updated')
      }
      setBudgetModal(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save budget.')
    }
  }

  const onBudgetDelete = async (row: PositionBudget) => {
    try {
      await positionBudgetApi.remove(positionId, row.id)
      message.success('Budget version removed')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not delete.')
    }
  }

  // ── Render ────────────────────────────────────────────────────────

  // ISO YYYY-MM-DD strings sort lexicographically so we don't need a
  // dayjs plugin (isSameOrBefore / isSameOrAfter are non-default plugins).
  const todayIso = dayjs().format('YYYY-MM-DD')
  const currentBudget = budgets.find(
    (b) => b.effectiveFrom <= todayIso && (!b.effectiveTo || b.effectiveTo >= todayIso),
  )

  const fmt = (n: number | undefined | null, cur: string) =>
    n == null ? '—' : `${cur} ${Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

  const cols: ColumnsType<PositionBudget> = [
    {
      title: 'Effective window',
      width: 200,
      render: (_, r) => (
        <Space size={4}>
          <span>{r.effectiveFrom}</span>
          <span>→</span>
          <span>{r.effectiveTo ?? <Typography.Text type="secondary">open</Typography.Text>}</span>
        </Space>
      ),
    },
    {
      title: 'Total monthly',
      align: 'right' as const,
      render: (_, r) => <strong>{fmt(r.totalMonthly, r.currency)}</strong>,
    },
    {
      title: 'Total annual',
      align: 'right' as const,
      render: (_, r) => fmt(r.totalAnnual, r.currency),
    },
    {
      title: 'Owner',
      dataIndex: 'budgetOwner',
      render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: '',
      width: 140,
      render: (_, r) =>
        canEdit && (
          <Space size={4}>
            <Button size="small" onClick={() => openBudget(r)}>
              Edit
            </Button>
            <Popconfirm
              title="Delete this budget version?"
              onConfirm={() => onBudgetDelete(r)}
            >
              <Button size="small" danger>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* ── Funding pill + edit button ── */}
      <Card
        size="small"
        title={
          <Space>
            <span>Funding</span>
            <Tag color={FUNDING_STATUS_COLOR[funding?.status ?? 'UNFUNDED']}>
              {FUNDING_STATUS_LABEL[funding?.status ?? 'UNFUNDED']}
            </Tag>
          </Space>
        }
        extra={canEdit && <Button size="small" onClick={openFunding}>Edit funding</Button>}
      >
        <Descriptions size="small" column={2}>
          <Descriptions.Item label="Source">
            {funding?.fundingSource ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Owner">
            {funding?.fundingOwner ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Expiry">
            {funding?.fundingExpiry ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Notes">
            {funding?.notes ?? '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* ── Current budget summary ── */}
      {currentBudget && (
        <Card
          size="small"
          title="Current budget"
          extra={
            <Typography.Text type="secondary">
              {currentBudget.effectiveFrom} → {currentBudget.effectiveTo ?? 'open'}
            </Typography.Text>
          }
        >
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="Basic salary">{fmt(currentBudget.budgetedBasicSalary, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Allowances">{fmt(currentBudget.budgetedAllowances, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Employer tax/social">{fmt(currentBudget.budgetedEmployerTax, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Bonus">{fmt(currentBudget.budgetedBonus, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Overtime">{fmt(currentBudget.budgetedOvertime, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Benefits">{fmt(currentBudget.budgetedBenefits, currentBudget.currency)}</Descriptions.Item>
            <Descriptions.Item label="Monthly total"><strong>{fmt(currentBudget.totalMonthly, currentBudget.currency)}</strong></Descriptions.Item>
            <Descriptions.Item label="Annual total" span={2}><strong>{fmt(currentBudget.totalAnnual, currentBudget.currency)}</strong></Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* ── Versioned budget table ── */}
      <Card
        size="small"
        title="Budget versions"
        extra={
          canEdit && (
            <Button size="small" type="primary" onClick={() => openBudget('new')}>
              + Add budget version
            </Button>
          )
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={cols}
          dataSource={budgets}
          loading={loading}
          pagination={false}
          locale={{ emptyText: 'No budget set yet.' }}
        />
      </Card>

      {/* ── Funding modal ── */}
      <Modal
        title="Edit funding"
        open={fundingModal}
        onOk={onFundingOk}
        onCancel={() => setFundingModal(false)}
        okText="Save"
        destroyOnClose
      >
        <Form form={fundingForm} layout="vertical" preserve={false}>
          <Form.Item name="status" label="Status" rules={[{ required: true }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="fundingSource" label="Funding source">
            <Input placeholder="e.g. Department budget, Grant GR-2026-01, Project Alpha" maxLength={160} />
          </Form.Item>
          <Form.Item name="fundingOwner" label="Funding owner">
            <Input placeholder="e.g. CFO, Department head" maxLength={120} />
          </Form.Item>
          <Form.Item
            name="fundingExpiry"
            label="Expiry date (grant / project)"
            tooltip="If set in the past, funding auto-flips to EXPIRED."
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Budget modal ── */}
      <Modal
        title={budgetModal === 'new' ? 'Add budget version' : 'Edit budget version'}
        open={!!budgetModal}
        onOk={onBudgetOk}
        onCancel={() => setBudgetModal(null)}
        okText="Save"
        destroyOnClose
        width={620}
      >
        <Form form={budgetForm} layout="vertical" preserve={false}>
          <Space size="small">
            <Form.Item name="effectiveFrom" label="Effective from" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="effectiveTo" label="Effective to (optional)">
              <DatePicker />
            </Form.Item>
            <Form.Item name="currency" label="Currency" rules={[{ required: true }]}>
              <Select
                style={{ width: 90 }}
                options={['AZN', 'USD', 'EUR', 'TRY'].map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
          </Space>
          <Space wrap size="small">
            <Form.Item name="budgetedBasicSalary" label="Basic salary">
              <InputNumber min={0} step={50} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="budgetedAllowances" label="Allowances">
              <InputNumber min={0} step={10} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="budgetedEmployerTax" label="Employer tax/social">
              <InputNumber min={0} step={10} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="budgetedBonus" label="Bonus">
              <InputNumber min={0} step={10} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="budgetedOvertime" label="Overtime">
              <InputNumber min={0} step={10} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="budgetedBenefits" label="Benefits">
              <InputNumber min={0} step={10} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="budgetOwner" label="Budget owner">
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
