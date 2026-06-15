import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  recruitmentApi,
  type Candidate,
  type CandidateRequest,
  type CandidateSource,
} from '../api/recruitment'
import { FormPageShell } from '../components/FormPageShell'
import { CandidateProfilePanels } from '../components/CandidateProfilePanels'

const LIST_PATH = '/recruitment/candidates'

const SOURCES: CandidateSource[] = [
  'LINKEDIN',
  'REFERRAL',
  'WEBSITE',
  'AGENCY',
  'JOB_BOARD',
  'INTERNAL',
  'OTHER',
]

export function CandidateFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<CandidateRequest>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ currency: 'AZN' })
      return
    }
    setLoading(true)
    recruitmentApi
      .candidate(id!)
      .then((c: Candidate) => {
        form.setFieldsValue({
          firstName: c.firstName,
          lastName: c.lastName,
          middleName: c.middleName ?? undefined,
          email: c.email ?? undefined,
          phone: c.phone ?? undefined,
          source: c.source ?? undefined,
          cvUrl: c.cvUrl ?? undefined,
          experienceYears: c.experienceYears ?? undefined,
          expectedSalary: c.expectedSalary ?? undefined,
          currency: c.currency,
          skills: c.skills ?? undefined,
          notes: c.notes ?? undefined,
        })
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load candidate'))
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: CandidateRequest) => {
    setSaving(true)
    try {
      if (editing) {
        await recruitmentApi.updateCandidate(id!, v)
        message.success('Candidate updated')
      } else {
        await recruitmentApi.createCandidate(v)
        message.success('Candidate created')
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
    <FormPageShell title={editing ? 'Edit candidate' : 'New candidate'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 720 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="firstName" label="First name" rules={[{ required: true, max: 100 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="lastName" label="Last name" rules={[{ required: true, max: 100 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="middleName" label="Middle name" rules={[{ max: 100 }]}>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="email"
                label="Email"
                rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="Phone" rules={[{ max: 40 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="source" label="Source">
                <Select
                  allowClear
                  options={SOURCES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="experienceYears" label="Experience (years)">
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="expectedSalary" label="Expected salary (monthly)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cvUrl" label="CV URL">
                <Input placeholder="https://…/cv.pdf" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="skills" label="Skills (comma-separated)">
            <Input placeholder="Java, PostgreSQL, React, …" />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create candidate'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
      {/* M291 — structured profile (education / experience / skills); edit only */}
      {editing && !loading && (
        <div style={{ maxWidth: 720 }}>
          <CandidateProfilePanels candidateId={id!} />
        </div>
      )}
    </FormPageShell>
  )
}
