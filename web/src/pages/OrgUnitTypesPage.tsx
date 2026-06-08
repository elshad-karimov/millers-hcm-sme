import { useEffect, useState } from 'react'
import {
  Badge,
  Button,
  Checkbox,
  ColorPicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  orgUnitTypeApi,
  type OrgUnitTypeConfigRequest,
  type OrgUnitTypeConfigResponse,
} from '../api/orgUnitType'

const { Title, Text } = Typography

export function OrgUnitTypesPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<OrgUnitTypeConfigRequest & { colorHex?: string }>()

  const [types, setTypes] = useState<OrgUnitTypeConfigResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editCode, setEditCode] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    orgUnitTypeApi
      .list()
      .then(setTypes)
      .catch(() => message.error('Failed to load org unit types'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditCode(null)
    form.resetFields()
    form.setFieldsValue({ canHaveChildren: true, rootLevel: false, active: true, sortOrder: 0 })
    setModalOpen(true)
  }

  const openEdit = (row: OrgUnitTypeConfigResponse) => {
    setEditCode(row.code)
    form.setFieldsValue({
      code: row.code,
      label: row.label,
      colorHex: row.color ?? undefined,
      sortOrder: row.sortOrder,
      canHaveChildren: row.canHaveChildren,
      rootLevel: row.rootLevel,
      allowedParentTypes: row.allowedParentTypes ?? undefined,
      active: row.active,
      notes: row.notes ?? undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    let values: OrgUnitTypeConfigRequest & { colorHex?: string }
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    const req: OrgUnitTypeConfigRequest = {
      ...values,
      color: values.colorHex ?? null,
    }
    setSaving(true)
    try {
      if (editCode) {
        await orgUnitTypeApi.update(editCode, req)
        message.success('Type updated')
      } else {
        await orgUnitTypeApi.create(req)
        message.success('Type created')
      }
      setModalOpen(false)
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

  const toggle = async (row: OrgUnitTypeConfigResponse) => {
    try {
      if (row.active) {
        await orgUnitTypeApi.deactivate(row.code)
      } else {
        await orgUnitTypeApi.activate(row.code)
      }
      load()
    } catch {
      message.error('Failed to update status')
    }
  }

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      render: (code: string, row: OrgUnitTypeConfigResponse) => (
        <Tag color={row.color ?? undefined}>{code}</Tag>
      ),
    },
    { title: 'Label', dataIndex: 'label' },
    {
      title: 'Rules',
      render: (_: unknown, row: OrgUnitTypeConfigResponse) => (
        <Space size={4}>
          {row.canHaveChildren && <Tag color="green">Can have children</Tag>}
          {row.rootLevel && <Tag color="blue">Root level</Tag>}
        </Space>
      ),
    },
    { title: 'Sort', dataIndex: 'sortOrder', width: 60 },
    {
      title: 'Status',
      dataIndex: 'active',
      render: (a: boolean) => (
        <Badge status={a ? 'success' : 'default'} text={a ? 'Active' : 'Inactive'} />
      ),
    },
    {
      title: 'Actions',
      render: (_: unknown, row: OrgUnitTypeConfigResponse) => (
        <Space>
          <Button size="small" onClick={() => openEdit(row)}>Edit</Button>
          <Button size="small" onClick={() => toggle(row)}>
            {row.active ? 'Deactivate' : 'Activate'}
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Org unit types</Title>
          <Text type="secondary">Configure the hierarchy levels available in the org chart.</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          New type
        </Button>
      </div>

      <Table
        rowKey="code"
        loading={loading}
        dataSource={types}
        columns={columns}
        size="small"
        pagination={false}
      />

      <Modal
        title={editCode ? `Edit type — ${editCode}` : 'New org unit type'}
        open={modalOpen}
        onOk={onSave}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        width={520}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="code"
            label="Code"
            rules={[
              { required: true },
              { pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: 'Upper-case letters, digits, underscore' },
            ]}
          >
            <Input
              placeholder="e.g. COST_CENTER"
              disabled={!!editCode}
              style={{ maxWidth: 240 }}
            />
          </Form.Item>
          <Form.Item name="label" label="Display label" rules={[{ required: true, max: 200 }]}>
            <Input placeholder="e.g. Cost Centre" style={{ maxWidth: 320 }} />
          </Form.Item>
          <Form.Item name="colorHex" label="Badge colour">
            <ColorPicker
              format="hex"
              onChange={(_, hex) => form.setFieldValue('colorHex', hex)}
            />
          </Form.Item>
          <Form.Item name="sortOrder" label="Sort order">
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="canHaveChildren" valuePropName="checked">
            <Checkbox>Can have child units</Checkbox>
          </Form.Item>
          <Form.Item name="rootLevel" valuePropName="checked">
            <Checkbox>Allowed at root level (no parent)</Checkbox>
          </Form.Item>
          <Form.Item
            name="allowedParentTypes"
            label="Allowed parent types (JSON array)"
            tooltip='E.g. ["BRANCH","DIVISION"] — leave blank for any'
          >
            <Input.TextArea
              rows={2}
              placeholder='["BRANCH","DIVISION"] or leave blank'
            />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
