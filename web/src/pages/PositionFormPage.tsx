import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { positionsApi, type Position, type PositionRequest } from '../api/positions'
import { FormPageShell } from '../components/FormPageShell'
import { PositionLifecyclePanel } from '../components/PositionLifecyclePanel'

interface FormValues {
  title: string
  parentPositionId?: string
  orgUnitLabel?: string
  grade?: string
  jobFamily?: string
  jobLevel?: string
  approvedHeadcount: number
  salaryMin?: number
  salaryMax?: number
  currency?: string
  employmentType?: string
  costCentre?: string
  budgetCode?: string
  location?: string
  effectiveFrom?: dayjs.Dayjs
  effectiveTo?: dayjs.Dayjs
}

const LIST_PATH = '/positions'

export function PositionFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  // M243 — kept in sync with the loaded position so the lifecycle panel
  // re-renders after a transition without re-fetching the whole form.
  const [current, setCurrent] = useState<Position | null>(null)
  const [positionOptions, setPositionOptions] = useState<{ value: string; label: string }[]>([])

  useEffect(() => {
    positionsApi.list({ size: 500 }).then((r) => {
      setPositionOptions(r.content.map((p) => ({ value: p.id, label: `${p.code} — ${p.title}` })))
    })
  }, [])

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ currency: 'AZN', approvedHeadcount: 1 })
      return
    }
    setLoading(true)
    positionsApi
      .get(id!)
      .then((p: Position) => {
        setCurrent(p)
        form.setFieldsValue({
          title: p.title,
          parentPositionId: p.parentPositionId ?? undefined,
          orgUnitLabel: p.orgUnitLabel ?? undefined,
          grade: p.grade ?? undefined,
          jobFamily: p.jobFamily ?? undefined,
          jobLevel: p.jobLevel ?? undefined,
          approvedHeadcount: p.approvedHeadcount,
          salaryMin: p.salaryMin ?? undefined,
          salaryMax: p.salaryMax ?? undefined,
          currency: p.currency,
          employmentType: p.employmentType ?? undefined,
          costCentre: p.costCentre ?? undefined,
          budgetCode: p.budgetCode ?? undefined,
          location: p.location ?? undefined,
          effectiveFrom: p.effectiveFrom ? dayjs(p.effectiveFrom) : undefined,
          effectiveTo: p.effectiveTo ? dayjs(p.effectiveTo) : undefined,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load position'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: PositionRequest = {
      ...v,
      effectiveFrom: v.effectiveFrom?.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo?.format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await positionsApi.update(id!, payload)
        message.success('Position updated')
      } else {
        await positionsApi.create(payload)
        message.success('Position created')
      }
      navigate(LIST_PATH)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell title={editing ? 'Edit position' : 'New position'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <>
          {editing && current && (
            <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 6 }}>
              <PositionLifecyclePanel
                position={current}
                onChange={(updated) => setCurrent(updated)}
              />
            </div>
          )}
          <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentPositionId" label="Parent position">
            <Select
              allowClear
              showSearch
              placeholder="None (root position)"
              optionFilterProp="label"
              options={positionOptions.filter((o) => o.value !== id)}
            />
          </Form.Item>
          <Form.Item name="orgUnitLabel" label="Department / Unit" rules={[{ max: 200 }]}>
            <Input placeholder="e.g. Engineering · Platform" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="approvedHeadcount"
                label="Approved headcount"
                rules={[{ required: true }]}
              >
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="grade" label="Grade" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="jobLevel" label="Job level" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="jobFamily" label="Job family" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="employmentType" label="Employment type" rules={[{ max: 32 }]}>
                <Input placeholder="FULL_TIME / PART_TIME / CONTRACT" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="salaryMin" label="Salary min">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="salaryMax" label="Salary max">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="budgetCode" label="Budget code" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="location" label="Location" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="effectiveFrom" label="Effective from">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="effectiveTo" label="Effective to">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create position'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
        </>
      )}
    </FormPageShell>
  )
}
