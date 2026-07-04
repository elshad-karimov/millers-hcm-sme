// HCM_12 M399 — development plans (PRD §21). Typed actions (§21.2), progress
// rolls up from done actions; "from review" builds a plan out of the M393
// competency gaps (§21.3).

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Progress,
  Row,
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
  devPlansApi,
  type DevAction,
  type DevActionRequest,
  type DevActionStatus,
  type DevActionType,
  type DevPlan,
  type DevPlanStatus,
} from '../../api/performance'
import { employeesApi, type Employee } from '../../api/employees'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<DevPlanStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  IN_PROGRESS: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
}

const ACTION_TYPES: DevActionType[] = [
  'TRAINING_COURSE',
  'CERTIFICATION',
  'COACHING',
  'MENTORING',
  'JOB_ROTATION',
  'STRETCH_ASSIGNMENT',
  'PROJECT_ASSIGNMENT',
  'SELF_STUDY',
  'WORKSHOP',
  'LEADERSHIP_PROGRAM',
]

const ACTION_STATUS_COLOR: Record<DevActionStatus, string> = {
  PLANNED: 'default',
  IN_PROGRESS: 'gold',
  DONE: 'green',
  CANCELLED: 'default',
}

export function DevPlansPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [rows, setRows] = useState<DevPlan[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)
  const [actionsByPlan, setActionsByPlan] = useState<Record<string, DevAction[]>>({})

  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<DevPlan | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const [editorActions, setEditorActions] = useState<DevActionRequest[]>([])

  const load = () => {
    setLoading(true)
    Promise.all([devPlansApi.list(), employeesApi.list({ size: 500 })])
      .then(([p, e]) => {
        setRows(p)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load development plans'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const empName = (id?: string | null) => {
    if (!id) return '—'
    const e = empMap.get(id)
    return e ? `${e.firstName} ${e.lastName}` : id
  }

  const loadActions = (planId: string) => {
    devPlansApi
      .actions(planId)
      .then((a) => setActionsByPlan((prev) => ({ ...prev, [planId]: a })))
      .catch(() => message.error('Failed to load actions'))
  }

  const openEditor = (p?: DevPlan) => {
    setEditing(p ?? null)
    form.resetFields()
    if (p) {
      form.setFieldsValue({
        ...p,
        targetDate: p.targetDate ? dayjs(p.targetDate) : null,
      })
      devPlansApi.actions(p.id).then((a) =>
        setEditorActions(
          a.map((x) => ({
            actionType: x.actionType,
            description: x.description,
            dueDate: x.dueDate,
            status: x.status,
          })),
        ),
      )
    } else {
      setEditorActions([{ actionType: 'TRAINING_COURSE', description: '', status: 'PLANNED' }])
    }
    setEditOpen(true)
  }

  const save = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      const req = {
        ...v,
        targetDate: v.targetDate ? v.targetDate.format('YYYY-MM-DD') : null,
        actions: editorActions.filter((a) => a.description.trim()),
      }
      if (editing) await devPlansApi.update(editing.id, req)
      else await devPlansApi.create(req)
      message.success(editing ? 'Plan updated' : 'Plan created')
      setEditOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const mark = async (action: DevAction, status: DevActionStatus) => {
    try {
      await devPlansApi.markAction(action.id, status)
      message.success('Action updated')
      loadActions(action.planId)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Update failed')
    }
  }

  const columns: ColumnsType<DevPlan> = [
    { title: 'Employee', key: 'emp', render: (_, p) => empName(p.employeeId), width: 180 },
    { title: 'Plan', dataIndex: 'title', ellipsis: true },
    {
      title: 'Progress',
      key: 'progress',
      width: 160,
      render: (_, p) => <Progress percent={Number(p.progressPercent)} size="small" />,
    },
    { title: 'Target', dataIndex: 'targetDate', width: 110, render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (v: DevPlanStatus) => <Tag color={STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 160,
      render: (_, p) =>
        canWrite && (
          <Space size={4}>
            <Button size="small" onClick={() => openEditor(p)}>
              Edit
            </Button>
            {p.status !== 'CANCELLED' && p.status !== 'COMPLETED' && (
              <Button
                size="small"
                danger
                onClick={() =>
                  devPlansApi.changeStatus(p.id, 'CANCELLED').then(() => {
                    message.success('Plan cancelled')
                    load()
                  })
                }
              >
                Cancel
              </Button>
            )}
          </Space>
        ),
    },
  ]

  if (loading && !rows.length) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Development plans
          </Title>
          <Text type="secondary">
            Growth plans with typed actions (§21) — progress rolls up as actions are done. Plans
            can be generated from a review&apos;s competency gaps.
          </Text>
        </Col>
        <Col>
          {canWrite && (
            <Button type="primary" onClick={() => openEditor()}>
              New plan
            </Button>
          )}
        </Col>
      </Row>

      <Card size="small">
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          expandable={{
            onExpand: (expanded, p) => expanded && loadActions(p.id),
            expandedRowRender: (p) => (
              <Table
                rowKey="id"
                size="small"
                columns={[
                  {
                    title: 'Action',
                    dataIndex: 'description',
                  },
                  {
                    title: 'Type',
                    dataIndex: 'actionType',
                    width: 170,
                    render: (v: DevActionType) => <Tag color="purple">{v.replace(/_/g, ' ')}</Tag>,
                  },
                  { title: 'Due', dataIndex: 'dueDate', width: 110, render: (v) => v ?? '—' },
                  {
                    title: 'Status',
                    dataIndex: 'status',
                    width: 120,
                    render: (v: DevActionStatus) => (
                      <Tag color={ACTION_STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>
                    ),
                  },
                  {
                    title: '',
                    key: 'a',
                    width: 200,
                    render: (_: unknown, a: DevAction) => (
                      <Space size={4}>
                        {a.status !== 'DONE' && a.status !== 'CANCELLED' && (
                          <>
                            <Button size="small" type="primary" onClick={() => mark(a, 'DONE')}>
                              Done
                            </Button>
                            <Button size="small" onClick={() => mark(a, 'IN_PROGRESS')}>
                              Start
                            </Button>
                          </>
                        )}
                        {a.status === 'DONE' && (
                          <Button size="small" onClick={() => mark(a, 'PLANNED')}>
                            Reopen
                          </Button>
                        )}
                      </Space>
                    ),
                  },
                ]}
                dataSource={actionsByPlan[p.id] ?? []}
                pagination={false}
                locale={{ emptyText: 'No actions.' }}
              />
            ),
          }}
        />
      </Card>

      {/* Editor */}
      <Modal
        title={editing ? 'Edit development plan' : 'New development plan'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={save}
        confirmLoading={saving}
        okText="Save"
        width={760}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!!editing}
                  options={employees.map((e) => ({
                    value: e.id,
                    label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label="Title" rules={[{ required: true, max: 300 }]}>
                <Input placeholder="Grow into senior analyst" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="careerGoal" label="Career goal">
                <Input placeholder="Team lead within 2 years" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="targetDate" label="Target date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Typography.Text strong>Actions (§21.2)</Typography.Text>
          {editorActions.map((a, i) => (
            <Row key={i} gutter={8} style={{ marginTop: 8 }} align="middle">
              <Col span={7}>
                <Select
                  style={{ width: '100%' }}
                  value={a.actionType ?? 'TRAINING_COURSE'}
                  onChange={(v) => {
                    const next = [...editorActions]
                    next[i] = { ...a, actionType: v }
                    setEditorActions(next)
                  }}
                  options={ACTION_TYPES.map((t) => ({
                    value: t,
                    label: t.replace(/_/g, ' '),
                  }))}
                />
              </Col>
              <Col span={13}>
                <Input
                  placeholder="Action description"
                  value={a.description}
                  onChange={(e) => {
                    const next = [...editorActions]
                    next[i] = { ...a, description: e.target.value }
                    setEditorActions(next)
                  }}
                />
              </Col>
              <Col span={2}>
                <Button
                  size="small"
                  danger
                  onClick={() => setEditorActions(editorActions.filter((_, x) => x !== i))}
                >
                  ✕
                </Button>
              </Col>
            </Row>
          ))}
          <Button
            style={{ marginTop: 8 }}
            onClick={() =>
              setEditorActions([
                ...editorActions,
                { actionType: 'TRAINING_COURSE', description: '', status: 'PLANNED' },
              ])
            }
          >
            Add action
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
