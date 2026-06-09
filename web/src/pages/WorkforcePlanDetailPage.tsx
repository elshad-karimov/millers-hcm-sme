// M247 — Workforce plan detail page.

import { useEffect, useMemo, useState } from 'react'
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
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  SCENARIO_TYPE_COLOR,
  SCENARIO_TYPE_LABEL,
  workforcePlanApi,
  WORKFORCE_PLAN_STATUS_COLOR,
  WORKFORCE_PLAN_STATUS_LABEL,
  type CloneRequest,
  type ScenarioType,
  type VarianceResult,
  type WorkforcePlan,
  type WorkforcePlanLine,
  type WorkforcePlanLineRequest,
} from '../api/workforcePlan'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CHANGE_TYPE_OPTIONS = ['ADD', 'EXPAND', 'REDUCE', 'REPLACE', 'HOLD'].map(
  (t) => ({ value: t, label: t }),
)

const SCENARIO_OPTIONS = (
  ['EXPANSION', 'REDUCTION', 'RESTRUCTURE', 'SEASONAL', 'WHAT_IF'] as ScenarioType[]
).map((t) => ({ value: t, label: SCENARIO_TYPE_LABEL[t] }))

export function WorkforcePlanDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { message, modal } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [plan, setPlan] = useState<WorkforcePlan | null>(null)
  const [lines, setLines] = useState<WorkforcePlanLine[]>([])
  const [variance, setVariance] = useState<VarianceResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [lineModal, setLineModal] = useState<WorkforcePlanLine | 'new' | null>(null)
  const [cloneModal, setCloneModal] = useState(false)

  // Same pattern as M244: locally re-shape the form values so DatePicker's
  // Dayjs field doesn't clash with the API record's string field.
  type LineFormValues = {
    lineNo?: number
    orgUnitLabel?: string
    jobFamily?: string
    grade?: string
    positionTitle?: string
    plannedHeadcount: number
    plannedFte?: number
    plannedMonthlyCost?: number
    currency?: string
    changeType?: string
    targetStartDate?: dayjs.Dayjs
    justification?: string
    notes?: string
  }
  type CloneFormValues = CloneRequest
  const [lineForm] = Form.useForm<LineFormValues>()
  const [cloneForm] = Form.useForm<CloneFormValues>()

  const editable = plan?.status === 'DRAFT'

  const refresh = () => {
    if (!id) return
    setLoading(true)
    workforcePlanApi.get(id).then(setPlan).catch((err) =>
      message.error(err?.response?.data?.message ?? 'Could not load'),
    )
    workforcePlanApi.lines(id).then(setLines).catch(() => setLines([]))
    workforcePlanApi.variance(id).then(setVariance).catch(() => setVariance(null))
      .finally(() => setLoading(false))
  }
  useEffect(refresh, [id])

  const lifecycleAction = async (
    action: 'submit' | 'approve' | 'archive',
    success: string,
  ) => {
    if (!id) return
    setSubmitting(true)
    try {
      const updated = await workforcePlanApi[action](id)
      setPlan(updated)
      message.success(success)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Action failed')
    } finally {
      setSubmitting(false)
    }
  }

  const onReject = () => {
    let reason = ''
    modal.confirm({
      title: 'Reject this plan?',
      content: (
        <Input.TextArea rows={3} placeholder="Reason — required" onChange={(e) => (reason = e.target.value)} />
      ),
      okText: 'Reject',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (!id || !reason.trim()) {
          message.error('Reason is required')
          throw new Error('reason')
        }
        try {
          const updated = await workforcePlanApi.reject(id, reason.trim())
          setPlan(updated)
          message.success('Rejected; sent back to draft state')
        } catch (err: unknown) {
          const e = err as { response?: { data?: { message?: string } } }
          message.error(e?.response?.data?.message ?? 'Failed')
          throw err
        }
      },
    })
  }

  // ── Lines ────────────────────────────────────────────────────────

  const openLine = (line: WorkforcePlanLine | 'new') => {
    if (line === 'new') {
      lineForm.resetFields()
      lineForm.setFieldsValue({
        plannedHeadcount: 1,
        plannedFte: 1,
        plannedMonthlyCost: 0,
        currency: 'AZN',
        changeType: 'ADD',
      })
    } else {
      lineForm.setFieldsValue({
        lineNo: line.lineNo,
        orgUnitLabel: line.orgUnitLabel ?? undefined,
        jobFamily: line.jobFamily ?? undefined,
        grade: line.grade ?? undefined,
        positionTitle: line.positionTitle ?? undefined,
        plannedHeadcount: line.plannedHeadcount,
        plannedFte: line.plannedFte,
        plannedMonthlyCost: line.plannedMonthlyCost,
        currency: line.currency,
        changeType: line.changeType ?? undefined,
        targetStartDate: line.targetStartDate ? dayjs(line.targetStartDate) : undefined,
        justification: line.justification ?? undefined,
        notes: line.notes ?? undefined,
      })
    }
    setLineModal(line)
  }

  const onLineOk = async () => {
    if (!id) return
    const v = await lineForm.validateFields()
    const body: WorkforcePlanLineRequest = {
      ...v,
      targetStartDate: v.targetStartDate ? v.targetStartDate.format('YYYY-MM-DD') : undefined,
    }
    try {
      if (lineModal === 'new') {
        await workforcePlanApi.addLine(id, body)
        message.success('Line added')
      } else if (lineModal) {
        await workforcePlanApi.updateLine(lineModal.id, body)
        message.success('Line updated')
      }
      setLineModal(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save')
    }
  }

  const onLineDelete = async (line: WorkforcePlanLine) => {
    try {
      await workforcePlanApi.removeLine(line.id)
      message.success('Line removed')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  // ── Clone ────────────────────────────────────────────────────────

  const openClone = () => {
    cloneForm.resetFields()
    cloneForm.setFieldsValue({
      newVersionCode: `${plan?.versionCode}-scenario`,
      newTitle: `${plan?.title ?? plan?.versionCode} (scenario)`,
      scenarioType: 'WHAT_IF',
    })
    setCloneModal(true)
  }
  const onClone = async () => {
    if (!id) return
    const v = await cloneForm.validateFields()
    try {
      const created = await workforcePlanApi.clone(id, v)
      setCloneModal(false)
      message.success('Scenario created')
      navigate(`/workforce-plans/${created.id}`)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Clone failed')
    }
  }

  // ── Totals + render ─────────────────────────────────────────────

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, l) => ({
        hc: acc.hc + l.plannedHeadcount,
        cost: acc.cost + Number(l.plannedMonthlyCost),
      }),
      { hc: 0, cost: 0 },
    )
  }, [lines])

  const fmt = (n: number, cur = 'AZN') =>
    `${cur} ${n.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`

  const lineCols: ColumnsType<WorkforcePlanLine> = [
    { title: '№', dataIndex: 'lineNo', width: 60 },
    {
      title: 'Org unit',
      dataIndex: 'orgUnitLabel',
      render: (v: string) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Position / job family',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          {r.positionTitle && <strong>{r.positionTitle}</strong>}
          {r.jobFamily && (
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {r.jobFamily}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    { title: 'Grade', dataIndex: 'grade', width: 80 },
    {
      title: 'Change',
      dataIndex: 'changeType',
      width: 100,
      render: (v: string) => (v ? <Tag>{v}</Tag> : ''),
    },
    {
      title: 'Planned HC',
      dataIndex: 'plannedHeadcount',
      align: 'right' as const,
      width: 100,
    },
    {
      title: 'Planned FTE',
      align: 'right' as const,
      width: 100,
      render: (_, r) => Number(r.plannedFte).toFixed(2),
    },
    {
      title: 'Monthly cost',
      align: 'right' as const,
      width: 140,
      render: (_, r) => <strong>{fmt(Number(r.plannedMonthlyCost), r.currency)}</strong>,
    },
    { title: 'Target', dataIndex: 'targetStartDate', width: 120 },
    ...(editable && canWrite
      ? [
          {
            title: '',
            width: 130,
            render: (_: unknown, r: WorkforcePlanLine) => (
              <Space size={4}>
                <Button size="small" onClick={() => openLine(r)}>
                  Edit
                </Button>
                <Popconfirm title="Delete this line?" onConfirm={() => onLineDelete(r)}>
                  <Button size="small" danger>
                    Delete
                  </Button>
                </Popconfirm>
              </Space>
            ),
          } as ColumnsType<WorkforcePlanLine>[number],
        ]
      : []),
  ]

  if (loading || !plan) return <Card loading />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* ── Header ── */}
      <Card
        title={
          <Space wrap>
            <Button size="small" onClick={() => navigate('/workforce-plans')}>
              ← Back
            </Button>
            <span>{plan.title ?? plan.versionCode}</span>
            <Tag color={SCENARIO_TYPE_COLOR[plan.scenarioType]}>
              {SCENARIO_TYPE_LABEL[plan.scenarioType]}
            </Tag>
            <Tag color={WORKFORCE_PLAN_STATUS_COLOR[plan.status]}>
              {WORKFORCE_PLAN_STATUS_LABEL[plan.status]}
            </Tag>
            {plan.parentPlanId && (
              <Tag color="default">
                forked from{' '}
                <a onClick={() => navigate(`/workforce-plans/${plan.parentPlanId}`)}>
                  parent
                </a>
              </Tag>
            )}
          </Space>
        }
        extra={
          <Space wrap>
            {canWrite && (
              <Button onClick={openClone}>📑 Clone as scenario</Button>
            )}
            {canWrite && editable && lines.length > 0 && (
              <Button
                type="primary"
                loading={submitting}
                onClick={() => lifecycleAction('submit', 'Submitted')}
              >
                Submit for approval
              </Button>
            )}
            {canWrite && plan.status === 'PENDING_APPROVAL' && (
              <>
                <Button
                  type="primary"
                  loading={submitting}
                  onClick={() => lifecycleAction('approve', 'Approved + activated')}
                >
                  Approve
                </Button>
                <Button danger loading={submitting} onClick={onReject}>
                  Reject
                </Button>
              </>
            )}
            {canWrite && (plan.status === 'ACTIVE' || plan.status === 'REJECTED') && (
              <Popconfirm
                title="Archive this plan?"
                onConfirm={() => lifecycleAction('archive', 'Archived')}
              >
                <Button danger loading={submitting}>
                  Archive
                </Button>
              </Popconfirm>
            )}
          </Space>
        }
      >
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="Version">{plan.versionCode}</Descriptions.Item>
          <Descriptions.Item label="Period">
            {plan.effectiveFrom} → {plan.effectiveTo ?? 'open'}
          </Descriptions.Item>
          <Descriptions.Item label="Scenario">{SCENARIO_TYPE_LABEL[plan.scenarioType]}</Descriptions.Item>
          <Descriptions.Item label="Lines">{lines.length}</Descriptions.Item>
          <Descriptions.Item label="Total planned HC">{totals.hc}</Descriptions.Item>
          <Descriptions.Item label="Total monthly cost">
            <strong>{fmt(totals.cost)}</strong>
          </Descriptions.Item>
          {plan.rejectedAt && (
            <Descriptions.Item label="Rejected" span={3}>
              {plan.rejectedBy} · {new Date(plan.rejectedAt).toLocaleString()} ·{' '}
              <em>{plan.rejectReason}</em>
            </Descriptions.Item>
          )}
          {plan.notes && (
            <Descriptions.Item label="Notes" span={3}>
              {plan.notes}
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      {/* ── Variance vs actual ── */}
      {variance && (
        <Card title="Variance vs current actual" size="small">
          <Space size="large" wrap>
            <Statistic title="Planned HC" value={variance.plannedHeadcount} />
            <Statistic title="Actual HC" value={variance.actualHeadcount} />
            <Statistic
              title="Gap (planned − actual)"
              value={variance.headcountGap}
              valueStyle={{
                color:
                  variance.headcountGap > 0
                    ? '#fa8c16'
                    : variance.headcountGap < 0
                    ? '#52c41a'
                    : undefined,
              }}
              suffix={
                variance.headcountGap > 0
                  ? '→ hire'
                  : variance.headcountGap < 0
                  ? '→ over'
                  : '→ on target'
              }
            />
            <Statistic title="Planned monthly cost" value={variance.plannedMonthlyCost} precision={2} />
            <Statistic title="Actual monthly cost" value={variance.actualMonthlyCost} precision={2} />
            <Statistic
              title="Cost gap"
              value={variance.monthlyCostGap}
              precision={2}
              valueStyle={{ color: variance.monthlyCostGap > 0 ? '#fa8c16' : '#52c41a' }}
            />
          </Space>
        </Card>
      )}

      {/* ── Lines ── */}
      <Card
        title="Plan lines"
        extra={
          editable && canWrite ? (
            <Button type="primary" onClick={() => openLine('new')}>
              + Add line
            </Button>
          ) : null
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={lineCols}
          dataSource={lines}
          pagination={false}
          locale={{ emptyText: 'No lines yet.' }}
        />
      </Card>

      {/* ── Line modal ── */}
      <Modal
        title={lineModal === 'new' ? 'Add plan line' : 'Edit plan line'}
        open={!!lineModal}
        onOk={onLineOk}
        onCancel={() => setLineModal(null)}
        okText="Save"
        destroyOnClose
        width={600}
      >
        <Form form={lineForm} layout="vertical" preserve={false}>
          <Form.Item name="orgUnitLabel" label="Org unit (label)">
            <Input maxLength={200} placeholder="e.g. Finance" />
          </Form.Item>
          <Space size="small">
            <Form.Item name="positionTitle" label="Position title">
              <Input maxLength={200} placeholder="e.g. Senior Accountant" />
            </Form.Item>
            <Form.Item name="jobFamily" label="Job family">
              <Input maxLength={64} placeholder="e.g. Accounting" />
            </Form.Item>
            <Form.Item name="grade" label="Grade">
              <Input maxLength={32} placeholder="G5" />
            </Form.Item>
          </Space>
          <Form.Item name="changeType" label="Change type" rules={[{ required: true }]}>
            <Select options={CHANGE_TYPE_OPTIONS} />
          </Form.Item>
          <Space size="small">
            <Form.Item name="plannedHeadcount" label="Planned HC" rules={[{ required: true }]}>
              <InputNumber min={0} step={1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="plannedFte" label="Planned FTE">
              <InputNumber min={0} step={0.5} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="plannedMonthlyCost" label="Monthly cost">
              <InputNumber min={0} step={100} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="currency" label="Currency">
              <Input style={{ width: 80 }} maxLength={3} />
            </Form.Item>
          </Space>
          <Form.Item name="targetStartDate" label="Target start date">
            <DatePicker />
          </Form.Item>
          <Form.Item name="justification" label="Justification">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Clone modal ── */}
      <Modal
        title="Clone as scenario"
        open={cloneModal}
        onOk={onClone}
        onCancel={() => setCloneModal(false)}
        okText="Clone"
        destroyOnClose
      >
        <Form form={cloneForm} layout="vertical" preserve={false}>
          <Form.Item name="newVersionCode" label="New version code" rules={[{ required: true }]}>
            <Input placeholder="e.g. 2026-stores+5" />
          </Form.Item>
          <Form.Item name="newTitle" label="New title">
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="scenarioType" label="Scenario type" rules={[{ required: true }]}>
            <Select options={SCENARIO_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
