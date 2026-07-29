// HCM_12 M398 — performance improvement plans (PRD §20). One live PIP per
// employee; acknowledge → periodic checkpoints (1–5 progress) → extend / close
// with a controlled outcome. Evidence files reuse the M16 attachment registry.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  pipsApi,
  type Pip,
  type PipCheckpoint,
  type PipOutcome,
  type PipStatus,
} from '../../api/performance'
import { employeesApi, type Employee } from '../../api/employees'
import { AttachmentUploader } from '../../components/AttachmentUploader'
import { EmployeePicker } from '../../components/EmployeePicker'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<PipStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  ACKNOWLEDGED: 'cyan',
  IN_PROGRESS: 'gold',
  EXTENDED: 'orange',
  COMPLETED_SUCCESS: 'green',
  FAILED: 'red',
  CANCELLED: 'default',
}

const OUTCOMES: PipOutcome[] = [
  'IMPROVED',
  'EXTENDED',
  'ROLE_CHANGE',
  'TRAINING_REQUIRED',
  'DISCIPLINARY',
  'TERMINATION_RECOMMENDED',
]

const LIVE: PipStatus[] = ['ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS', 'EXTENDED']

export function PipPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)
  const canHr = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<Pip[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<PipStatus | undefined>()

  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<Pip | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const [detail, setDetail] = useState<Pip | null>(null)
  const [checkpoints, setCheckpoints] = useState<PipCheckpoint[]>([])
  const [cpForm] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([pipsApi.list(statusFilter ? { status: statusFilter } : {}), employeesApi.list({ size: 500 })])
      .then(([p, e]) => {
        setRows(p)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load PIPs'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [statusFilter]) // eslint-disable-line react-hooks/exhaustive-deps

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const empName = (id?: string | null) => {
    if (!id) return '—'
    const e = empMap.get(id)
    return e ? `${e.firstName} ${e.lastName}` : id
  }

  const openEditor = (p?: Pip) => {
    setEditing(p ?? null)
    form.resetFields()
    if (p) {
      form.setFieldsValue({
        ...p,
        startDate: dayjs(p.startDate),
        endDate: dayjs(p.endDate),
      })
    }
    setEditOpen(true)
  }

  const save = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      const req = {
        ...v,
        startDate: v.startDate.format('YYYY-MM-DD'),
        endDate: v.endDate.format('YYYY-MM-DD'),
      }
      if (editing) await pipsApi.update(editing.id, req)
      else await pipsApi.create(req)
      message.success(editing ? 'PIP updated' : 'PIP created (DRAFT)')
      setEditOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const openDetail = (p: Pip) => {
    setDetail(p)
    pipsApi.checkpoints(p.id).then(setCheckpoints).catch(() => setCheckpoints([]))
  }

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn()
      message.success(ok)
      setDetail(null)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Action failed')
    }
  }

  const columns: ColumnsType<Pip> = [
    { title: 'Employee', key: 'emp', render: (_, p) => empName(p.employeeId) },
    { title: 'Reason', dataIndex: 'reason', ellipsis: true },
    { title: 'Window', key: 'window', width: 200, render: (_, p) => `${p.startDate} → ${p.endDate}` },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 160,
      render: (v: PipStatus) => <Tag color={STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Outcome',
      dataIndex: 'outcome',
      width: 170,
      render: (v: PipOutcome | null) => (v ? <Tag color="purple">{v.replace(/_/g, ' ')}</Tag> : '—'),
    },
    {
      title: '',
      key: 'actions',
      width: 140,
      render: (_, p) => (
        <Space size={4}>
          <Button size="small" onClick={() => openDetail(p)}>
            Open
          </Button>
          {canWrite && p.status === 'DRAFT' && (
            <Button size="small" onClick={() => openEditor(p)}>
              Edit
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
            Performance improvement plans
          </Title>
          <Text type="secondary">
            Structured improvement (§20): draft → activate → employee acknowledges → checkpoints →
            outcome. One live PIP per employee; history is never deleted.
          </Text>
        </Col>
        <Col>
          {canWrite && (
            <Button type="primary" onClick={() => openEditor()}>
              New PIP
            </Button>
          )}
        </Col>
      </Row>

      <Card size="small">
        <Select
          allowClear
          style={{ minWidth: 220, marginBottom: 12 }}
          placeholder="All statuses"
          value={statusFilter}
          onChange={setStatusFilter}
          options={(Object.keys(STATUS_COLOR) as PipStatus[]).map((s) => ({
            value: s,
            label: s.replace(/_/g, ' '),
          }))}
        />
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
        />
      </Card>

      {/* Editor */}
      <Modal
        title={editing ? 'Edit PIP (draft)' : 'New PIP'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={save}
        confirmLoading={saving}
        okText="Save"
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
                <EmployeePicker disabled={!!editing} placeholder="Select employee" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="managerOwnerId" label="Manager owner">
                <EmployeePicker allowClear placeholder="Select manager owner" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="reason" label="Reason" rules={[{ required: true, max: 2000 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="startDate" label="Start" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label="End" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="issueDescription" label="Performance issue">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="objectives" label="Improvement objectives">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="successCriteria" label="Success criteria">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="supportActions" label="Training / coaching actions">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="consequences" label="Consequences if not improved">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* Detail drawer */}
      <Drawer
        title={detail ? `PIP — ${empName(detail.employeeId)}` : ''}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={720}
      >
        {detail && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space size={8} wrap>
              <Tag color={STATUS_COLOR[detail.status]}>{detail.status.replace(/_/g, ' ')}</Tag>
              <Tag>
                {detail.startDate} → {detail.endDate}
              </Tag>
              {detail.acknowledgedAt && (
                <Tag color="cyan">acknowledged {new Date(detail.acknowledgedAt).toLocaleDateString()}</Tag>
              )}
              {detail.outcome && <Tag color="purple">{detail.outcome.replace(/_/g, ' ')}</Tag>}
            </Space>
            <Text>{detail.reason}</Text>
            {detail.objectives && (
              <Text type="secondary">Objectives: {detail.objectives}</Text>
            )}
            {detail.successCriteria && (
              <Text type="secondary">Success criteria: {detail.successCriteria}</Text>
            )}

            <Space size={8} wrap>
              {canWrite && detail.status === 'DRAFT' && (
                <Button type="primary" onClick={() => act(() => pipsApi.activate(detail.id), 'PIP activated')}>
                  Activate
                </Button>
              )}
              {detail.status === 'ACTIVE' && (
                <Button
                  onClick={() =>
                    Modal.confirm({
                      title: 'Acknowledge this PIP?',
                      content: <Input.TextArea id="pip-ack-comments" placeholder="Comments (optional)" rows={2} />,
                      onOk: () =>
                        act(
                          () =>
                            pipsApi.acknowledge(
                              detail.id,
                              (document.getElementById('pip-ack-comments') as HTMLTextAreaElement)
                                ?.value || undefined,
                            ),
                          'PIP acknowledged',
                        ),
                    })
                  }
                >
                  Acknowledge (employee)
                </Button>
              )}
              {canWrite && LIVE.includes(detail.status) && (
                <Button
                  onClick={() =>
                    Modal.confirm({
                      title: 'Extend PIP',
                      content: (
                        <Input id="pip-extend-date" placeholder="YYYY-MM-DD (new end date)" />
                      ),
                      onOk: () =>
                        act(
                          () =>
                            pipsApi.extend(
                              detail.id,
                              (document.getElementById('pip-extend-date') as HTMLInputElement)?.value,
                            ),
                          'PIP extended',
                        ),
                    })
                  }
                >
                  Extend
                </Button>
              )}
              {canHr && LIVE.includes(detail.status) && (
                <Button
                  danger
                  onClick={() => {
                    let outcome: PipOutcome = 'IMPROVED'
                    Modal.confirm({
                      title: 'Close PIP with outcome (HR)',
                      content: (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Select
                            style={{ width: '100%' }}
                            defaultValue={'IMPROVED' as PipOutcome}
                            onChange={(v) => {
                              outcome = v
                            }}
                            options={OUTCOMES.map((o) => ({
                              value: o,
                              label: o.replace(/_/g, ' '),
                            }))}
                          />
                          <Input.TextArea id="pip-close-notes" placeholder="Outcome notes" rows={2} />
                        </Space>
                      ),
                      onOk: () =>
                        act(
                          () =>
                            pipsApi.close(
                              detail.id,
                              outcome,
                              (document.getElementById('pip-close-notes') as HTMLTextAreaElement)
                                ?.value || undefined,
                            ),
                          'PIP closed',
                        ),
                    })
                  }}
                >
                  Close with outcome
                </Button>
              )}
            </Space>

            {canWrite && LIVE.includes(detail.status) && detail.status !== 'ACTIVE' && (
              <Card size="small" title="Add checkpoint (§20.3)">
                <Form
                  form={cpForm}
                  layout="inline"
                  onFinish={async (v) => {
                    try {
                      await pipsApi.addCheckpoint(detail.id, {
                        checkpointDate: v.checkpointDate
                          ? v.checkpointDate.format('YYYY-MM-DD')
                          : undefined,
                        progressRating: v.progressRating,
                        managerComments: v.managerComments,
                      })
                      message.success('Checkpoint recorded')
                      cpForm.resetFields()
                      pipsApi.checkpoints(detail.id).then(setCheckpoints)
                    } catch (e: unknown) {
                      const err = e as { response?: { data?: { message?: string } } }
                      message.error(err.response?.data?.message ?? 'Checkpoint failed')
                    }
                  }}
                >
                  <Form.Item name="checkpointDate">
                    <DatePicker placeholder="Date" />
                  </Form.Item>
                  <Form.Item name="progressRating">
                    <InputNumber min={1} max={5} placeholder="1–5" />
                  </Form.Item>
                  <Form.Item name="managerComments" style={{ flex: 1 }}>
                    <Input placeholder="Manager comments" />
                  </Form.Item>
                  <Button htmlType="submit" type="primary" size="small">
                    Record
                  </Button>
                </Form>
              </Card>
            )}
            {detail.status === 'ACTIVE' && canWrite && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Checkpoints unlock after the employee acknowledges the PIP.
              </Text>
            )}

            <Card size="small" title="Checkpoints">
              {checkpoints.length === 0 ? (
                <Text type="secondary">No checkpoints yet.</Text>
              ) : (
                <Timeline
                  items={checkpoints.map((c) => ({
                    children: (
                      <Space direction="vertical" size={0}>
                        <Text>
                          {c.checkpointDate}
                          {c.progressRating != null && (
                            <Tag
                              style={{ marginLeft: 8 }}
                              color={c.progressRating >= 3 ? 'green' : 'red'}
                            >
                              {c.progressRating}/5
                            </Tag>
                          )}
                        </Text>
                        {c.managerComments && <Text type="secondary">{c.managerComments}</Text>}
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {c.recordedBy ?? '—'}
                        </Text>
                      </Space>
                    ),
                  }))}
                />
              )}
            </Card>

            <Card size="small" title="Evidence / documents">
              <AttachmentUploader
                ownerModule="performance"
                ownerEntity="pip"
                ownerId={detail.id}
              />
            </Card>
          </Space>
        )}
      </Drawer>
    </div>
  )
}
