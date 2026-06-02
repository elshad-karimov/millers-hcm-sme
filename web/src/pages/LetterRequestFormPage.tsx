// M77 — Self-service "Request a letter" form.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import {
  letterTemplatesApi,
  selfLettersApi,
  type LetterTemplate,
} from '../api/letters'

export function LetterRequestFormPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [templates, setTemplates] = useState<LetterTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [templateId, setTemplateId] = useState<string | undefined>()
  const [form] = Form.useForm<{
    templateId: string
    purpose?: string
    customFields?: Record<string, string>
  }>()

  useEffect(() => {
    letterTemplatesApi
      .list(true)
      .then(setTemplates)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load templates'),
      )
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const selected = useMemo(
    () => templates.find((t) => t.id === templateId),
    [templates, templateId],
  )

  const onFinish = async (values: {
    templateId: string
    purpose?: string
    customFields?: Record<string, string>
  }) => {
    setSubmitting(true)
    try {
      const created = await selfLettersApi.submit({
        templateId: values.templateId,
        purpose: values.purpose,
        customFields: values.customFields,
      })
      message.success(`Request ${created.requestNo} submitted`)
      navigate('/my')
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Submit failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Request a letter</Typography.Title>}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        style={{ maxWidth: 720 }}
      >
        <Form.Item
          label="Template"
          name="templateId"
          rules={[{ required: true, message: 'Pick a template' }]}
        >
          <Select
            placeholder="Select a letter template"
            options={templates.map((t) => ({ value: t.id, label: t.name }))}
            onChange={(v) => setTemplateId(v)}
          />
        </Form.Item>

        {selected && (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={
                selected.requiresApproval
                  ? 'This template requires HR approval before issuing.'
                  : 'This template is auto-issued on submit.'
              }
              description={selected.description ?? null}
            />

            <Form.Item label="Purpose" name="purpose">
              <Input.TextArea rows={2}
                placeholder="Short reason — included in the audit trail" />
            </Form.Item>

            {selected.placeholdersJson &&
              Object.entries(selected.placeholdersJson).map(([key, description]) => (
                <Form.Item
                  key={key}
                  label={`${key} — ${description}`}
                  name={['customFields', key]}
                  rules={[{ required: true, message: 'Required' }]}
                >
                  <Input />
                </Form.Item>
              ))}
          </>
        )}

        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            Submit request
          </Button>
          <Button onClick={() => navigate('/my')}>Cancel</Button>
        </Space>
      </Form>
    </Card>
  )
}
