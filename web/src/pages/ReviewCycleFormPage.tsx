import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import {
  performanceApi,
  type CycleType,
  type ReviewCycle,
  type ReviewCycleRequest,
} from '../api/performance'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/performance/cycles'

const TYPE_OPTIONS: { value: CycleType; label: string }[] = [
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'MID_YEAR', label: 'Mid-year' },
  { value: 'QUARTERLY', label: 'Quarterly' },
  { value: 'PROBATION', label: 'Probation' },
  { value: 'PROJECT', label: 'Project' },
  { value: 'ADHOC', label: 'Ad-hoc' },
]

interface FormValues {
  code: string
  name: string
  cycleType: CycleType
  period: [dayjs.Dayjs, dayjs.Dayjs]
  selfReviewDue?: dayjs.Dayjs
  managerReviewDue?: dayjs.Dayjs
  finalDue?: dayjs.Dayjs
  description?: string
}

export function ReviewCycleFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ cycleType: 'ANNUAL' })
      return
    }
    setLoading(true)
    performanceApi
      .cycle(id!)
      .then((c: ReviewCycle) => {
        form.setFieldsValue({
          code: c.code,
          name: c.name,
          cycleType: c.cycleType,
          period: [dayjs(c.periodStart), dayjs(c.periodEnd)],
          selfReviewDue: c.selfReviewDue ? dayjs(c.selfReviewDue) : undefined,
          managerReviewDue: c.managerReviewDue ? dayjs(c.managerReviewDue) : undefined,
          finalDue: c.finalDue ? dayjs(c.finalDue) : undefined,
          description: c.description ?? undefined,
        })
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load cycle'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: ReviewCycleRequest = {
      code: v.code,
      name: v.name,
      cycleType: v.cycleType,
      periodStart: v.period[0].format('YYYY-MM-DD'),
      periodEnd: v.period[1].format('YYYY-MM-DD'),
      selfReviewDue: v.selfReviewDue?.format('YYYY-MM-DD'),
      managerReviewDue: v.managerReviewDue?.format('YYYY-MM-DD'),
      finalDue: v.finalDue?.format('YYYY-MM-DD'),
      description: v.description,
    }
    try {
      if (editing) {
        await performanceApi.updateCycle(id!, payload)
        message.success('Cycle updated')
      } else {
        await performanceApi.createCycle(payload)
        message.success('Cycle created')
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
    <FormPageShell title={editing ? 'Edit review cycle' : 'New review cycle'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 40 }]}>
                <Input placeholder="e.g. ANNUAL-2026" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="cycleType" label="Type" rules={[{ required: true }]}>
                <Select options={TYPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="period" label="Cycle period" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%', maxWidth: 360 }} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="selfReviewDue" label="Self-review due">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="managerReviewDue" label="Manager review due">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="finalDue" label="Final due">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create cycle'}
              </Button>
            </Space>
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            New cycles start in DRAFT. Open the cycle from the list to allow goal-setting and
            review submissions. Default 1–5 rating scale applies unless a custom one is configured
            later.
          </Typography.Text>
        </Form>
      )}
    </FormPageShell>
  )
}
