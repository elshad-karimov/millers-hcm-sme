// M105/M106 — Onboarding & offboarding checklists.
// Unified HR page with tabs for both flows. Lists active assignments with
// per-task progress; supports start-new and inline task status updates.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  checklistsApi,
  TASK_STATUS_COLOR,
  type AssignmentResponse,
  type ChecklistFlowType,
  type ChecklistTaskStatusValue,
  type TaskStatusResponse,
  type TemplateResponse,
} from '../api/checklists'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const STATUS_OPTIONS: { value: ChecklistTaskStatusValue; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'DONE', label: 'Done' },
  { value: 'SKIPPED', label: 'Skipped' },
]

function ChecklistPanel({ flow }: { flow: ChecklistFlowType }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canStart = hasRole(...RoleSets.HR_WRITE)

  const [assignments, setAssignments] = useState<AssignmentResponse[]>([])
  const [templates, setTemplates] = useState<TemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<AssignmentResponse | null>(null)
  const [startOpen, setStartOpen] = useState(false)
  const [startForm] = Form.useForm<{
    templateId: string
    employeeId: string
    anchorDate: ReturnType<typeof dayjs>
    notes?: string
  }>()
  const [starting, setStarting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([
      checklistsApi.active(flow),
      checklistsApi.templatesByFlow(flow),
    ])
      .then(([a, t]) => { setAssignments(a); setTemplates(t) })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [flow])

  const submitStart = async () => {
    const v = await startForm.validateFields()
    setStarting(true)
    try {
      await checklistsApi.start({
        templateId: v.templateId,
        employeeId: v.employeeId,
        anchorDate: v.anchorDate?.format('YYYY-MM-DD'),
        notes: v.notes,
      })
      message.success(`${flow} checklist started`)
      setStartOpen(false)
      startForm.resetFields()
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    } finally { setStarting(false) }
  }

  const updateTask = async (
    task: TaskStatusResponse,
    patch: { status?: ChecklistTaskStatusValue; notes?: string },
  ) => {
    try {
      const updated = await checklistsApi.updateTask(task.id, patch)
      setSelected(updated)
      setAssignments((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Update failed',
      )
    }
  }

  const cancelAssignment = async (a: AssignmentResponse) => {
    try {
      await checklistsApi.cancel(a.id)
      message.success('Cancelled')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Cancel failed',
      )
    }
  }

  const cols: ColumnsType<AssignmentResponse> = [
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <a onClick={() => setSelected(r)}>{r.employeeName ?? '—'}</a>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.templateName}</Text>
        </Space>
      ),
    },
    {
      title: 'Anchor date',
      dataIndex: 'anchorDate',
      width: 130,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: 'Progress',
      width: 220,
      render: (_, r) => (
        <Tooltip title={`${r.completedTasks} of ${r.totalTasks} tasks`}>
          <Progress
            percent={r.progressPercent}
            size="small"
            status={r.requiredCompleted >= r.requiredTotal && r.requiredTotal > 0 ? 'success' : 'active'}
          />
        </Tooltip>
      ),
    },
    {
      title: 'Required',
      width: 110,
      align: 'center',
      render: (_, r) => (
        <Tag color={r.requiredCompleted >= r.requiredTotal && r.requiredTotal > 0 ? 'green' : 'blue'}>
          {r.requiredCompleted} / {r.requiredTotal}
        </Tag>
      ),
    },
    {
      title: 'Started',
      dataIndex: 'startedAt',
      width: 120,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: '',
      width: 100,
      render: (_, r) => canStart ? (
        <Popconfirm
          title="Cancel this checklist?"
          onConfirm={() => cancelAssignment(r)}
          okText="Cancel"
          cancelText="Keep"
        >
          <Button size="small" danger>Cancel</Button>
        </Popconfirm>
      ) : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Statistic title={`Active ${flow.toLowerCase()} checklists`} value={assignments.length} />
        {canStart && (
          <Button type="primary" onClick={() => setStartOpen(true)}>
            Start {flow.toLowerCase()}…
          </Button>
        )}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={assignments}
          pagination={{ pageSize: 20 }}
          size="small"
          locale={{ emptyText: <Empty description="No active checklists" /> }}
        />
      </Card>

      <Drawer
        open={!!selected}
        title={selected ? `${selected.employeeName} — ${selected.templateName}` : ''}
        onClose={() => setSelected(null)}
        width={620}
      >
        {selected && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Progress percent={selected.progressPercent} />
            <Text>
              {selected.completedTasks} of {selected.totalTasks} tasks done · {selected.requiredCompleted} of {selected.requiredTotal} required
            </Text>
            {selected.tasks.map((task) => (
              <Card key={task.id} size="small" style={{
                borderLeft: task.required
                  ? `3px solid ${task.status === 'DONE' || task.status === 'SKIPPED' ? '#52c41a' : '#1677ff'}`
                  : undefined,
              }}>
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Space>
                      <Tag>{task.stepOrder}</Tag>
                      <Text strong>{task.title}</Text>
                      {task.required && <Tag color="red">required</Tag>}
                      {task.ownerRole && <Tag>{task.ownerRole}</Tag>}
                    </Space>
                    <Tag color={TASK_STATUS_COLOR[task.status]}>{task.status.replace(/_/g, ' ')}</Tag>
                  </Space>
                  {task.description && (
                    <Text type="secondary" style={{ fontSize: 12 }}>{task.description}</Text>
                  )}
                  <Space>
                    {task.dueDate && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Due: {task.dueDate}
                      </Text>
                    )}
                    {STATUS_OPTIONS.map((opt) => (
                      <Checkbox
                        key={opt.value}
                        checked={task.status === opt.value}
                        onChange={() => updateTask(task, { status: opt.value })}
                      >
                        {opt.label}
                      </Checkbox>
                    ))}
                  </Space>
                </Space>
              </Card>
            ))}
          </Space>
        )}
      </Drawer>

      <Modal
        open={startOpen}
        title={`Start ${flow.toLowerCase()} checklist`}
        onCancel={() => setStartOpen(false)}
        onOk={submitStart}
        confirmLoading={starting}
        okText="Start"
      >
        <Form form={startForm} layout="vertical">
          <Form.Item
            name="templateId"
            label="Template"
            rules={[{ required: true, message: 'Pick a template' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Pick template"
              options={templates.map((t) => ({
                value: t.id,
                label: `${t.code} — ${t.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="Employee ID"
            rules={[{ required: true, message: 'Required' }]}
            extra="UUID of the employee (paste from their profile page)."
          >
            <Input placeholder="00000000-0000-0000-0000-000000000000" />
          </Form.Item>
          <Form.Item
            name="anchorDate"
            label={flow === 'ONBOARDING' ? 'Hire date' : 'Last working day'}
            rules={[{ required: true, message: 'Required' }]}
            extra="Task due dates are calculated relative to this anchor."
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={3} placeholder="Manager handover notes, special instructions, etc." />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

export function ChecklistsPage() {
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Onboarding & offboarding</Title>
      <Text type="secondary">
        Standardised task lists driven by HR templates.
        Tasks auto-complete the checklist when every required item is DONE
        (or SKIPPED). For template editing, ask a SYSTEM_ADMIN.
      </Text>
      <Tabs
        items={[
          { key: 'on', label: 'Onboarding', children: <ChecklistPanel flow="ONBOARDING" /> },
          { key: 'off', label: 'Offboarding', children: <ChecklistPanel flow="OFFBOARDING" /> },
        ]}
      />
    </Space>
  )
}

