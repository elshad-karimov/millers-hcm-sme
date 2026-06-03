import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
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
  type BonusMatrixRule,
  type BonusMatrixRuleRequest,
} from '../api/compbenefits'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

interface FormValues {
  code: string
  description?: string
  matchRecommendation?: string
  ratingRange?: [number | null, number | null]
  bonusPercent?: number
  flatAmount?: number
  currency: string
  maxAmount?: number
  priority: number
  effective: [dayjs.Dayjs, dayjs.Dayjs | null]
  active: boolean
}

export function BonusMatrixPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<BonusMatrixRule[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<BonusMatrixRule | null>(null)
  const [form] = Form.useForm<FormValues>()

  const load = () => {
    setLoading(true)
    compBenefitsApi
      .matrixRules()
      .then(setRows)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load matrix'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      currency: 'AZN',
      priority: 100,
      active: true,
      effective: [dayjs().startOf('year'), null],
    })
    setOpen(true)
  }

  const openEdit = (r: BonusMatrixRule) => {
    setEditing(r)
    form.setFieldsValue({
      code: r.code,
      description: r.description ?? undefined,
      matchRecommendation: r.matchRecommendation ?? undefined,
      ratingRange:
        r.minRating != null && r.maxRating != null
          ? [Number(r.minRating), Number(r.maxRating)]
          : [null, null],
      bonusPercent: r.bonusPercent != null ? Number(r.bonusPercent) : undefined,
      flatAmount: r.flatAmount != null ? Number(r.flatAmount) : undefined,
      currency: r.currency,
      maxAmount: r.maxAmount != null ? Number(r.maxAmount) : undefined,
      priority: r.priority,
      effective: [dayjs(r.effectiveFrom), r.effectiveTo ? dayjs(r.effectiveTo) : null],
      active: r.active,
    })
    setOpen(true)
  }

  const onFinish = async (v: FormValues) => {
    const [minR, maxR] = v.ratingRange ?? [null, null]
    const payload: BonusMatrixRuleRequest = {
      code: v.code,
      description: v.description,
      matchRecommendation: v.matchRecommendation || undefined,
      minRating: minR ?? undefined,
      maxRating: maxR ?? undefined,
      bonusPercent: v.bonusPercent,
      flatAmount: v.flatAmount,
      currency: v.currency,
      maxAmount: v.maxAmount,
      priority: v.priority,
      effectiveFrom: v.effective[0].format('YYYY-MM-DD'),
      effectiveTo: v.effective[1] ? v.effective[1].format('YYYY-MM-DD') : undefined,
      active: v.active,
    }
    try {
      if (editing) {
        await compBenefitsApi.updateMatrixRule(editing.id, payload)
        message.success('Matrix rule updated')
      } else {
        await compBenefitsApi.createMatrixRule(payload)
        message.success('Matrix rule created')
      }
      setOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const columns: ColumnsType<BonusMatrixRule> = [
    { title: 'Priority', dataIndex: 'priority', width: 90, align: 'right' },
    { title: 'Code', dataIndex: 'code', width: 180 },
    {
      title: 'Recommendation',
      dataIndex: 'matchRecommendation',
      width: 160,
      render: (v: string | null) => (v ? <Tag color="purple">{v}</Tag> : '—'),
    },
    {
      title: 'Rating band',
      render: (_, r) =>
        r.minRating != null && r.maxRating != null ? `${r.minRating} – ${r.maxRating}` : '—',
      width: 130,
    },
    {
      title: 'Amount',
      render: (_, r) =>
        r.flatAmount != null
          ? `${r.flatAmount} ${r.currency}`
          : r.bonusPercent != null
          ? `${r.bonusPercent}% of base`
          : '—',
    },
    {
      title: 'Cap',
      dataIndex: 'maxAmount',
      width: 130,
      render: (v: number | null, r) => (v != null ? `${v} ${r.currency}` : '—'),
    },
    { title: 'From', dataIndex: 'effectiveFrom', width: 110 },
    { title: 'To', dataIndex: 'effectiveTo', width: 110, render: (v: string | null) => v ?? '—' },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    canEdit
      ? {
          title: '',
          width: 80,
          render: (_, r) => (
            <Button size="small" onClick={() => openEdit(r)}>
              Edit
            </Button>
          ),
        }
      : { title: '', width: 0, render: () => null },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Bonus matrix
        </Typography.Title>
      }
      extra={
        canEdit && (
          <Button type="primary" onClick={openCreate}>
            New rule
          </Button>
        )
      }
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        Rules are evaluated by priority (lower wins). When a Performance review carries an explicit
        <code> bonusPercent</code> set during calibration, that always wins — the matrix is the
        fallback when only a recommendation / final rating is available.
      </Typography.Paragraph>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />

      <Modal
        open={open}
        title={editing ? `Edit ${editing.code}` : 'New matrix rule'}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        okText="Save"
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Row gutter={16}>
            <Col span={10}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="e.g. BONUS_TIER_A" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="matchRecommendation" label="Match recommendation">
                <Input placeholder="e.g. BONUS_TIER_A (blank = any)" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="priority" label="Priority">
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="ratingRange" label="Final-rating band (min – max), inclusive">
            <RangeInputs />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="bonusPercent" label="Bonus % of base">
                <InputNumber min={0} max={500} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="flatAmount" label="Flat amount">
                <InputNumber min={0} step={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="maxAmount" label="Cap">
                <InputNumber min={0} step={50} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="effective" label="Effective period" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} allowEmpty={[false, true]} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="active" valuePropName="checked">
            <Checkbox>Active</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

interface RangeInputsProps {
  value?: [number | null, number | null]
  onChange?: (v: [number | null, number | null]) => void
}

function RangeInputs({ value, onChange }: RangeInputsProps) {
  const [min, max] = value ?? [null, null]
  return (
    <Space>
      <InputNumber
        min={0}
        max={5}
        step={0.1}
        placeholder="min"
        value={min ?? undefined}
        onChange={(v) => onChange?.([v == null ? null : Number(v), max ?? null])}
      />
      <span>—</span>
      <InputNumber
        min={0}
        max={5}
        step={0.1}
        placeholder="max"
        value={max ?? undefined}
        onChange={(v) => onChange?.([min ?? null, v == null ? null : Number(v)])}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Leave both blank to match any rating.
      </Typography.Text>
    </Space>
  )
}
