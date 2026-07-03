// HCM_12 M389 — review templates (PRD §5.2 / §18.1). HR Admin defines which sections a
// review form has and each scoring section's weight; scoring weights must total 100%.

import { useEffect, useMemo, useState } from 'react'
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
  SCORING_SECTIONS,
  SECTION_TYPE_LABEL,
  perfTemplatesApi,
  type PerfSectionType,
  type PerfTemplateRequest,
  type PerfTemplateResponse,
  type TemplateSection,
} from '../../api/performance'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const SECTION_OPTIONS = (Object.keys(SECTION_TYPE_LABEL) as PerfSectionType[]).map((k) => ({
  value: k,
  label: SECTION_TYPE_LABEL[k],
}))

export function ReviewTemplatesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<PerfTemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PerfTemplateResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [sections, setSections] = useState<TemplateSection[]>([])
  const [form] = Form.useForm<{
    templateCode: string
    templateName: string
    description?: string
    employeeType?: string
    active: boolean
  }>()

  const load = () => {
    setLoading(true)
    perfTemplatesApi.list(false)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load templates'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const scoringTotal = useMemo(
    () => sections
      .filter((s) => SCORING_SECTIONS.includes(s.sectionType))
      .reduce((sum, s) => sum + (Number(s.weightPercent) || 0), 0),
    [sections],
  )

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ active: true })
    setSections([
      { sectionType: 'GOALS', weightPercent: 50, required: true },
      { sectionType: 'KPI', weightPercent: 25, required: true },
      { sectionType: 'COMPETENCY', weightPercent: 20, required: true },
      { sectionType: 'VALUES', weightPercent: 5, required: true },
      { sectionType: 'MANAGER_COMMENTS', weightPercent: 0, required: true },
      { sectionType: 'FINAL_RATING', weightPercent: 0, required: true },
    ])
    setOpen(true)
  }
  const startEdit = (t: PerfTemplateResponse) => {
    setEditing(t)
    form.setFieldsValue({
      templateCode: t.templateCode,
      templateName: t.templateName,
      description: t.description ?? undefined,
      employeeType: t.employeeType ?? undefined,
      active: t.active,
    })
    setSections(t.sections.map((s) => ({ ...s })))
    setOpen(true)
  }
  const patchSection = (i: number, patch: Partial<TemplateSection>) =>
    setSections((cur) => cur.map((s, idx) => (idx === i ? { ...s, ...patch } : s)))
  const submit = async () => {
    const v = await form.validateFields()
    if (scoringTotal !== 0 && scoringTotal !== 100) {
      message.error(`Scoring section weights must total 100% (currently ${scoringTotal}%)`)
      return
    }
    const req: PerfTemplateRequest = { ...v, sections }
    setSaving(true)
    try {
      if (editing) { await perfTemplatesApi.update(editing.id, req); message.success('Template updated') }
      else { await perfTemplatesApi.create(req); message.success('Template created') }
      setOpen(false); load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed')
    } finally { setSaving(false) }
  }

  const cols: ColumnsType<PerfTemplateResponse> = [
    {
      title: 'Code',
      dataIndex: 'templateCode',
      width: 160,
      render: (v, r) => <a onClick={() => canEdit && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'templateName' },
    {
      title: 'Sections',
      render: (_, r) => (
        <Space size={4} wrap>
          {r.sections.map((s) => (
            <Tag key={s.id ?? s.sectionType} color={s.scoring ? 'geekblue' : 'default'}>
              {SECTION_TYPE_LABEL[s.sectionType]}{s.scoring ? ` ${s.weightPercent}%` : ''}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Weights',
      width: 100,
      align: 'center',
      render: (_, r) => (
        <Tag color={r.scoringWeightTotal === 100 ? 'green' : 'red'}>{r.scoringWeightTotal}%</Tag>
      ),
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
      <Title level={3} style={{ margin: 0 }}>Review templates</Title>
      <Text type="secondary">
        Templates define which sections a review form contains and the weight of each scoring
        section in the overall score (weights must total 100%).
      </Text>
      {canEdit && <div><Button type="primary" onClick={startCreate}>New template…</Button></div>}
      <Card>
        <Table rowKey="id" columns={cols} dataSource={rows} size="small" pagination={false}
          locale={{ emptyText: <Empty description="No templates" /> }} />
      </Card>

      <Modal open={open} width={780}
        title={editing ? `Edit template — ${editing.templateCode}` : 'New review template'}
        onCancel={() => setOpen(false)} onOk={submit} confirmLoading={saving}
        okText={editing ? 'Save' : 'Create'}>
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="templateCode" label="Code" rules={[{ required: true }, { max: 40 }]}>
                <Input placeholder="DEFAULT_ANNUAL" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="templateName" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Default Annual Review" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="description" label="Description">
                <Input.TextArea rows={1} />
              </Form.Item>
            </Col>
            <Col span={7}>
              <Form.Item name="employeeType" label="Employee type (blank = any)">
                <Input placeholder="FULL_TIME" />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Space style={{ justifyContent: 'space-between', width: '100%' }}>
            <Text strong>Sections (in order)</Text>
            <Tag color={scoringTotal === 100 || scoringTotal === 0 ? 'green' : 'red'}>
              Scoring weights: {scoringTotal}%
            </Tag>
          </Space>
          <Paragraph type="secondary" style={{ fontSize: 12, margin: '4px 0' }}>
            Scoring sections (Goals/KPI/OKR/Competency/Values/Behavioral) carry weight and must
            total 100%. Other sections are informational (weight 0).
          </Paragraph>
          {sections.map((s, i) => {
            const scoring = SCORING_SECTIONS.includes(s.sectionType)
            return (
              <Row gutter={8} key={i} align="middle" style={{ marginBottom: 6 }}>
                <Col span={8}>
                  <Select style={{ width: '100%' }} value={s.sectionType} options={SECTION_OPTIONS}
                    onChange={(v) => patchSection(i, { sectionType: v, weightPercent: SCORING_SECTIONS.includes(v) ? s.weightPercent : 0 })} />
                </Col>
                <Col span={7}>
                  <Input placeholder="Title (optional)" value={s.title ?? undefined}
                    onChange={(e) => patchSection(i, { title: e.target.value })} />
                </Col>
                <Col span={4}>
                  <InputNumber style={{ width: '100%' }} min={0} max={100} addonAfter="%"
                    disabled={!scoring} value={s.weightPercent}
                    onChange={(v) => patchSection(i, { weightPercent: v ?? 0 })} />
                </Col>
                <Col span={3}>
                  <Switch checkedChildren="req" unCheckedChildren="opt" checked={s.required !== false}
                    onChange={(c) => patchSection(i, { required: c })} />
                </Col>
                <Col span={2}>
                  {sections.length > 1 && (
                    <Button size="small" danger
                      onClick={() => setSections((c) => c.filter((_, idx) => idx !== i))}>✕</Button>
                  )}
                </Col>
              </Row>
            )
          })}
          <Button size="small"
            onClick={() => setSections((c) => [...c, { sectionType: 'SUMMARY', weightPercent: 0, required: false }])}>
            Add section
          </Button>
        </Form>
      </Modal>
    </Space>
  )
}
