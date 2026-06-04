// M111 — Shift patterns + auto-roster generator.
//
// HR Admin defines rotation patterns (cycle of N days; each day maps to a
// shift or rest); HR/specialists assign patterns to employees and trigger
// auto-generation of RosterEntry rows from the pattern over a date range.

import { useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  shiftPatternsApi,
  type AssignmentResponse,
  type GenerateRosterResponse,
  type PatternRequest,
  type PatternResponse,
} from '../api/shiftPatterns'
import { shiftsApi, type Shift } from '../api/roster'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

function PatternListTab({
  onOpen,
}: { onOpen: (p: PatternResponse | null) => void }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [patterns, setPatterns] = useState<PatternResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(false)

  const load = () => {
    setLoading(true)
    shiftPatternsApi
      .list(activeOnly)
      .then(setPatterns)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const cols: ColumnsType<PatternResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 130,
      render: (v, r) => <a onClick={() => onOpen(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Cycle',
      dataIndex: 'cycleDays',
      width: 90,
      align: 'right',
      render: (v: number) => `${v} day${v === 1 ? '' : 's'}`,
    },
    {
      title: 'Working / rest',
      width: 130,
      render: (_, r) => {
        const working = r.days.filter((d) => d.shiftId).length
        const off = r.days.length - working
        return <Text>{working} on / {off} off</Text>
      },
    },
    {
      title: 'Assigned to',
      dataIndex: 'assignmentCount',
      width: 110,
      align: 'right',
      render: (v: number) => v === 0
        ? <Text type="secondary">—</Text>
        : <Tag color="blue">{v}</Tag>,
    },
    {
      title: 'Status',
      width: 100,
      align: 'center',
      render: (_, r) => r.active
        ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Switch
          checked={activeOnly}
          onChange={setActiveOnly}
          checkedChildren="Active only"
          unCheckedChildren="All patterns"
        />
        {canEdit && <Button type="primary" onClick={() => onOpen(null)}>New pattern…</Button>}
      </Space>
      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={patterns}
          size="small"
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: <Empty description="No patterns" /> }}
        />
      </Card>
    </Space>
  )
}

// ── Pattern editor drawer ────────────────────────────────────────────────────

