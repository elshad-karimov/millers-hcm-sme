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
import { useTranslation } from 'react-i18next'
import {
  letterTemplatesApi,
  selfLettersApi,
  type LetterTemplate,
} from '../api/letters'

export function LetterRequestFormPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  // M236 — letters namespace + common for the shared Cancel button.
  const { t } = useTranslation(['letters', 'common'])
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
        message.error(e?.response?.data?.message ?? t('letters:newRequest.loadFailed')),
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
      message.success(t('letters:newRequest.messages.submitted', { requestNo: created.requestNo }))
      navigate('/my')
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          t('letters:newRequest.messages.submitFailed'),
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
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>{t('letters:newRequest.title')}</Typography.Title>}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        style={{ maxWidth: 720 }}
      >
        <Form.Item
          label={t('letters:newRequest.template')}
          name="templateId"
          rules={[{ required: true, message: t('letters:newRequest.pickTemplate') }]}
        >
          <Select
            placeholder={t('letters:newRequest.templatePlaceholder')}
            options={templates.map((tpl) => ({ value: tpl.id, label: tpl.name }))}
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
                  ? t('letters:newRequest.requiresApproval')
                  : t('letters:newRequest.autoIssued')
              }
              description={selected.description ?? null}
            />

            <Form.Item label={t('letters:newRequest.purpose')} name="purpose">
              <Input.TextArea rows={2}
                placeholder={t('letters:newRequest.purposePlaceholder')} />
            </Form.Item>

            {selected.placeholdersJson &&
              Object.entries(selected.placeholdersJson).map(([key, description]) => (
                <Form.Item
                  key={key}
                  label={`${key} — ${description}`}
                  name={['customFields', key]}
                  rules={[{ required: true, message: t('letters:newRequest.fieldRequired') }]}
                >
                  <Input />
                </Form.Item>
              ))}
          </>
        )}

        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            {t('letters:newRequest.submit')}
          </Button>
          <Button onClick={() => navigate('/my')}>{t('common:cancel')}</Button>
        </Space>
      </Form>
    </Card>
  )
}
