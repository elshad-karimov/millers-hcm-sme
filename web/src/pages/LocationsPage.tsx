// M141 — Location master admin (§11).
//
// Structured physical-site records replacing the free-text location
// VARCHAR that was scattered across org_unit / position / employee.
// Each location carries GPS, timezone, holiday-jurisdiction, shift-group
// default, and legal-entity linkage.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { locationApi, type LocationRequest, type LocationResponse, type LocationType } from '../api/location'
import { legalEntitiesApi } from '../api/legalEntities'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const LOCATION_TYPES: LocationType[] = [
  'HEAD_OFFICE', 'BRANCH', 'STORE', 'WAREHOUSE', 'FACTORY',
  'RESTAURANT', 'DISTRIBUTION_CENTER', 'REMOTE', 'CLIENT_SITE', 'PROJECT_SITE',
]

const TYPE_COLOR: Record<LocationType, string> = {
  HEAD_OFFICE: 'gold',
  BRANCH: 'blue',
  STORE: 'green',
  WAREHOUSE: 'orange',
  FACTORY: 'red',
  RESTAURANT: 'cyan',
  DISTRIBUTION_CENTER: 'purple',
  REMOTE: 'default',
  CLIENT_SITE: 'geekblue',
  PROJECT_SITE: 'magenta',
}

export function LocationsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [items, setItems] = useState<LocationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<LocationResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [legalEntityOptions, setLegalEntityOptions] = useState<{ value: string; label: string }[]>([])
  const [form] = Form.useForm<LocationRequest>()

  const load = () => {
    setLoading(true)
    locationApi.list(false)
      .then(setItems)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load locations'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    legalEntitiesApi.list(true)
      .then((le) => setLegalEntityOptions(le.map((e) => ({ value: e.id, label: `${e.code} — ${e.name}` }))))
      .catch(() => {/* non-critical */})
  }, []) // eslint-disable-line

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ active: true, country: 'AZ', holidayJurisdiction: 'AZ' })
    setOpen(true)
  }

  const startEdit = (loc: LocationResponse) => {
    setEditing(loc)
    form.setFieldsValue({
      code: loc.code,
      name: loc.name,
      locationType: loc.locationType,
      country: loc.country ?? undefined,
      city: loc.city ?? undefined,
      region: loc.region ?? undefined,
      address: loc.address ?? undefined,
      latitude: loc.latitude ?? undefined,
      longitude: loc.longitude ?? undefined,
      timezone: loc.timezone ?? undefined,
      holidayJurisdiction: loc.holidayJurisdiction ?? undefined,
      workCalendarCode: loc.workCalendarCode ?? undefined,
      legalEntityId: loc.legalEntityId ?? undefined,
      costCentreCode: loc.costCentreCode ?? undefined,
      phone: loc.phone ?? undefined,
      email: loc.email ?? undefined,
      active: loc.active,
      notes: loc.notes ?? undefined,
    })
    setOpen(true)
  }

  const onFinish = async (values: LocationRequest) => {
    setSaving(true)
    try {
      if (editing) {
        await locationApi.update(editing.id, values)
        message.success('Location updated')
      } else {
        await locationApi.create(values)
        message.success('Location created')
      }
      setOpen(false)
      load()
    } catch (e: unknown) {
      message.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (loc: LocationResponse) => {
    try {
      if (loc.active) {
        await locationApi.deactivate(loc.id)
        message.success('Location deactivated')
      } else {
        await locationApi.activate(loc.id)
        message.success('Location activated')
      }
      load()
    } catch (e: unknown) {
      message.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed')
    }
  }

  const columns: ColumnsType<LocationResponse> = [
    { title: 'Code', dataIndex: 'code', width: 100, render: (v) => <Text code>{v}</Text> },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'locationType',
      width: 150,
      render: (t: LocationType) => <Tag color={TYPE_COLOR[t]}>{t.replace('_', ' ')}</Tag>,
    },
    {
      title: 'Location',
      render: (_, r) => [r.city, r.country].filter(Boolean).join(', ') || '—',
    },
    { title: 'Timezone', dataIndex: 'timezone', render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: 'Actions',
      width: 160,
      render: (_, r) =>
        canWrite ? (
          <Space size="small">
            <Button size="small" onClick={() => startEdit(r)}>Edit</Button>
            <Popconfirm
              title={r.active ? 'Deactivate this location?' : 'Reactivate this location?'}
              onConfirm={() => toggle(r)}
              okText="Yes"
            >
              <Button size="small" danger={r.active}>{r.active ? 'Deactivate' : 'Activate'}</Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ]

  return (
    <Card
      title={<Title level={4} style={{ margin: 0 }}>Locations</Title>}
      extra={canWrite && <Button type="primary" onClick={startCreate}>+ New location</Button>}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
      ) : (
        <Table
          rowKey="id"
          dataSource={items}
          columns={columns}
          pagination={{ pageSize: 20 }}
          size="small"
        />
      )}

      <Modal
        open={open}
        title={editing ? 'Edit location' : 'New location'}
        onCancel={() => setOpen(false)}
        footer={null}
        width={680}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 60 }]}>
                <Input disabled={!!editing} placeholder="e.g. LOC-HQ" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="locationType" label="Type" rules={[{ required: true }]}>
                <Select options={LOCATION_TYPES.map((t) => ({ value: t, label: t.replace('_', ' ') }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>

          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="country" label="Country"
                tooltip="ISO 3166-1 alpha-2 (e.g. AZ, GB)"
                rules={[{ pattern: /^[A-Z]{2}$/, message: 'ISO alpha-2' }]}>
                <Input maxLength={2} placeholder="AZ" />
              </Form.Item>
            </Col>
            <Col span={9}>
              <Form.Item name="city" label="City" rules={[{ max: 120 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={9}>
              <Form.Item name="region" label="Region" rules={[{ max: 120 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="address" label="Address" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="latitude" label="Latitude"
                tooltip="WGS-84 decimal degrees (−90 to 90)">
                <InputNumber style={{ width: '100%' }} min={-90} max={90} step={0.000001} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="longitude" label="Longitude"
                tooltip="WGS-84 decimal degrees (−180 to 180)">
                <InputNumber style={{ width: '100%' }} min={-180} max={180} step={0.000001} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="timezone" label="Timezone"
                tooltip="IANA timezone ID (e.g. Asia/Baku)"
                rules={[{ max: 60 }]}>
                <Input placeholder="Asia/Baku" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="holidayJurisdiction" label="Holiday jurisdiction"
                tooltip="Matches the jurisdiction field in the holiday calendar (e.g. AZ, GB)"
                rules={[{ max: 8 }]}>
                <Input placeholder="AZ" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="costCentreCode" label="Cost centre" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="legalEntityId" label="Legal entity">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="— none —"
                  options={legalEntityOptions}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="phone" label="Phone" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label="Email" rules={[{ type: 'email' }, { max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={4000} showCount />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create location'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
