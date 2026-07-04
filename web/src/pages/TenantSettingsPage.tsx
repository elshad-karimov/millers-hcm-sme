import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Space,
  Spin,
  Switch,
  Table,
  Typography,
  App as AntdApp,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'

interface SettingDto {
  key: string
  value: string
}

interface KnownSetting {
  key: string
  label: string
  description?: string
  type: 'boolean' | 'text'
}

const KNOWN_SETTINGS: KnownSetting[] = [
  {
    key: 'manager_can_view_salary',
    label: 'Manager can view team salary',
    description: 'Allow managers to see their own team\'s salaries',
    type: 'boolean',
  },
]

export function TenantSettingsPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [customSettings, setCustomSettings] = useState<SettingDto[]>([])
  const [newKey, setNewKey] = useState('')
  const [newValue, setNewValue] = useState('')

  useEffect(() => {
    api
      .get<SettingDto[]>('/settings')
      .then((r) => {
        const data = r.data
        // Separate known from custom
        const knownKeys = KNOWN_SETTINGS.map((s) => s.key)
        const custom = data.filter((s) => !knownKeys.includes(s.key))
        setCustomSettings(custom)

        // Initialize form with known settings
        const initialValues: Record<string, boolean | string> = {}
        KNOWN_SETTINGS.forEach((known) => {
          const existing = data.find((s) => s.key === known.key)
          if (known.type === 'boolean') {
            initialValues[known.key] = existing?.value === 'true'
          } else {
            initialValues[known.key] = existing?.value ?? ''
          }
        })
        form.setFieldsValue(initialValues)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load settings'),
      )
      .finally(() => setLoading(false))
  }, [form, message])

  async function handleSave() {
    try {
      const values = await form.validateFields()
      setSaving(true)

      // Build settings map
      const payload: Record<string, string> = {}
      KNOWN_SETTINGS.forEach((known) => {
        if (known.type === 'boolean') {
          payload[known.key] = values[known.key] ? 'true' : 'false'
        } else {
          payload[known.key] = values[known.key] ?? ''
        }
      })
      // Add custom settings
      customSettings.forEach((s) => {
        payload[s.key] = s.value
      })

      await api.put('/settings', payload)
      message.success('Settings saved')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      message.error(axiosErr?.response?.data?.message ?? 'Failed to save settings')
    } finally {
      setSaving(false)
    }
  }

  function handleAddCustom() {
    if (!newKey.trim()) {
      message.warning('Please enter a key')
      return
    }
    if (customSettings.some((s) => s.key === newKey)) {
      message.warning('Key already exists')
      return
    }
    setCustomSettings([...customSettings, { key: newKey, value: newValue }])
    setNewKey('')
    setNewValue('')
  }

  function handleDeleteCustom(key: string) {
    setCustomSettings(customSettings.filter((s) => s.key !== key))
  }

  const customColumns: ColumnsType<SettingDto> = [
    { title: 'Key', dataIndex: 'key', width: '40%' },
    {
      title: 'Value',
      dataIndex: 'value',
      width: '50%',
      render: (v, record) => (
        <Input
          value={v}
          onChange={(e) => {
            const updated = customSettings.map((s) =>
              s.key === record.key ? { ...s, value: e.target.value } : s,
            )
            setCustomSettings(updated)
          }}
        />
      ),
    },
    {
      title: '',
      width: '10%',
      render: (_, record) => (
        <Button
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDeleteCustom(record.key)}
        />
      ),
    },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3}>Tenant Settings</Typography.Title>

      <Card title="Configuration">
        <Form form={form} layout="vertical">
          {KNOWN_SETTINGS.map((known) => (
            <Form.Item
              key={known.key}
              name={known.key}
              label={known.label}
              valuePropName={known.type === 'boolean' ? 'checked' : 'value'}
              extra={known.description}
            >
              {known.type === 'boolean' ? <Switch /> : <Input />}
            </Form.Item>
          ))}
        </Form>
      </Card>

      <Card title="Custom Settings">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Table
            rowKey="key"
            columns={customColumns}
            dataSource={customSettings}
            pagination={false}
            size="small"
          />
          <Space>
            <Input
              placeholder="Key"
              value={newKey}
              onChange={(e) => setNewKey(e.target.value)}
              style={{ width: 200 }}
            />
            <Input
              placeholder="Value"
              value={newValue}
              onChange={(e) => setNewValue(e.target.value)}
              style={{ width: 300 }}
            />
            <Button icon={<PlusOutlined />} onClick={handleAddCustom}>
              Add
            </Button>
          </Space>
        </Space>
      </Card>

      <Button type="primary" size="large" loading={saving} onClick={handleSave}>
        Save All Settings
      </Button>
    </Space>
  )
}
