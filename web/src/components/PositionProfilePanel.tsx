// M248 — Position Profile Panel.
//
// Reusable panel rendered on PositionFormPage. Shows what each
// position requires for every occupant — allowances, required docs,
// mandatory training, equipment, access roles, checklist items,
// approval limits — grouped by type with per-section CRUD.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Collapse,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  PROFILE_ITEM_TYPE_COLOR,
  PROFILE_ITEM_TYPE_ICON,
  PROFILE_ITEM_TYPE_LABEL,
  positionProfileApi,
  profileItemHasAmount,
  type PositionProfileItem,
  type ProfileItemRequest,
  type ProfileItemType,
} from '../api/positionProfile'
import { positionsApi, type Position } from '../api/positions'

const TYPE_OPTIONS = (
  [
    'ALLOWANCE',
    'REQUIRED_DOCUMENT',
    'TRAINING',
    'EQUIPMENT',
    'ACCESS_ROLE',
    'CHECKLIST_ITEM',
    'APPROVAL_LIMIT',
  ] as ProfileItemType[]
).map((t) => ({
  value: t,
  label: `${PROFILE_ITEM_TYPE_ICON[t]} ${PROFILE_ITEM_TYPE_LABEL[t]}`,
}))

interface Props {
  positionId: string
  canEdit?: boolean
}

