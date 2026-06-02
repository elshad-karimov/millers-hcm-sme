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
import {
  PERSONAL_INFO_FIELDS,
  selfPersonalInfoApi,
  type PersonalInfoFieldKey,
} from '../api/personalInfo'

const FIELD_HELP: Record<PersonalInfoFieldKey, string> = {
  email: 'Work or personal e-mail — must be a valid address.',
  phone: '+994 55 123 4567 — digits, spaces, parentheses, hyphens allowed.',
  addressLine1: 'Free text, up to 200 characters.',
  addressLine2: 'Free text, up to 200 characters.',
  city: 'Free text, up to 200 characters.',
  district: 'Free text, up to 200 characters.',
  postalCode: 'Alphanumeric postal code (3-20 chars).',
  country: 'ISO 3166-1 alpha-2 code, e.g. AZ, TR.',
  maritalStatus: 'SINGLE, MARRIED, DIVORCED, WIDOWED, DOMESTIC_PARTNERSHIP.',
  emergencyContactName: 'Full name, up to 200 characters.',
  emergencyContactPhone: 'Phone number with country code.',
}

export function PersonalInfoRequestFormPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
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
      message.success(`Submitted as ${created.requestNo} — awaiting HR approval`)
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

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Request a personal-info change
        </Typography.Title>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16, maxWidth: 720 }}
        message="HR approval is required"
        description="Your edits stay pending until an HR specialist reviews them. The change is applied automatically once approved."
      />

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        style={{ maxWidth: 720 }}
      >
        <Form.Item
          label="Field"
          name="fieldKey"
          rules={[{ required: true, message: 'Pick a field' }]}
        >
          <Select
            placeholder="Select the field you want to change"
            options={PERSONAL_INFO_FIELDS.map((f) => ({ value: f, label: f }))}
            onChange={(v) => setField(v)}
          />
        </Form.Item>

        {field && (
          <>
            <Form.Item
              label="New value"
              name="newValue"
              extra={FIELD_HELP[field]}
              rules={[{ required: true, message: 'Enter the new value' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label="Reason (optional)" name="reason">
              <Input.TextArea rows={2}
                placeholder="Why are you requesting this change?" />
            </Form.Item>
          </>
        )}

        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            Submit for approval
          </Button>
          <Button onClick={() => navigate('/my')}>Cancel</Button>
        </Space>
      </Form>
    </Card>
  )
}
