// M474 — Custom KPI dashboards (create layouts, view values).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { analyticsApi, kpiDefinitionsApi, type DashboardLayout, type KpiDefinition } from '../../api/analytics'

interface WidgetDef {
  kpiCode: string
  position: number
}

export function MyDashboardsPage() {
  const { message } = AntdApp.useApp()
  const [dashboards, setDashboards] = useState<DashboardLayout[]>([])
  const [kpis, setKpis] = useState<KpiDefinition[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [viewingId, setViewingId] = useState<string | null>(null)
  const [kpiValues, setKpiValues] = useState<Record<string, any>>({})
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([analyticsApi.listDashboards(), kpiDefinitionsApi.listAll()])
      .then(([dashboards, kpis]) => {
        setDashboards(dashboards)
        setKpis(kpis)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ shared: false, widgets: [] })
    setEditingId(null)
    setModalOpen(true)
  }

  const openEdit = (layout: DashboardLayout) => {
    const widgets: WidgetDef[] = JSON.parse(layout.widgets || '[]')
    form.setFieldsValue({ ...layout, widgets })
    setEditingId(layout.id!)
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const payload = { ...values, widgets: JSON.stringify(values.widgets || []) }
      if (editingId) {
        await analyticsApi.updateDashboard(editingId, payload)
        message.success('Dashboard updated')
      } else {
        await analyticsApi.createDashboard(payload)
        message.success('Dashboard created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: 'Delete dashboard?',
      onOk: async () => {
        try {
          await analyticsApi.deleteDashboard(id)
          message.success('Dashboard deleted')
          load()
        } catch (e: any) {
          message.error(e?.response?.data?.message ?? 'Delete failed')
        }
      },
    })
  }

  const viewDashboard = async (layout: DashboardLayout) => {
    const widgets: WidgetDef[] = JSON.parse(layout.widgets || '[]')
    const codes = widgets.map((w) => w.kpiCode)
    if (codes.length === 0) {
      message.warning('No widgets in this dashboard')
      return
    }
    try {
      const values = await analyticsApi.getKpiValues(codes)
      setKpiValues(values)
      setViewingId(layout.id!)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load KPI values')
    }
  }

  const columns: ColumnsType<DashboardLayout> = [
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Shared',
      dataIndex: 'shared',
      width: 100,
      render: (s: boolean) => (s ? 'Yes' : 'No'),
    },
    {
      title: 'Widgets',
      width: 100,
      render: (_, r) => {
        const widgets: WidgetDef[] = JSON.parse(r.widgets || '[]')
        return widgets.length
      },
    },
    {
      title: 'Actions',
      width: 200,
      render: (_, r) => (
        <Space>
          <a onClick={() => viewDashboard(r)}>View</a>
          <a onClick={() => openEdit(r)}>Edit</a>
          <a onClick={() => handleDelete(r.id!)} style={{ color: 'red' }}>
            Delete
          </a>
        </Space>
      ),
    },
  ]

  const currentDashboard = dashboards.find((d) => d.id === viewingId)
  const widgets: WidgetDef[] = currentDashboard ? JSON.parse(currentDashboard.widgets || '[]') : []

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="My Dashboards" extra={<Button type="primary" onClick={openCreate}>Create Dashboard</Button>}>
        <Table rowKey="id" columns={columns} dataSource={dashboards} loading={loading} />
      </Card>

      {viewingId && (
        <Card
          title={currentDashboard?.name}
          extra={<Button onClick={() => setViewingId(null)}>Close</Button>}
        >
          <Row gutter={[16, 16]}>
            {widgets.map((w, idx) => {
              const kpi = kpis.find((k) => k.code === w.kpiCode)
              const value = kpiValues[w.kpiCode]
              return (
                <Col key={idx} xs={24} sm={12} md={8} lg={6}>
                  <Card>
                    <Statistic
                      title={kpi?.name || w.kpiCode}
                      value={value?.value ?? '—'}
                      suffix={kpi?.unit}
                    />
                    {kpi?.targetValue != null && (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        Target: {kpi.targetValue} {kpi.unit}
                      </Typography.Text>
                    )}
                  </Card>
                </Col>
              )
            })}
          </Row>
        </Card>
      )}

      <Modal
        title={editingId ? 'Edit Dashboard' : 'Create Dashboard'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="My Dashboard" />
          </Form.Item>
          <Form.Item name="shared" label="Shared" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.List name="widgets">
            {(fields, { add, remove }) => (
              <>
                <Typography.Text strong>Widgets</Typography.Text>
                {fields.map((field) => (
                  <Row key={field.key} gutter={8} style={{ marginTop: 8 }}>
                    <Col span={18}>
                      <Form.Item {...field} name={[field.name, 'kpiCode']} noStyle>
                        <Select placeholder="Select KPI">
                          {kpis.map((kpi) => (
                            <Select.Option key={kpi.code} value={kpi.code}>
                              {kpi.name} ({kpi.code})
                            </Select.Option>
                          ))}
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={4}>
                      <Form.Item {...field} name={[field.name, 'position']} noStyle initialValue={field.key}>
                        <Input placeholder="Pos" type="number" />
                      </Form.Item>
                    </Col>
                    <Col span={2}>
                      <Button danger onClick={() => remove(field.name)}>
                        X
                      </Button>
                    </Col>
                  </Row>
                ))}
                <Button type="dashed" onClick={() => add()} style={{ marginTop: 8, width: '100%' }}>
                  + Add Widget
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </Space>
  )
}