export function PositionProfilePanel({ positionId, canEdit = true }: Props) {
  const { message } = AntdApp.useApp()
  const [items, setItems] = useState<PositionProfileItem[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<PositionProfileItem | 'new' | null>(null)
  const [presetType, setPresetType] = useState<ProfileItemType | undefined>()
  const [cloneOpen, setCloneOpen] = useState(false)
  const [otherPositions, setOtherPositions] = useState<Position[]>([])

  type FormValues = Omit<ProfileItemRequest, 'mandatory'> & { mandatory: boolean }
  const [form] = Form.useForm<FormValues>()
  const [cloneForm] = Form.useForm<{ sourcePositionId: string }>()

  const refresh = () => {
    setLoading(true)
    positionProfileApi.list(positionId)
      .then(setItems)
      .catch((err) =>
        message.warning(err?.response?.data?.message ?? 'Could not load profile'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [positionId])

  // Lazy-load other positions only when the clone modal opens — saves
  // 500-row fetches on every page view.
  const ensureOtherPositions = () => {
    if (otherPositions.length > 0) return
    positionsApi.list({ size: 500 })
      .then((r) => setOtherPositions(r.content.filter((p) => p.id !== positionId)))
      .catch(() => setOtherPositions([]))
  }

  const open = (it: PositionProfileItem | 'new', t?: ProfileItemType) => {
    if (it === 'new') {
      setPresetType(t)
      form.resetFields()
      form.setFieldsValue({
        itemType: t ?? 'ALLOWANCE',
        mandatory: true,
        sortOrder: 0,
        currency: 'AZN',
      })
    } else {
      setPresetType(it.itemType)
      form.setFieldsValue({
        itemType: it.itemType,
        label: it.label,
        valueAmount: it.valueAmount ?? undefined,
        currency: it.currency ?? undefined,
        mandatory: it.mandatory,
        referenceCode: it.referenceCode ?? undefined,
        notes: it.notes ?? undefined,
        sortOrder: it.sortOrder,
      })
    }
    setEditing(it)
  }

  const onOk = async () => {
    const v = await form.validateFields()
    const body: ProfileItemRequest = {
      itemType: v.itemType,
      label: v.label,
      valueAmount: v.valueAmount,
      currency: v.currency,
      mandatory: v.mandatory,
      referenceCode: v.referenceCode,
      notes: v.notes,
      sortOrder: v.sortOrder ?? 0,
    }
    try {
      if (editing === 'new') {
        await positionProfileApi.create(positionId, body)
        message.success('Item added')
      } else if (editing) {
        await positionProfileApi.update(positionId, editing.id, body)
        message.success('Item updated')
      }
      setEditing(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const onDelete = async (it: PositionProfileItem) => {
    try {
      await positionProfileApi.remove(positionId, it.id)
      message.success('Item removed')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  const onClone = async () => {
    const v = await cloneForm.validateFields()
    try {
      const created = await positionProfileApi.cloneFrom(positionId, v.sourcePositionId)
      message.success(`Cloned ${created.length} item(s)`)
      setCloneOpen(false)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Clone failed')
    }
  }

  // ── Render ───────────────────────────────────────────────────────

  const grouped = useMemo(() => {
    const by = new Map<ProfileItemType, PositionProfileItem[]>()
    for (const t of [
      'ALLOWANCE',
      'REQUIRED_DOCUMENT',
      'TRAINING',
      'EQUIPMENT',
      'ACCESS_ROLE',
      'CHECKLIST_ITEM',
      'APPROVAL_LIMIT',
    ] as ProfileItemType[]) {
      by.set(t, [])
    }
    for (const it of items) {
      const arr = by.get(it.itemType)
      if (arr) arr.push(it)
    }
    return by
  }, [items])

  const cols = (type: ProfileItemType): ColumnsType<PositionProfileItem> => [
    { title: 'Label', dataIndex: 'label' },
    ...(profileItemHasAmount(type)
      ? [
          {
            title: 'Amount',
            align: 'right' as const,
            width: 150,
            render: (_: unknown, r: PositionProfileItem) =>
              r.valueAmount != null
                ? `${r.currency ?? ''} ${Number(r.valueAmount).toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}`
                : '—',
          },
        ]
      : []),
    {
      title: 'Reference',
      dataIndex: 'referenceCode',
      width: 160,
      render: (v: string) =>
        v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'Mandatory',
      dataIndex: 'mandatory',
      width: 110,
      align: 'center' as const,
      render: (m: boolean) =>
        m ? <Tag color="red">Required</Tag> : <Tag>Optional</Tag>,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      render: (v: string) =>
        v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    ...(canEdit
      ? [
          {
            title: '',
            width: 130,
            render: (_: unknown, r: PositionProfileItem) => (
              <Space size={4}>
                <Button size="small" onClick={() => open(r)}>
                  Edit
                </Button>
                <Popconfirm title="Remove this item?" onConfirm={() => onDelete(r)}>
                  <Button size="small" danger>
                    Delete
                  </Button>
                </Popconfirm>
              </Space>
            ),
          } as ColumnsType<PositionProfileItem>[number],
        ]
      : []),
  ]

  const totalMandatory = items.filter((i) => i.mandatory).length
  const totalAllowanceAmount = items
    .filter((i) => i.itemType === 'ALLOWANCE' && i.mandatory)
    .reduce((sum, i) => sum + Number(i.valueAmount ?? 0), 0)

  return (
    <Card
      title={
        <Space wrap>
          <span>Profile</span>
          <Tag color="default">{items.length} items</Tag>
          <Tag color="red">{totalMandatory} required</Tag>
          {totalAllowanceAmount > 0 && (
            <Tag color="green">
              💰{' '}
              {totalAllowanceAmount.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}
              /mo
            </Tag>
          )}
        </Space>
      }
      extra={
        canEdit && (
          <Space>
            <Button
              onClick={() => {
                ensureOtherPositions()
                cloneForm.resetFields()
                setCloneOpen(true)
              }}
            >
              ↻ Clone from another position
            </Button>
            <Button type="primary" onClick={() => open('new')}>
              + Add item
            </Button>
          </Space>
        )
      }
      loading={loading}
    >
      {items.length === 0 ? (
        <Empty
          description="No profile items defined yet. Use + Add item, or clone from another position."
        />
      ) : (
        <Collapse
          ghost
          defaultActiveKey={(['ALLOWANCE', 'REQUIRED_DOCUMENT', 'TRAINING', 'EQUIPMENT', 'ACCESS_ROLE', 'CHECKLIST_ITEM', 'APPROVAL_LIMIT'] as ProfileItemType[])
            .filter((t) => (grouped.get(t) ?? []).length > 0)}
          items={(
            [
              'ALLOWANCE',
              'APPROVAL_LIMIT',
              'REQUIRED_DOCUMENT',
              'TRAINING',
              'EQUIPMENT',
              'ACCESS_ROLE',
              'CHECKLIST_ITEM',
            ] as ProfileItemType[]
          )
            .filter((t) => (grouped.get(t) ?? []).length > 0)
            .map((t) => ({
              key: t,
              label: (
                <Space>
                  <span>{PROFILE_ITEM_TYPE_ICON[t]}</span>
                  <Tag color={PROFILE_ITEM_TYPE_COLOR[t]}>{PROFILE_ITEM_TYPE_LABEL[t]}</Tag>
                  <Typography.Text type="secondary">
                    {(grouped.get(t) ?? []).length} item(s)
                  </Typography.Text>
                </Space>
              ),
              extra: canEdit ? (
                <Button
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation()
                    open('new', t)
                  }}
                >
                  + Add {PROFILE_ITEM_TYPE_LABEL[t].toLowerCase()}
                </Button>
              ) : null,
              children: (
                <Table
                  size="small"
                  rowKey="id"
                  columns={cols(t)}
                  dataSource={grouped.get(t) ?? []}
                  pagination={false}
                />
              ),
            }))}
        />
      )}

      {/* ── Add / edit modal ── */}
      <Modal
        title={editing === 'new' ? 'Add profile item' : 'Edit profile item'}
        open={!!editing}
        onOk={onOk}
        onCancel={() => setEditing(null)}
        okText="Save"
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="itemType" label="Type" rules={[{ required: true }]}>
            <Select
              options={TYPE_OPTIONS}
              onChange={(v) => setPresetType(v)}
              disabled={editing !== 'new'}
            />
          </Form.Item>
          <Form.Item name="label" label="Label" rules={[{ required: true, max: 200 }]}>
            <Input placeholder="e.g. Fuel allowance / Driver licence / Forklift cert" />
          </Form.Item>
          {profileItemHasAmount(presetType ?? 'ALLOWANCE') && (
            <Space size="small">
              <Form.Item name="valueAmount" label="Amount">
                <InputNumber min={0} step={50} style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="currency" label="Currency">
                <Select
                  style={{ width: 90 }}
                  options={['AZN', 'USD', 'EUR', 'TRY'].map((c) => ({ value: c, label: c }))}
                />
              </Form.Item>
            </Space>
          )}
          <Form.Item name="referenceCode" label="Reference code (optional)" tooltip="Used by Phase F.2 to wire into native modules (allowance code, course code, etc.)">
            <Input placeholder="e.g. ALLOW-FUEL or COURSE-FORKLIFT" maxLength={120} />
          </Form.Item>
          <Form.Item name="mandatory" label="Mandatory" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sortOrder" label="Sort order" tooltip="Lower numbers appear first">
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Clone-from modal ── */}
      <Modal
        title="Clone profile from another position"
        open={cloneOpen}
        onOk={onClone}
        onCancel={() => setCloneOpen(false)}
        okText="Clone"
        destroyOnClose
      >
        <Form form={cloneForm} layout="vertical" preserve={false}>
          <Form.Item
            name="sourcePositionId"
            label="Source position"
            rules={[{ required: true }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={otherPositions.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.title}`,
              }))}
            />
          </Form.Item>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
            Existing items on this position are kept. The cloned items are added on top.
          </Typography.Paragraph>
        </Form>
      </Modal>
    </Card>
  )
}
