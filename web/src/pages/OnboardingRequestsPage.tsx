// M301 — Onboarding provisioning queue (Phase B.1).
// M310 — Dashboard summary cards per category + IT sub-breakdown by kind.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { prettyEnum } from '../api/checklists'
import type { AssetType } from '../api/assetsNotesRewards'
import {
  IT_PROVISIONING_KINDS,
  onboardingRequestsApi,
  REQUEST_CATEGORY_COLOR,
  REQUEST_STATUS_COLOR,
  type ItProvisioningKind,
  type ProvisioningQueueSummary,
  type ResourceRequest,
  type ResourceRequestCategory,
} from '../api/onboardingRequests'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const ASSET_TYPES: AssetType[] = [
  'LAPTOP', 'MOBILE_PHONE', 'SIM_CARD', 'VEHICLE', 'ACCESS_CARD', 'UNIFORM',
  'TOOLS', 'EQUIPMENT', 'LOCKER', 'FUEL_CARD', 'CREDIT_CARD', 'TABLET', 'HEADSET', 'OTHER',
]
const ASSET_TYPE_OPTIONS = ASSET_TYPES.map((v) => ({ value: v, label: prettyEnum(v) }))

const SUGGESTED_ASSET: Record<ResourceRequestCategory, AssetType> = {
  EQUIPMENT: 'LAPTOP', WORKSPACE: 'LOCKER', ACCESS_CARD: 'ACCESS_CARD',
  IT_ACCOUNT: 'OTHER', OTHER: 'OTHER',
}
const KIND_OPTIONS = IT_PROVISIONING_KINDS.map((v) => ({ value: v, label: prettyEnum(v) }))
const SUGGESTED_KIND: Partial<Record<ResourceRequestCategory, ItProvisioningKind>> = {
  IT_ACCOUNT: 'EMAIL_ACCOUNT', ACCESS_CARD: 'ACCESS_CARD',
}
const isItCategory = (c: ResourceRequestCategory) => c === 'IT_ACCOUNT' || c === 'ACCESS_CARD'

// Hex equivalents of Ant Design tag colors used in REQUEST_CATEGORY_COLOR.
const CATEGORY_COLOR_HEX: Record<ResourceRequestCategory, string> = {
  EQUIPMENT: '#2f54eb',
  WORKSPACE: '#fa8c16',
  IT_ACCOUNT: '#531dab',
  ACCESS_CARD: '#d4380d',
  OTHER: '#8c8c8c',
}

// All filter options in display order.
const CATEGORY_FILTERS: Array<{ label: string; value: 'ALL' | ResourceRequestCategory }> = [
  { label: 'All', value: 'ALL' },
  { label: 'Equipment', value: 'EQUIPMENT' },
  { label: 'Workspace', value: 'WORKSPACE' },
  { label: 'IT Account', value: 'IT_ACCOUNT' },
  { label: 'Access Card', value: 'ACCESS_CARD' },
  { label: 'Other', value: 'OTHER' },
]

