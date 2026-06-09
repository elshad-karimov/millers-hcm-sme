// M79 — Self-service "Request a personal-info change" form.
//
// Employees can edit their own profile fields, but the change goes through
// HR approval. Each submit creates one row per field changed.

import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  PERSONAL_INFO_FIELDS,
  selfPersonalInfoApi,
  type PersonalInfoFieldKey,
} from '../api/personalInfo'

export function PersonalInfoRequestFormPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  // M237 — personalInfo namespace + common. Per-field help text lives
  // in JSON; the dictionary key is the same as the enum value, so
  // adding a new field = one row per locale, zero code changes.
  const { t } = useTranslation(['personalInfo', 'common'])
  const [submitting, setSubmitting] = useState(false)
  const [field, setField] = useState<PersonalInfoFieldKey | undefined>()
  const [form] = Form.useForm<{
    fieldKey: PersonalInfoFieldKey
    newValue: string
    reason?: string
  }>()

  const onFinish = async (values: {
    fieldKey: PersonalInfoFieldKey
    newValue: string
    reason?: string
  }) => {
    setSubmitting(true)
    try {
      const created = await selfPersonalInfoApi.submit({
        fieldKey: values.fieldKey,
        newValue: values.newValue,
        reason: values.reason,
      })
      message.success(t('personalInfo:newRequest.messages.submitted', { requestNo: created.requestNo }))
      navigate('/my')
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          t('personalInfo:newRequest.messages.submitFailed'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('personalInfo:newRequest.title')}
        </Typography.Title>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16, maxWidth: 720 }}
        message={t('personalInfo:newRequest.alertTitle')}
        description={t('personalInfo:newRequest.alertDescription')}
      />

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        style={{ maxWidth: 720 }}
      >
        <Form.Item
          label={t('personalInfo:newRequest.field')}
          name="fieldKey"
          rules={[{ required: true, message: t('personalInfo:newRequest.pickField') }]}
        >
          <Select
            placeholder={t('personalInfo:newRequest.fieldPlaceholder')}
            options={PERSONAL_INFO_FIELDS.map((f) => ({ value: f, label: f }))}
            onChange={(v) => setField(v)}
          />
        </Form.Item>

        {field && (
          <>
            <Form.Item
              label={t('personalInfo:newRequest.newValue')}
              name="newValue"
              extra={t(`personalInfo:newRequest.fieldHelp.${field}`)}
              rules={[{ required: true, message: t('personalInfo:newRequest.enterNewValue') }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label={t('personalInfo:newRequest.reason')} name="reason">
              <Input.TextArea rows={2}
                placeholder={t('personalInfo:newRequest.reasonPlaceholder')} />
            </Form.Item>
          </>
        )}

        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            {t('personalInfo:newRequest.submit')}
          </Button>
          <Button onClick={() => navigate('/my')}>{t('common:cancel')}</Button>
        </Space>
      </Form>
    </Card>
  )
}
