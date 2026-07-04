// HCM_20 M428 — Budget control settings page.

import { useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Form, InputNumber, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'

interface BudgetControlRule {
  id: string
  triggerPoint: 'SALARY_CHANGE' | 'NEW_HIRE' | 'OVERTIME' | 'TRAINING'
  action: 'WARN' | 'BLOCK'
  thresholdPct: number
  active: boolean
}

const TRIGGER_OPTIONS = [
  { value: 'SALARY_CHANGE', label: 'Salary Change' },
  { value: 'NEW_HIRE', label: 'New Hire' },
  { value: 'OVERTIME', label: 'Overtime' },
  { value: 'TRAINING', label: 'Training' },
]

const ACTION_OPTIONS = [
  { value: 'WARN', label: 'Warn' },
  { value: 'BLOCK', label: 'Block' },
]

export function BudgetSettingsPage() {
  const { message } = AntdApp.useApp()
  const [rules, setRules] = useState<BudgetControlRule[]>([])
  const [loading, setLoading] = useState(true)
  const [form] = Form.useForm()

  const refresh = () => {
    setLoading(true)
    api
      .get<BudgetControlRule[]>('/budgets/control-rules')
      .then((r) => setRules(r.data))
      .catch((err) => message.error(err?.response?.data?.message ?? 'Could not load rules'))
      .finally(() => setLoading(false))
  }
  useEffect(refresh, [])

  const onSave = async () => {
    const v = await form.validateFields()
    try {
      await api.post('/budgets/control-rules', v)
      message.success('Rule saved')
      form.resetFields()
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save')
    }
  }

  const columns: ColumnsType<BudgetControlRule> = [
    {
      title: 'Trigger',
      dataIndex: 'triggerPoint',
      render: (v: string) => <Tag>{v.replace('_', ' ')}</Tag>,
    },
    {
      title: 'Action',
      dataIndex: 'action',
      render: (v: string) => <Tag color={v === 'BLOCK' ? 'red' : 'orange'}>{v}</Tag>,
    },
    {
      title: 'Threshold %',
      dataIndex: 'thresholdPct',
      align: 'right' as const,
      render: (v: number) => `${Number(v).toFixed(2)}%`,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="Budget Control Rules" size="small">
        <Table
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Card title="Add / Update Rule" size="small">
        <Form form={form} layout="inline">
          <Form.Item name="triggerPoint" label="Trigger" rules={[{ required: true }]}>
            <Select options={TRIGGER_OPTIONS} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="action" label="Action" rules={[{ required: true }]}>
            <Select options={ACTION_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="thresholdPct" label="Threshold %" rules={[{ required: true }]}>
            <InputNumber min={0} max={200} step={5} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onSave}>
              Save
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Space>
  )
}