function PatternEditor({
  open,
  pattern,
  shifts,
  onClose,
  onSaved,
}: {
  open: boolean
  pattern: PatternResponse | null
  shifts: Shift[]
  onClose: () => void
  onSaved: () => void
}) {
  const { message } = AntdApp.useApp()
  const [cycleDays, setCycleDays] = useState(7)
  const [dayShifts, setDayShifts] = useState<(string | null)[]>(new Array(7).fill(null))
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [active, setActive] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (pattern) {
      setCode(pattern.code)
      setName(pattern.name)
      setDescription(pattern.description ?? '')
      setActive(pattern.active)
      setCycleDays(pattern.cycleDays)
      const days = new Array(pattern.cycleDays).fill(null) as (string | null)[]
      pattern.days.forEach((d) => { days[d.dayIndex] = d.shiftId ?? null })
      setDayShifts(days)
    } else {
      setCode('')
      setName('')
      setDescription('')
      setActive(true)
      setCycleDays(7)
      setDayShifts(new Array(7).fill(null))
    }
  }, [pattern, open])

  const onCycleDaysChange = (v: number | null) => {
    if (!v || v < 1) return
    const next = new Array(v).fill(null) as (string | null)[]
    for (let i = 0; i < Math.min(v, dayShifts.length); i++) next[i] = dayShifts[i]
    setCycleDays(v)
    setDayShifts(next)
  }

  const submit = async () => {
    if (!code || !name) {
      message.error('Code and name are required')
      return
    }
    const req: PatternRequest = {
      code,
      name,
      description: description || undefined,
      cycleDays,
      active,
      days: dayShifts.map((shiftId, dayIndex) => ({ dayIndex, shiftId })),
    }
    setSaving(true)
    try {
      if (pattern) {
        await shiftPatternsApi.update(pattern.id, req)
        message.success('Pattern updated')
      } else {
        await shiftPatternsApi.create(req)
        message.success('Pattern created')
      }
      onSaved()
      onClose()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally { setSaving(false) }
  }

  const shiftOptions = [
    { value: '__off__', label: <Text type="secondary">OFF (rest day)</Text> },
    ...shifts.map((s) => ({
      value: s.id,
      label: (
        <Space>
          {s.color && <div style={{ width: 10, height: 10, borderRadius: 2, background: s.color }} />}
          <span>{s.code} — {s.startTime.slice(0, 5)} → {s.endTime.slice(0, 5)}</span>
        </Space>
      ),
    })),
  ]

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={pattern ? `Edit pattern — ${pattern.code}` : 'New pattern'}
      width={720}
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" onClick={submit} loading={saving}>
            {pattern ? 'Save' : 'Create'}
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row gutter={12}>
          <Col span={8}>
            <Text strong>Code</Text>
            <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="4ON-3OFF" />
          </Col>
          <Col span={10}>
            <Text strong>Name</Text>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="4-on, 3-off rotation" />
          </Col>
          <Col span={6}>
            <Text strong>Cycle (days)</Text>
            <InputNumber
              min={1}
              max={365}
              value={cycleDays}
              onChange={onCycleDaysChange}
              style={{ width: '100%' }}
            />
          </Col>
        </Row>

        <div>
          <Text strong>Description</Text>
          <Input.TextArea
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What rotation does this pattern model?"
          />
        </div>

        <div>
          <Switch
            checked={active}
            onChange={setActive}
            checkedChildren="Active"
            unCheckedChildren="Inactive"
          />
        </div>

        <Card title="Cycle days" size="small">
          <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            Pick a shift for each day in the cycle, or leave it as OFF for a rest day.
            The cycle repeats forever — day {cycleDays} loops back to day 1.
          </Text>
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            {dayShifts.map((shiftId, idx) => {
              const shift = shifts.find((s) => s.id === shiftId)
              return (
                <Row key={idx} gutter={8} align="middle">
                  <Col span={3}>
                    <Tag>Day {idx + 1}</Tag>
                  </Col>
                  <Col span={4}>
                    {shiftId ? (
                      <div style={{
                        background: shift?.color ?? '#1677ff',
                        color: 'white',
                        padding: '2px 8px',
                        borderRadius: 3,
                        fontSize: 12,
                        fontWeight: 600,
                        textAlign: 'center',
                      }}>{shift?.code ?? '—'}</div>
                    ) : (
                      <Text type="secondary">OFF</Text>
                    )}
                  </Col>
                  <Col span={17}>
                    <Select
                      style={{ width: '100%' }}
                      value={shiftId ?? '__off__'}
                      onChange={(v) => {
                        const next = [...dayShifts]
                        next[idx] = v === '__off__' ? null : v
                        setDayShifts(next)
                      }}
                      options={shiftOptions}
                    />
                  </Col>
                </Row>
              )
            })}
          </Space>
        </Card>
      </Space>
    </Drawer>
  )
}

// ── Assignments + generate tab ──────────────────────────────────────────────

function GenerateTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canGenerate = hasRole(...RoleSets.HR_WRITE)

  const [patterns, setPatterns] = useState<PatternResponse[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [assignmentsByPattern, setAssignmentsByPattern] = useState<Record<string, AssignmentResponse[]>>({})
  const [selectedPatternId, setSelectedPatternId] = useState<string | undefined>()
  const [assignOpen, setAssignOpen] = useState(false)
  const [genOpen, setGenOpen] = useState(false)
  const [lastGen, setLastGen] = useState<GenerateRosterResponse | null>(null)
  const [assignForm] = Form.useForm<{
    employeeId: string
    patternId: string
    startDate: ReturnType<typeof dayjs>
    endDate?: ReturnType<typeof dayjs>
    anchorDayIndex: number
    notes?: string
  }>()
  const [genForm] = Form.useForm<{
    range: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
    employeeIds?: string[]
    overwriteExisting: boolean
  }>()

  useEffect(() => {
    Promise.all([
      shiftPatternsApi.list(true),
      employeesApi.list({ size: 500 }),
    ])
      .then(([p, e]) => {
        setPatterns(p)
        setEmployees(e.content)
        if (p.length > 0) setSelectedPatternId(p[0].id)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
  }, [message])

  useEffect(() => {
    if (!selectedPatternId) return
    shiftPatternsApi
      .assignmentsForPattern(selectedPatternId)
      .then((a) => setAssignmentsByPattern((prev) => ({ ...prev, [selectedPatternId]: a })))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load assignments'))
  }, [selectedPatternId, message])

  const startAssign = () => {
    assignForm.resetFields()
    assignForm.setFieldsValue({
      patternId: selectedPatternId,
      startDate: dayjs(),
      anchorDayIndex: 0,
    })
    setAssignOpen(true)
  }

  const submitAssign = async () => {
    const v = await assignForm.validateFields()
    try {
      await shiftPatternsApi.assign({
        employeeId: v.employeeId,
        patternId: v.patternId,
        startDate: v.startDate.format('YYYY-MM-DD'),
        endDate: v.endDate ? v.endDate.format('YYYY-MM-DD') : undefined,
        anchorDayIndex: v.anchorDayIndex,
        notes: v.notes,
      })
      message.success('Pattern assigned')
      setAssignOpen(false)
      if (selectedPatternId) {
        const fresh = await shiftPatternsApi.assignmentsForPattern(selectedPatternId)
        setAssignmentsByPattern((prev) => ({ ...prev, [selectedPatternId]: fresh }))
      }
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Assign failed',
      )
    }
  }

  const startGenerate = () => {
    genForm.resetFields()
    genForm.setFieldsValue({
      range: [dayjs().startOf('month'), dayjs().endOf('month')],
      overwriteExisting: false,
    })
    setGenOpen(true)
  }

  const submitGenerate = async () => {
    const v = await genForm.validateFields()
    try {
      const res = await shiftPatternsApi.generateRoster({
        from: v.range[0].format('YYYY-MM-DD'),
        to: v.range[1].format('YYYY-MM-DD'),
        employeeIds: v.employeeIds && v.employeeIds.length > 0 ? v.employeeIds : undefined,
        overwriteExisting: v.overwriteExisting,
      })
      setLastGen(res)
      message.success(
        `Generated ${res.rosterRowsCreated} new entr${res.rosterRowsCreated === 1 ? 'y' : 'ies'}` +
        (res.rosterRowsUpdated > 0 ? ` (${res.rosterRowsUpdated} updated)` : '') +
        (res.rosterRowsSkippedLocked > 0 ? `; skipped ${res.rosterRowsSkippedLocked} locked` : '')
      )
      setGenOpen(false)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Generation failed',
      )
    }
  }

  const endAssignment = async (a: AssignmentResponse) => {
    const today = dayjs()
    try {
      await shiftPatternsApi.endAssignment(a.id, {
        endDate: today.format('YYYY-MM-DD'),
        reason: 'Ended via UI',
      })
      message.success('Assignment ended')
      if (selectedPatternId) {
        const fresh = await shiftPatternsApi.assignmentsForPattern(selectedPatternId)
        setAssignmentsByPattern((prev) => ({ ...prev, [selectedPatternId]: fresh }))
      }
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'End failed',
      )
    }
  }

  const currentAssignments = selectedPatternId
    ? (assignmentsByPattern[selectedPatternId] ?? [])
    : []

  const assignmentCols: ColumnsType<AssignmentResponse> = [
    { title: 'Employee', dataIndex: 'employeeName', render: (v) => v ?? '—' },
    { title: 'Start', dataIndex: 'startDate', width: 110 },
    {
      title: 'End',
      dataIndex: 'endDate',
      width: 120,
      render: (v) => v ?? <Tag color="green">Open</Tag>,
    },
    {
      title: 'Anchor',
      dataIndex: 'anchorDayIndex',
      width: 90,
      align: 'center',
      render: (v: number) => `Day ${v + 1}`,
    },
    { title: 'Notes', dataIndex: 'notes', render: (v) => v ?? '—' },
    {
      title: '',
      width: 100,
      render: (_, r) => canGenerate && !r.endDate ? (
        <Popconfirm
          title="End this assignment today?"
          onConfirm={() => endAssignment(r)}
        >
          <Button size="small" danger>End</Button>
        </Popconfirm>
      ) : null,
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card size="small">
        <Row gutter={12} align="middle">
          <Col flex="auto">
            <Space>
              <Text strong>Pattern:</Text>
              <Select
                style={{ minWidth: 300 }}
                value={selectedPatternId}
                onChange={setSelectedPatternId}
                options={patterns.map((p) => ({
                  value: p.id,
                  label: `${p.code} — ${p.name} (${p.cycleDays} days)`,
                }))}
                placeholder="Pick a pattern"
              />
            </Space>
          </Col>
          <Col>
            <Space>
              {canGenerate && selectedPatternId && (
                <Button onClick={startAssign}>Assign employee…</Button>
              )}
              {canGenerate && (
                <Button type="primary" onClick={startGenerate}>Generate roster…</Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      {lastGen && (
        <Alert
          showIcon
          type="success"
          onClose={() => setLastGen(null)}
          closable
          message="Last generation result"
          description={
            <Space size="large">
              <Statistic title="Employees" value={lastGen.employeesProcessed} valueStyle={{ fontSize: 18 }} />
              <Statistic title="Created" value={lastGen.rosterRowsCreated} valueStyle={{ fontSize: 18 }} />
              <Statistic title="Updated" value={lastGen.rosterRowsUpdated} valueStyle={{ fontSize: 18 }} />
              <Statistic title="Skipped (locked)" value={lastGen.rosterRowsSkippedLocked} valueStyle={{ fontSize: 18 }} />
              <Statistic title="Rest days" value={lastGen.restDaysSkipped} valueStyle={{ fontSize: 18 }} />
            </Space>
          }
        />
      )}

      <Card title={selectedPatternId
        ? `Assignments on ${patterns.find((p) => p.id === selectedPatternId)?.code ?? ''}`
        : 'Pick a pattern to see its assignments'}>
        <Table
          rowKey="id"
          columns={assignmentCols}
          dataSource={currentAssignments}
          size="small"
          pagination={false}
          locale={{ emptyText: <Empty description="No assignments on this pattern" /> }}
        />
      </Card>

      <Modal
        open={assignOpen}
        title="Assign pattern to employee"
        onCancel={() => setAssignOpen(false)}
        onOk={submitAssign}
        okText="Assign"
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="patternId" label="Pattern" rules={[{ required: true }]}>
            <Select
              options={patterns.map((p) => ({
                value: p.id,
                label: `${p.code} — ${p.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Pick an employee' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
              placeholder="Pick an employee"
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="startDate" label="Start date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label="End date (optional)"
                tooltip="Leave blank for an open-ended rotation.">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="anchorDayIndex" label="Start at cycle day (0-based)"
            tooltip="0 = start at the first day of the cycle. Use non-zero to stagger this employee within their team's rotation.">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={genOpen}
        title="Generate roster from patterns"
        onCancel={() => setGenOpen(false)}
        onOk={submitGenerate}
        okText="Generate"
        width={620}
      >
        <Form form={genForm} layout="vertical">
          <Form.Item name="range" label="Date range" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="employeeIds" label="Employees (optional)"
            tooltip="Leave blank to include every employee with an active assignment in this date range.">
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              placeholder="All employees with pattern assignments"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
              maxTagCount="responsive"
            />
          </Form.Item>
          <Form.Item name="overwriteExisting" label="Overwrite existing"
            valuePropName="checked"
            tooltip="When on, existing non-locked roster entries in the range are overwritten with the pattern's shift. Locked entries are always preserved.">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ── Page shell ───────────────────────────────────────────────────────────────

export function ShiftPatternsPage() {
  const [shifts, setShifts] = useState<Shift[]>([])
  const [editing, setEditing] = useState<PatternResponse | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [reload, setReload] = useState(0)
  const { message } = AntdApp.useApp()

  useEffect(() => {
    shiftsApi
      .list(true)
      .then(setShifts)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load shifts'))
  }, [message])

  const onOpen = (p: PatternResponse | null) => {
    setEditing(p)
    setEditorOpen(true)
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Shift patterns</Title>
      <Text type="secondary">
        Define rotating cycles (like "4-on / 3-off" or "DDD-NNN-OFF") and assign them to employees.
        Use the generator to fan-out RosterEntry rows for a date range in one click — locked entries
        are preserved, gaps are filled.
      </Text>
      <Tabs
        items={[
          { key: 'patterns', label: 'Patterns', children: <PatternListTab key={reload} onOpen={onOpen} /> },
          { key: 'generate', label: 'Assignments + generate', children: <GenerateTab /> },
        ]}
      />
      <PatternEditor
        open={editorOpen}
        pattern={editing}
        shifts={shifts}
        onClose={() => setEditorOpen(false)}
        onSaved={() => setReload((n) => n + 1)}
      />
    </Space>
  )
}
