// HCM_12 M388 — tenant-configurable rating scale master (PRD §5.3 / §18.3).
// HR Admin manages scales; the DEFAULT_5PT scale is seeded. Score bands drive the
// numeric-score → rating-label conversion used by weighted scoring (M394).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  RATING_SCALE_TYPE_LABEL,
  ratingScalesApi,
  type RatingScaleRequest,
  type RatingScaleResponse,
  type RatingScaleType,
  type RatingScaleValue,
} from '../../api/performance'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const TYPE_OPTIONS = (Object.keys(RATING_SCALE_TYPE_LABEL) as RatingScaleType[]).map((k) => ({
  value: k,
  label: RATING_SCALE_TYPE_LABEL[k],
}))

export function RatingScalesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<RatingScaleResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<RatingScaleResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [values, setValues] = useState<RatingScaleValue[]>([])
  const [form] = Form.useForm<{
    scaleCode: string
    scaleName: string
    scaleType: RatingScaleType
    description?: string
    active: boolean
    isDefault: boolean
  }>()

  const load = () => {
    setLoading(true)
    ratingScalesApi
      .list(false)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load rating scales'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ scaleType: 'NUMERIC_1_5', active: true, isDefault: false })
    setValues([
      { ratingValue: 1, ratingLabel: 'Unsatisfactory', minPercentage: 0, maxPercentage: 39.99 },
      { ratingValue: 2, ratingLabel: 'Needs Improvement', minPercentage: 40, maxPercentage: 59.99 },
      { ratingValue: 3, ratingLabel: 'Meets Expectations', minPercentage: 60, maxPercentage: 74.99 },
      { ratingValue: 4, ratingLabel: 'Exceeds Expectations', minPercentage: 75, maxPercentage: 89.99 },
      { ratingValue: 5, ratingLabel: 'Outstanding', minPercentage: 90, maxPercentage: 100 },
    ])
    setOpen(true)
  }
  const startEdit = (s: RatingScaleResponse) => {
    setEditing(s)
    form.setFieldsValue({
      scaleCode: s.scaleCode,
      scaleName: s.scaleName,
      scaleType: s.scaleType,
      description: s.description ?? undefined,
      active: s.active,
      isDefault: s.isDefault,
    })
    setValues(s.values.map((v) => ({ ...v })))
    setOpen(true)
  }
  const patchValue = (i: number, patch: Partial<RatingScaleValue>) =>
    setValues((cur) => cur.map((v, idx) => (idx === i ? { ...v, ...patch } : v)))
  const submit = async () => {
    const v = await form.validateFields()
    const valid = values.filter((x) => x.ratingLabel.trim())
    if (!valid.length) { message.error('Add at least one scale value'); return }
    const req: RatingScaleRequest = { ...v, values: valid }
    setSaving(true)
    try {
      if (editing) { await ratingScalesApi.update(editing.id, req); message.success('Scale updated') }
      else { await ratingScalesApi.create(req); message.success('Scale created') }
      setOpen(false); load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed')
    } finally { setSaving(false) }
  }

  const cols: ColumnsType<RatingScaleResponse> = [
    {
      title: 'Code',
      dataIndex: 'scaleCode',
      width: 140,
      render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'scaleName' },
    {
      title: 'Type',
      dataIndex: 'scaleType',
      width: 140,
      render: (t: RatingScaleType) => <Tag>{RATING_SCALE_TYPE_LABEL[t]}</Tag>,
    },
    {
      title: 'Values',
      render: (_, r) => (
        <Space size={4} wrap>
          {r.values.map((v) => (
            <Tag key={v.id ?? v.ratingLabel} color={v.colorCode ?? 'default'}>
              {v.ratingValue} · {v.ratingLabel}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Default',
      width: 90,
      align: 'center',
      render: (_, r) => (r.isDefault ? <Tag color="blue">Default</Tag> : '—'),
    },
    {
      title: 'Status',
      width: 90,
      align: 'center',
      render: (_, r) => (r.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>),
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Rating scales</Title>
      <Text type="secondary">
        Tenant-configurable rating scales for performance reviews. Score bands (min–max %)
        convert a weighted numeric score into the rating label.
      </Text>
      {canEdit && <div><Button type="primary" onClick={startCreate}>New scale…</Button></div>}
      <Card>
        <Table rowKey="id" columns={cols} dataSource={rows} size="small" pagination={false}
          locale={{ emptyText: <Empty description="No rating scales" /> }} />
      </Card>

      <Modal open={open} width={760}
        title={editing ? `Edit scale — ${editing.scaleCode}` : 'New rating scale'}
        onCancel={() => setOpen(false)} onOk={submit} confirmLoading={saving}
        okText={editing ? 'Save' : 'Create'}>
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={7}>
              <Form.Item name="scaleCode" label="Code" rules={[{ required: true }, { max: 40 }]}>
                <Input placeholder="DEFAULT_5PT" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={11}>
              <Form.Item name="scaleName" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Default 5-Point Scale" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="scaleType" label="Type" rules={[{ required: true }]}>
                <Select options={TYPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={14}>
              <Form.Item name="description" label="Description">
                <Input.TextArea rows={1} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="isDefault" label="Tenant default" valuePropName="checked"
                tooltip="Used when a cycle doesn't pick a scale explicitly.">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Text strong>Scale values (ascending) + score bands</Text>
          <Paragraph type="secondary" style={{ fontSize: 12, margin: '4px 0' }}>
            Bands must ascend without overlapping; a weighted score of e.g. 82 falls into the
            75–89.99 band → that rating label.
          </Paragraph>
          {values.map((v, i) => (
            <Row gutter={8} key={i} align="middle" style={{ marginBottom: 6 }}>
              <Col span={3}>
                <InputNumber style={{ width: '100%' }} min={0} value={v.ratingValue}
                  onChange={(x) => patchValue(i, { ratingValue: x ?? 0 })} />
              </Col>
              <Col span={8}>
                <Input placeholder="Label" value={v.ratingLabel}
                  onChange={(e) => patchValue(i, { ratingLabel: e.target.value })} />
              </Col>
              <Col span={4}>
                <InputNumber style={{ width: '100%' }} min={0} max={100} placeholder="Min %"
                  value={v.minPercentage ?? undefined}
                  onChange={(x) => patchValue(i, { minPercentage: x })} />
              </Col>
              <Col span={4}>
                <InputNumber style={{ width: '100%' }} min={0} max={100} placeholder="Max %"
                  value={v.maxPercentage ?? undefined}
                  onChange={(x) => patchValue(i, { maxPercentage: x })} />
              </Col>
              <Col span={3}>
                <Input placeholder="colour" value={v.colorCode ?? undefined}
                  onChange={(e) => patchValue(i, { colorCode: e.target.value })} />
              </Col>
              <Col span={2}>
                {values.length > 1 && (
                  <Button size="small" danger
                    onClick={() => setValues((c) => c.filter((_, idx) => idx !== i))}>✕</Button>
                )}
              </Col>
            </Row>
          ))}
          <Button size="small"
            onClick={() => setValues((c) => [...c, { ratingValue: (c.at(-1)?.ratingValue ?? 0) + 1, ratingLabel: '' }])}>
            Add value
          </Button>
        </Form>
      </Modal>
    </Space>
  )
}
