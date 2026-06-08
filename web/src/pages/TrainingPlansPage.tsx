// M157 — Department / Annual / Compliance Training Plans (§8.14.2).
// HR creates plans, adds courses, activates them, then runs enroll-all to
// batch-enroll all matching employees in one click.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Badge,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  learningApi,
  trainingPlanApi,
  type Course,
  type TrainingPlanItemResponse,
  type TrainingPlanRequest,
  type TrainingPlanResponse,
  type TrainingPlanStatus,
  type TrainingPlanType,
} from '../api/learning'
import { orgApi, type OrgUnitResponse } from '../api/org'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const PLAN_TYPE_COLOR: Record<TrainingPlanType, string> = {
  DEPARTMENT: 'blue',
  ANNUAL: 'green',
  COMPLIANCE: 'red',
  CAREER_PATH: 'purple',
}

const STATUS_COLOR: Record<TrainingPlanStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'processing',
  COMPLETED: 'success',
  ARCHIVED: 'default',
}

export function TrainingPlansPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [plans, setPlans] = useState<TrainingPlanResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [orgUnits, setOrgUnits] = useState<OrgUnitResponse[]>([])
  const [courses, setCourses] = useState<Course[]>([])

  // Create/edit modal
  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [formLoading, setFormLoading] = useState(false)
  const [form] = Form.useForm<TrainingPlanRequest>()

  // Detail drawer
  const [detail, setDetail] = useState<TrainingPlanResponse | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [addItemOpen, setAddItemOpen] = useState(false)
  const [itemForm] = Form.useForm<{ courseId: string; dueDate?: string; notes?: string; sortOrder?: number }>()

  const load = () => {
    setLoading(true)
    Promise.all([
      trainingPlanApi.list(),
      orgApi.active().then((v) => (v ? orgApi.units(v.id) : [])),
      learningApi.courses({ page: 0, size: 500, status: 'PUBLISHED' }),
    ])
      .then(([p, units, coursePage]) => {
        setPlans(p)
        setOrgUnits(units)
        setCourses(coursePage.content)
      })
      .catch(() => message.error('Failed to load training plans'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const openCreate = () => {
    setEditingId(null)
    form.resetFields()
    setFormOpen(true)
  }

  const openEdit = (p: TrainingPlanResponse) => {
    setEditingId(p.id)
    form.setFieldsValue({
      name: p.name,
      description: p.description ?? undefined,
      planType: p.planType,
      orgUnitId: p.orgUnitId ?? undefined,
      fiscalYear: p.fiscalYear ?? undefined,
      deadline: p.deadline ?? undefined,
    })
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setFormLoading(true)
    try {
      if (editingId) {
        await trainingPlanApi.update(editingId, values)
        message.success('Training plan updated.')
      } else {
        await trainingPlanApi.create(values)
        message.success('Training plan created.')
      }
      setFormOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setFormLoading(false)
    }
  }

  const handleAction = async (
    action: 'activate' | 'complete' | 'archive' | 'enrollAll',
    id: string,
  ) => {
    try {
      if (action === 'enrollAll') {
        const result = await trainingPlanApi.enrollAll(id)
        message.success(`Enrolled ${result.enrolled} employees (${result.skipped} already enrolled).`)
      } else {
        await trainingPlanApi[action](id)
        message.success('Status updated.')
      }
      load()
      if (detail?.id === id) {
        const updated = await trainingPlanApi.get(id)
        setDetail(updated)
      }
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Action failed',
      )
    }
  }

  const openDetail = async (p: TrainingPlanResponse) => {
    const full = await trainingPlanApi.get(p.id)
    setDetail(full)
    setDrawerOpen(true)
  }

  const handleAddItem = async () => {
    if (!detail) return
    const values = await itemForm.validateFields()
    try {
      const updated = await trainingPlanApi.addItem(detail.id, {
        courseId: values.courseId,
        dueDate: values.dueDate,
        notes: values.notes,
        sortOrder: values.sortOrder ?? 0,
      })
      setDetail(updated)
      message.success('Course added.')
      setAddItemOpen(false)
      itemForm.resetFields()
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to add course',
      )
    }
  }

  const handleRemoveItem = async (item: TrainingPlanItemResponse) => {
    if (!detail) return
    try {
      await trainingPlanApi.removeItem(detail.id, item.id)
      const updated = await trainingPlanApi.get(detail.id)
      setDetail(updated)
      load()
    } catch (e) {
      message.error('Failed to remove course.')
    }
  }

  const cols: ColumnsType<TrainingPlanResponse> = [
    {
      title: 'Plan',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Button type="link" style={{ padding: 0 }} onClick={() => openDetail(r)}>
            {r.planNo}
          </Button>
          <Text>{r.name}</Text>
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'planType',
      width: 130,
      render: (v: TrainingPlanType) => <Tag color={PLAN_TYPE_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: TrainingPlanStatus) => <Badge status={STATUS_COLOR[v] as 'default'} text={v} />,
    },
    { title: 'Year', dataIndex: 'fiscalYear', width: 80, render: (v) => v ?? '—' },
    {
      title: 'Deadline',
      dataIndex: 'deadline',
      width: 120,
      render: (v: string) => v ? dayjs(v).format('DD MMM YYYY') : '—',
    },
    {
      title: 'Courses',
      width: 90,
      render: (_, r) => r.items.length,
    },
    {
      title: 'Enrolled',
      dataIndex: 'enrolledCount',
      width: 90,
      align: 'right',
    },
    ...(canWrite
      ? [
          {
            title: '',
            width: 200,
            render: (_: unknown, r: TrainingPlanResponse) => (
              <Space size={4}>
                {r.status === 'DRAFT' && (
                  <>
                    <Button size="small" onClick={() => openEdit(r)}>Edit</Button>
                    <Popconfirm title="Activate this plan?" onConfirm={() => handleAction('activate', r.id)} okText="Activate">
                      <Button size="small" type="primary">Activate</Button>
                    </Popconfirm>
                  </>
                )}
                {r.status === 'ACTIVE' && (
                  <>
                    <Button size="small" type="primary" onClick={() => handleAction('enrollAll', r.id)}>
                      Enroll all
                    </Button>
                    <Popconfirm title="Mark as completed?" onConfirm={() => handleAction('complete', r.id)} okText="Complete">
                      <Button size="small">Complete</Button>
                    </Popconfirm>
                  </>
                )}
                {(r.status === 'COMPLETED' || r.status === 'DRAFT') && (
                  <Popconfirm title="Archive this plan?" onConfirm={() => handleAction('archive', r.id)} okText="Archive">
                    <Button size="small" danger>Archive</Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          } as ColumnsType<TrainingPlanResponse>[number],
        ]
      : []),
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Training Plans</Title>
        {canWrite && (
          <Button type="primary" onClick={openCreate}>+ New plan</Button>
        )}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={plans}
          size="small"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Create / edit modal */}
      <Drawer
        title={editingId ? 'Edit training plan' : 'New training plan'}
        open={formOpen}
        onClose={() => setFormOpen(false)}
        width={520}
        footer={
          <Space>
            <Button onClick={() => setFormOpen(false)}>Cancel</Button>
            <Button type="primary" loading={formLoading} onClick={handleSave}>Save</Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={300} />
          </Form.Item>
          <Form.Item name="planType" label="Type" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="DEPARTMENT">Department</Select.Option>
              <Select.Option value="ANNUAL">Annual</Select.Option>
              <Select.Option value="COMPLIANCE">Compliance</Select.Option>
              <Select.Option value="CAREER_PATH">Career Path</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="orgUnitId" label="Department / Org unit">
            <Select allowClear showSearch
              filterOption={(v, o) => (o?.label as string ?? '').toLowerCase().includes(v.toLowerCase())}
              options={orgUnits.map((u) => ({ value: u.id, label: u.name }))}
              placeholder="All active employees (if blank)"
            />
          </Form.Item>
          <Form.Item name="fiscalYear" label="Fiscal year">
            <InputNumber style={{ width: '100%' }} min={2020} max={2040} placeholder="e.g. 2026" />
          </Form.Item>
          <Form.Item name="deadline" label="Deadline">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      {/* Detail drawer */}
      <Drawer
        title={detail ? `${detail.planNo} — ${detail.name}` : ''}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setAddItemOpen(false) }}
        width={620}
      >
        {detail && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="Type">
                <Tag color={PLAN_TYPE_COLOR[detail.planType]}>{detail.planType.replace(/_/g, ' ')}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Badge status={STATUS_COLOR[detail.status] as 'default'} text={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="Fiscal year">{detail.fiscalYear ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Deadline">
                {detail.deadline ? dayjs(detail.deadline).format('DD MMM YYYY') : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Enrolled">{detail.enrolledCount}</Descriptions.Item>
              <Descriptions.Item label="Completed">{detail.completedCount}</Descriptions.Item>
            </Descriptions>

            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>Courses ({detail.items.length})</Text>
              {canWrite && detail.status === 'DRAFT' && (
                <Button size="small" type="primary" onClick={() => { setAddItemOpen(true); itemForm.resetFields() }}>
                  Add course
                </Button>
              )}
            </Space>

            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={detail.items}
              columns={[
                {
                  title: 'Course',
                  render: (_, item: TrainingPlanItemResponse) => (
                    <Space direction="vertical" size={0}>
                      <Text strong>{item.courseTitle ?? item.courseId}</Text>
                      {item.courseCode && <Text type="secondary" style={{ fontSize: 11 }}>{item.courseCode}</Text>}
                    </Space>
                  ),
                },
                {
                  title: 'Due',
                  dataIndex: 'dueDate',
                  width: 110,
                  render: (v: string) => v ? dayjs(v).format('DD MMM YYYY') : '—',
                },
                ...(canWrite && detail.status === 'DRAFT'
                  ? [
                      {
                        title: '',
                        width: 60,
                        render: (_: unknown, item: TrainingPlanItemResponse) => (
                          <Popconfirm title="Remove?" onConfirm={() => handleRemoveItem(item)} okText="Remove">
                            <Button size="small" danger>✕</Button>
                          </Popconfirm>
                        ),
                      } as ColumnsType<TrainingPlanItemResponse>[number],
                    ]
                  : []),
              ]}
            />

            {addItemOpen && (
              <Card size="small" title="Add course to plan">
                <Form form={itemForm} layout="vertical">
                  <Form.Item name="courseId" label="Course" rules={[{ required: true }]}>
                    <Select showSearch
                      filterOption={(v, o) => (o?.label as string ?? '').toLowerCase().includes(v.toLowerCase())}
                      options={courses
                        .filter((c) => !detail.items.some((i) => i.courseId === c.id))
                        .map((c) => ({ value: c.id, label: `${c.code} — ${c.title}` }))}
                    />
                  </Form.Item>
                  <Form.Item name="dueDate" label="Due date">
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="notes" label="Notes">
                    <Input.TextArea rows={2} maxLength={1000} />
                  </Form.Item>
                  <Space>
                    <Button onClick={() => setAddItemOpen(false)}>Cancel</Button>
                    <Button type="primary" onClick={handleAddItem}>Add</Button>
                  </Space>
                </Form>
              </Card>
            )}

            {canWrite && detail.status === 'ACTIVE' && (
              <Popconfirm
                title="Enroll all matching employees in all courses of this plan?"
                description="Already-enrolled employees will be skipped. This action is audited."
                onConfirm={() => handleAction('enrollAll', detail.id)}
                okText="Enroll all"
              >
                <Button type="primary" block>Enroll all employees now</Button>
              </Popconfirm>
            )}
          </Space>
        )}
      </Drawer>
    </Space>
  )
}
