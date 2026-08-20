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
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { DeleteOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { CATEGORIES } from '../nav/modules'
import { refreshModuleSettings, useEnabledModules } from '../nav/moduleSettings'

/** Modules that can never be switched off (users would lose their workspace /
 *  admins would lose the settings screen that re-enables everything). */
const ALWAYS_ON_MODULES = new Set(['self-service', 'platform-admin'])
const DISABLED_MODULES_KEY = 'disabled_modules'

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
  const [disabledMods, setDisabledMods] = useState<Set<string>>(new Set())
  // Plan-level entitlement is read-only here — the plan is control-plane
  // (SYSTEM_ADMIN, /api/admin/tenants), not something an HR admin sets.
  const { plan, notInPlan, upgrades } = useEnabledModules()

  useEffect(() => {
    api
      .get<SettingDto[]>('/settings')
      .then((r) => {
        const data = r.data
        // Separate known from custom
        const knownKeys = KNOWN_SETTINGS.map((s) => s.key)
        const custom = data.filter(
          (s) => !knownKeys.includes(s.key) && s.key !== DISABLED_MODULES_KEY,
        )
        setCustomSettings(custom)

        // Module enablement lives in the disabled_modules CSV setting.
        const dm = data.find((s) => s.key === DISABLED_MODULES_KEY)?.value ?? ''
        setDisabledMods(new Set(dm.split(',').map((x) => x.trim()).filter(Boolean)))

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
      // Module enablement (CSV of disabled module keys)
      payload[DISABLED_MODULES_KEY] = Array.from(disabledMods).join(',')

      await api.put('/settings', payload)
      refreshModuleSettings() // update the live nav without a reload
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

      <Card
        title={
          <Space>
            <span>Modules</span>
            <Tag color="green">{plan}</Tag>
          </Space>
        }
        extra={
          <Typography.Text type="secondary">
            Switch off modules this tenant should not see
          </Typography.Text>
        }
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 10 }}>
          {CATEGORIES.map((c) => {
            const alwaysOn = ALWAYS_ON_MODULES.has(c.key)
            // Out of plan: shown locked rather than hidden, so an admin can see
            // what the product offers and what an upgrade would unlock. The
            // toggle is inert — the server enforces the plan regardless.
            const locked = notInPlan.has(c.key)
            const enabled = alwaysOn || (!locked && !disabledMods.has(c.key))
            return (
              <div
                key={c.key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '6px 10px',
                  borderRadius: 8,
                  background: 'rgba(0,0,0,0.02)',
                  opacity: locked ? 0.6 : 1,
                }}
              >
                <span>
                  {locked && <LockOutlined style={{ marginInlineEnd: 6 }} />}
                  {c.label}
                  {alwaysOn && <Typography.Text type="secondary"> · always on</Typography.Text>}
                  {locked && (
                    <Typography.Text type="secondary">
                      {' '}· available in {upgrades.get(c.key) ?? 'a higher plan'}
                    </Typography.Text>
                  )}
                </span>
                <Switch
                  checked={enabled}
                  disabled={alwaysOn || locked}
                  onChange={(on) =>
                    setDisabledMods((prev) => {
                      const next = new Set(prev)
                      if (on) next.delete(c.key)
                      else next.add(c.key)
                      return next
                    })
                  }
                />
              </div>
            )
          })}
        </div>
        {notInPlan.size > 0 && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            {notInPlan.size} module{notInPlan.size === 1 ? '' : 's'} are outside your{' '}
            {plan} plan. <Link to="/upgrade">See what an upgrade adds</Link>.
          </Typography.Paragraph>
        )}
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
