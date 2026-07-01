import { useEffect, useState } from 'react'
import { Button, Card, Form, InputNumber, Select, Typography, App as AntdApp } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import {
  compensationApi,
  type CompConfig,
  type BudgetExceededPolicy,
} from '../api/compensation'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title } = Typography

const BUDGET_POLICIES: { value: BudgetExceededPolicy; label: string }[] = [
  { value: 'WARNING', label: 'Warning' },
  { value: 'HARD_STOP', label: 'Hard Stop' },
  { value: 'EXCEPTION_APPROVAL', label: 'Exception Approval' },
]

export function CompensationConfigPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<CompConfig>()

  const load = () => {
    setLoading(true)
    compensationApi
      .getConfig()
      .then((cfg) => form.setFieldsValue(cfg))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load config'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = async (v: CompConfig) => {
    setSaving(true)
    try {
      await compensationApi.updateConfig(v)
      message.success('Configuration saved')
      load()
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
    <div style={{ padding: 24 }}>
      <Title level={2}>Compensation Settings</Title>

      <Card loading={loading} style={{ maxWidth: 800 }}>
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="maxIncreasePctWithoutApproval"
            label="Max Increase % Without Approval"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber
              min={0}
              max={100}
              precision={2}
              addonAfter="%"
              disabled={!canWrite}
              style={{ width: 200 }}
            />
          </Form.Item>

          <Form.Item
            name="budgetExceededPolicy"
            label="Budget Exceeded Policy"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select options={BUDGET_POLICIES} disabled={!canWrite} style={{ width: 300 }} />
          </Form.Item>

          <Form.Item
            name="defaultCurrency"
            label="Default Currency"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'AZN', label: 'AZN' },
                { value: 'USD', label: 'USD' },
                { value: 'EUR', label: 'EUR' },
              ]}
              disabled={!canWrite}
              style={{ width: 200 }}
            />
          </Form.Item>

          {canWrite && (
            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
                Save Configuration
              </Button>
            </Form.Item>
          )}
        </Form>
      </Card>
    </div>
  )
}
