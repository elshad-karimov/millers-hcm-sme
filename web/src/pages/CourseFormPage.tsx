import { useEffect, useState } from 'react'
import {
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  learningApi,
  type Course,
  type CourseCategory,
  type CourseRequest,
} from '../api/learning'
import { FormPageShell } from '../components/FormPageShell'

const LIST_PATH = '/learning/courses'

const CATEGORIES: { value: CourseCategory; label: string }[] = [
  { value: 'COMPLIANCE', label: 'Compliance' },
  { value: 'ONBOARDING', label: 'Onboarding' },
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'LEADERSHIP', label: 'Leadership' },
  { value: 'SOFT_SKILLS', label: 'Soft skills' },
  { value: 'OTHER', label: 'Other' },
]

interface FormValues {
  code: string
  title: string
  description?: string
  contentMarkdown?: string
  category: CourseCategory
  durationHours?: number
  mandatory: boolean
  passingScore?: number
  maxAttempts?: number
  validForMonths?: number
  coverUrl?: string
}

export function CourseFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({
        category: 'TECHNICAL',
        mandatory: false,
        durationHours: 1,
        passingScore: 70,
        maxAttempts: 3,
      })
      return
    }
    setLoading(true)
    learningApi
      .course(id!)
      .then((c: Course) => {
        form.setFieldsValue({
          code: c.code,
          title: c.title,
          description: c.description ?? undefined,
          contentMarkdown: c.contentMarkdown ?? undefined,
          category: c.category,
          durationHours: Number(c.durationHours),
          mandatory: c.mandatory,
          passingScore: c.passingScore,
          maxAttempts: c.maxAttempts,
          validForMonths: c.validForMonths ?? undefined,
          coverUrl: c.coverUrl ?? undefined,
        })
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load course'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: CourseRequest = {
      code: v.code,
      title: v.title,
      description: v.description,
      contentMarkdown: v.contentMarkdown,
      category: v.category,
      durationHours: v.durationHours,
      mandatory: v.mandatory,
      passingScore: v.passingScore,
      maxAttempts: v.maxAttempts,
      validForMonths: v.validForMonths,
      coverUrl: v.coverUrl,
    }
    try {
      const saved = editing
        ? await learningApi.updateCourse(id!, payload)
        : await learningApi.createCourse(payload)
      message.success(editing ? 'Course updated' : 'Course created')
      navigate(`/learning/courses/${saved.id}`)
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
    <FormPageShell title={editing ? 'Edit course' : 'New course'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 880 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="e.g. INFOSEC-101" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="title" label="Title" rules={[{ required: true, max: 240 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Short description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="contentMarkdown"
            label="Course body (markdown)"
            tooltip="Plain markdown — rendered in the course detail page."
          >
            <Input.TextArea rows={8} placeholder="# Introduction&#10;..." />
          </Form.Item>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="category" label="Category" rules={[{ required: true }]}>
                <Select options={CATEGORIES} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="durationHours" label="Duration (h)">
                <InputNumber min={0.25} step={0.25} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="passingScore" label="Pass score (%)">
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="maxAttempts" label="Max attempts">
                <InputNumber min={1} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="validForMonths" label="Certificate validity (months)">
                <InputNumber min={1} max={120} style={{ width: '100%' }} placeholder="∞" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="mandatory" valuePropName="checked">
                <Checkbox>Mandatory for all employees</Checkbox>
              </Form.Item>
            </Col>
            <Col span={18}>
              <Form.Item name="coverUrl" label="Cover image URL">
                <Input placeholder="https://…/cover.png" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create course'}
              </Button>
            </Space>
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            New courses start in DRAFT. Add quiz questions on the course detail page, then
            publish it so employees can enroll.
          </Typography.Text>
        </Form>
      )}
    </FormPageShell>
  )
}
