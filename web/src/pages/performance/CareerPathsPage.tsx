// HCM_16 M416 — Career paths: progression routes with steps, requirements.
// Transparent to all employees (read); HR manages them.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { api } from '../../api/client'

const { Title, Text } = Typography

interface CareerPath {
  id: string
  code: string
  name: string
  jobFamily?: string | null
  description?: string | null
  active: boolean
  steps: CareerPathStep[]
}

interface CareerPathStep {
  id: string
  stepOrder: number
  fromPositionId?: string | null
  toPositionId: string
  requiredSkills?: string | null
  requiredCertifications?: string | null
  requiredExperienceYears?: number | null
  requiredCourses?: string | null
  typicalTenureMonths?: number | null
}

interface Position {
  id: string
  code: string
  title: string
}

export function CareerPathsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [paths, setPaths] = useState<CareerPath[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form] = Form.useForm()
  const [positions, setPositions] = useState<Position[]>([])
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    api
      .get<CareerPath[]>('/staffing/career-paths')
      .then((r) => setPaths(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load paths'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // Load positions
    api
      .get<{ content: Position[] }>('/positions', { params: { size: 500 } })
      .then((r) => setPositions(r.data.content))
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setEditingId(null)
    setDrawerOpen(true)
  }

  const openEdit = (path: CareerPath) => {
    setEditingId(path.id)
    form.setFieldsValue({
      code: path.code,
      name: path.name,
      jobFamily: path.jobFamily,
      description: path.description,
      active: path.active,
      steps: path.steps.map((s) => ({
        fromPositionId: s.fromPositionId,
        toPositionId: s.toPositionId,
        requiredSkills: s.requiredSkills,
        requiredCertifications: s.requiredCertifications,
        requiredExperienceYears: s.requiredExperienceYears,
        requiredCourses: s.requiredCourses,
        typicalTenureMonths: s.typicalTenureMonths,
      })),
    })
    setDrawerOpen(true)
  }

  const submitPath = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      if (editingId) {
        await api.put(`/staffing/career-paths/${editingId}`, v)
        message.success('Updated')
      } else {
        await api.post('/staffing/career-paths', v)
        message.success('Created')
      }
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const cols: ColumnsType<CareerPath> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Job Family', dataIndex: 'jobFamily', render: (v) => v ?? '—' },
    { title: 'Steps', dataIndex: 'steps', render: (s: CareerPathStep[]) => s.length },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: '',
      width: 100,
      render: (_, r) => (
        <Button size="small" onClick={() => openEdit(r)}>
          Edit
        </Button>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3}>Career Paths</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Create Path
        </Button>
      </Space>

      {loading ? (
        <Spin />
      ) : (
        <Card>
          <Table
            rowKey="id"
            columns={cols}
            dataSource={paths}
            pagination={false}
            locale={{ emptyText: <Empty description="No career paths yet" /> }}
          />
        </Card>
      )}

      <Drawer
        open={drawerOpen}
        title={editingId ? 'Edit Career Path' : 'Create Career Path'}
        width={700}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            <Button type="primary" onClick={submitPath} loading={saving}>
              Save
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="Code" rules={[{ required: true }]}>
            <Input disabled={!!editingId} placeholder="ANALYST_TRACK" />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="Analyst career track" />
          </Form.Item>
          <Form.Item name="jobFamily" label="Job Family">
            <Input placeholder="Finance" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="Progression for finance analysts" />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>

          <Text strong>Steps:</Text>
          <Form.List name="steps">
            {(fields, { add, remove }) => (
              <List
                dataSource={fields}
                renderItem={(field) => (
                  <Card
                    key={field.key}
                    size="small"
                    style={{ marginTop: 8 }}
                    extra={<DeleteOutlined onClick={() => remove(field.name)} />}
                  >
                    <Form.Item
                      {...field}
                      name={[field.name, 'fromPositionId']}
                      label="From Position"
                    >
                      <Select
                        allowClear
                        showSearch
                        placeholder="Entry step (leave empty)"
                        optionFilterProp="label"
                        options={positions.map((p) => ({
                          value: p.id,
                          label: `${p.code} — ${p.title}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'toPositionId']}
                      label="To Position"
                      rules={[{ required: true }]}
                    >
                      <Select
                        showSearch
                        placeholder="Target position"
                        optionFilterProp="label"
                        options={positions.map((p) => ({
                          value: p.id,
                          label: `${p.code} — ${p.title}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'requiredSkills']} label="Skills">
                      <Input placeholder="Excel, SQL, PowerBI" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'requiredCertifications']}
                      label="Certifications"
                    >
                      <Input placeholder="CFA Level I" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'requiredExperienceYears']}
                      label="Experience (years)"
                    >
                      <InputNumber min={0} placeholder="2" style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'requiredCourses']} label="Courses">
                      <Input placeholder="Finance 101, Advanced Excel" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'typicalTenureMonths']}
                      label="Tenure (months)"
                    >
                      <InputNumber min={0} placeholder="18" style={{ width: '100%' }} />
                    </Form.Item>
                  </Card>
                )}
              >
                <Button
                  type="dashed"
                  onClick={() => add()}
                  block
                  icon={<PlusOutlined />}
                  style={{ marginTop: 8 }}
                >
                  Add Step
                </Button>
              </List>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </Space>
  )
}