export function OnboardingRequestsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [rows, setRows] = useState<ResourceRequest[]>([])
  const [summary, setSummary] = useState<ProvisioningQueueSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [cat, setCat] = useState<'ALL' | ResourceRequestCategory>('ALL')
  const [fulfilling, setFulfilling] = useState<ResourceRequest | null>(null)
  const [form] = Form.useForm<{
    createAsset: boolean
    assetType: AssetType
    assetName: string
    assetIdentifier?: string
    assignedAt: ReturnType<typeof dayjs>
    provisioningKind?: ItProvisioningKind
    provisionedRef?: string
    notes?: string
  }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([onboardingRequestsApi.open(), onboardingRequestsApi.summary()])
      .then(([r, s]) => { setRows(r); setSummary(s) })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const filtered = useMemo(
    () => (cat === 'ALL' ? rows : rows.filter((r) => r.category === cat)),
    [rows, cat],
  )

  const setStatus = async (r: ResourceRequest, status: 'IN_PROGRESS' | 'CANCELLED') => {
    try {
      await onboardingRequestsApi.updateStatus(r.id, status)
      message.success(`${r.requestNo} → ${prettyEnum(status)}`)
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    }
  }

  const openFulfill = (r: ResourceRequest) => {
    setFulfilling(r)
    const createAsset = r.category === 'EQUIPMENT' || r.category === 'WORKSPACE' || r.category === 'ACCESS_CARD'
    form.setFieldsValue({
      createAsset,
      assetType: SUGGESTED_ASSET[r.category],
      assetName: r.title,
      assetIdentifier: undefined,
      assignedAt: dayjs(),
      provisioningKind: r.provisioningKind ?? SUGGESTED_KIND[r.category],
      provisionedRef: undefined,
      notes: undefined,
    })
  }

  const submitFulfill = async () => {
    const v = await form.validateFields()
    const it = isItCategory(fulfilling!.category)
    setSaving(true)
    try {
      await onboardingRequestsApi.fulfill(fulfilling!.id, {
        createAsset: v.createAsset,
        assetType: v.createAsset ? v.assetType : undefined,
        assetName: v.createAsset ? v.assetName : undefined,
        assetIdentifier: v.createAsset ? v.assetIdentifier : undefined,
        assignedAt: v.assignedAt?.format('YYYY-MM-DD'),
        provisioningKind: it ? v.provisioningKind : undefined,
        provisionedRef: it ? v.provisionedRef : undefined,
        notes: v.notes,
      })
      message.success(`${fulfilling!.requestNo} fulfilled`)
      setFulfilling(null)
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    } finally { setSaving(false) }
  }

  const cols: ColumnsType<ResourceRequest> = [
    {
      title: 'Request',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.title}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.requestNo}</Text>
        </Space>
      ),
    },
    { title: 'New hire', dataIndex: 'employeeName', width: 170, render: (v) => v ?? '—' },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 150,
      render: (v: ResourceRequestCategory, r) => (
        <Space direction="vertical" size={2}>
          <Tag color={REQUEST_CATEGORY_COLOR[v]} style={{ marginInlineEnd: 0 }}>{prettyEnum(v)}</Tag>
          {r.provisioningKind && (
            <Text type="secondary" style={{ fontSize: 11 }}>{prettyEnum(r.provisioningKind)}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: ResourceRequest['status']) => <Tag color={REQUEST_STATUS_COLOR[v]}>{prettyEnum(v)}</Tag>,
    },
    {
      title: 'Requested',
      dataIndex: 'requestedAt',
      width: 120,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: '',
      width: 240,
      render: (_, r) => canWrite ? (
        <Space>
          {r.status === 'REQUESTED' && (
            <Button size="small" onClick={() => setStatus(r, 'IN_PROGRESS')}>Start</Button>
          )}
          <Button size="small" type="primary" onClick={() => openFulfill(r)}>Fulfil…</Button>
          <Popconfirm title="Cancel this request?" okText="Cancel" cancelText="Keep"
            onConfirm={() => setStatus(r, 'CANCELLED')}>
            <Button size="small" danger>Cancel</Button>
          </Popconfirm>
        </Space>
      ) : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ margin: 0 }}>Provisioning requests</Title>
        <Text type="secondary">
          Equipment, workspace &amp; IT requests auto-raised by onboarding tasks.
          Fulfilling an equipment request creates the asset handover record and ticks
          the new hire's checklist task.
        </Text>
      </div>

      {/* M310 — Summary dashboard cards */}
      {summary && (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Space wrap>
            {CATEGORY_FILTERS.map((opt) => {
              const count = opt.value === 'ALL'
                ? summary.totalPending
                : (summary.byCategory[opt.value as ResourceRequestCategory] ?? 0)
              const active = cat === opt.value
              return (
                <Card
                  key={opt.value}
                  size="small"
                  hoverable
                  onClick={() => setCat(opt.value)}
                  style={{
                    cursor: 'pointer',
                    minWidth: 110,
                    borderColor: active ? '#1677ff' : undefined,
                    boxShadow: active ? '0 0 0 2px rgba(22,119,255,0.15)' : undefined,
                  }}
                >
                  <Statistic
                    title={opt.value === 'ALL'
                      ? <Text type="secondary" style={{ fontSize: 12 }}>All open</Text>
                      : (
                        <Tag
                          color={REQUEST_CATEGORY_COLOR[opt.value as ResourceRequestCategory]}
                          style={{ marginInlineEnd: 0, fontSize: 11 }}
                        >
                          {opt.label}
                        </Tag>
                      )
                    }
                    value={count}
                    valueStyle={{
                      fontSize: 22,
                      color: opt.value === 'ALL'
                        ? undefined
                        : CATEGORY_COLOR_HEX[opt.value as ResourceRequestCategory],
                    }}
                  />
                  {opt.value === 'ALL' && summary.totalInProgress > 0 && (
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {summary.totalInProgress} in progress
                    </Text>
                  )}
                </Card>
              )
            })}
          </Space>

          {/* IT sub-breakdown by provisioning kind */}
          {Object.keys(summary.itByKind).length > 0 && (
            <Space wrap size="small">
              <Text type="secondary" style={{ fontSize: 12 }}>IT by kind:</Text>
              {(Object.entries(summary.itByKind) as Array<[ItProvisioningKind, number]>).map(
                ([kind, count]) => (
                  <Tag key={kind} color="purple">{prettyEnum(kind)}: {count}</Tag>
                ),
              )}
            </Space>
          )}
        </Space>
      )}

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={filtered}
          pagination={{ pageSize: 20 }}
          size="small"
          locale={{ emptyText: <Empty description="No open provisioning requests" /> }}
        />
      </Card>

      <Modal
        open={!!fulfilling}
        title={fulfilling ? `Fulfil ${fulfilling.requestNo} — ${fulfilling.title}` : ''}
        onCancel={() => setFulfilling(null)}
        onOk={submitFulfill}
        confirmLoading={saving}
        okText="Fulfil"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="createAsset" valuePropName="checked" extra="Records an asset handover against the new hire (M72/M124).">
            <Checkbox>Create asset handover record</Checkbox>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.createAsset !== c.createAsset}>
            {({ getFieldValue }) => getFieldValue('createAsset') && (
              <>
                <Form.Item name="assetType" label="Asset type" rules={[{ required: true }]}>
                  <Select options={ASSET_TYPE_OPTIONS} showSearch optionFilterProp="label" />
                </Form.Item>
                <Form.Item name="assetName" label="Asset name" rules={[{ required: true }]}>
                  <Input placeholder='e.g. "MacBook Pro 14"' />
                </Form.Item>
                <Form.Item name="assetIdentifier" label="Serial / tag (optional)">
                  <Input placeholder="Serial number or asset tag" />
                </Form.Item>
                <Form.Item name="assignedAt" label="Handover date" rules={[{ required: true }]}>
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </>
            )}
          </Form.Item>
          {fulfilling && isItCategory(fulfilling.category) && (
            <>
              <Form.Item name="provisioningKind" label="Provisioning kind">
                <Select allowClear options={KIND_OPTIONS} placeholder="—" />
              </Form.Item>
              <Form.Item
                name="provisionedRef"
                label="What was provisioned"
                extra="Account/email created, systems granted, ticket no — request-tracking only; no live account is created."
              >
                <Input placeholder="e.g. j.doe@company.com · JIRA-1234 · CRM + VPN" />
              </Form.Item>
            </>
          )}
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
