// M110 — Roster + shift catalog page.
// HR + managers manage shifts and per-day employee assignments.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  ColorPicker,
  DatePicker,
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
  Switch,
  Table,
  Tabs,
  Tag,
  TimePicker,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  rosterApi,
  shiftsApi,
  type RosterEntryResponse,
  type RosterGrid,
  type Shift,
  type ShiftRequest,
} from '../api/roster'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
import {
  openShiftsApi,
  swapRequestsApi,
  SWAP_STATUS_COLOR,
  type OpenShift,
  type ShiftSwapRequest,
  type OpenShiftRequest as OpenShiftReq,
  type SwapRequestDto,
} from '../api/scheduling'
import { selfApi } from '../api/self'

const { Title, Text } = Typography

// ─── Shift catalog tab ───────────────────────────────────────────────────────

function ShiftCatalogTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)
  const canArchive = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [shifts, setShifts] = useState<Shift[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Shift | null>(null)
  const [form] = Form.useForm<{
    code: string
    name: string
    description?: string
    start: ReturnType<typeof dayjs>
    end: ReturnType<typeof dayjs>
    breakMinutes: number
    color?: string
    active: boolean
  }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    shiftsApi
      .list(activeOnly)
      .then(setShifts)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load shifts'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      breakMinutes: 60,
      active: true,
      color: '#1677ff',
      start: dayjs('08:00', 'HH:mm'),
      end: dayjs('16:00', 'HH:mm'),
    })
    setOpen(true)
  }

  const startEdit = (s: Shift) => {
    setEditing(s)
    form.setFieldsValue({
      code: s.code,
      name: s.name,
      description: s.description ?? undefined,
      start: dayjs(s.startTime, 'HH:mm:ss'),
      end: dayjs(s.endTime, 'HH:mm:ss'),
      breakMinutes: s.breakMinutes,
      color: s.color ?? undefined,
      active: s.active,
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const req: ShiftRequest = {
      code: v.code,
      name: v.name,
      description: v.description,
      startTime: v.start.format('HH:mm'),
      endTime: v.end.format('HH:mm'),
      breakMinutes: v.breakMinutes,
      color: v.color,
      active: v.active,
    }
    setSaving(true)
    try {
      if (editing) {
        await shiftsApi.update(editing.id, req)
        message.success('Shift updated')
      } else {
        await shiftsApi.create(req)
        message.success('Shift created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally { setSaving(false) }
  }

  const archive = async (s: Shift) => {
    try {
      await shiftsApi.archive(s.id)
      message.success('Shift archived')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Archive failed',
      )
    }
  }

  const cols: ColumnsType<Shift> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 110,
      render: (v, r) => <a onClick={() => canWrite && startEdit(r)}>{v}</a>,
    },
    {
      title: 'Name',
      render: (_, r) => (
        <Space>
          {r.color && (
            <div style={{
              width: 14, height: 14, borderRadius: 3, background: r.color,
              border: '1px solid #d9d9d9',
            }} />
          )}
          <Text strong>{r.name}</Text>
        </Space>
      ),
    },
    {
      title: 'Window',
      width: 200,
      render: (_, r) => (
        <Text>
          {r.startTime.slice(0, 5)} → {r.endTime.slice(0, 5)}
          {r.crossesMidnight && <Tag color="purple" style={{ marginLeft: 8 }}>overnight</Tag>}
        </Text>
      ),
    },
    {
      title: 'Break',
      dataIndex: 'breakMinutes',
      width: 80,
      align: 'right',
      render: (v: number) => `${v}m`,
    },
    {
      title: 'Duration',
      dataIndex: 'durationMinutes',
      width: 110,
      align: 'right',
      render: (v: number) => `${Math.floor(v / 60)}h ${v % 60}m`,
    },
    {
      title: 'Status',
      width: 100,
      align: 'center',
      render: (_, r) => r.active
        ? <Tag color="green">Active</Tag>
        : <Tag>Archived</Tag>,
    },
    {
      title: '',
      width: 110,
      render: (_, r) => canArchive && r.active ? (
        <Popconfirm title="Archive this shift?" onConfirm={() => archive(r)}>
          <Button size="small" danger>Archive</Button>
        </Popconfirm>
      ) : null,
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
          unCheckedChildren="All shifts"
        />
        {canWrite && <Button type="primary" onClick={startCreate}>New shift…</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={shifts}
          size="small"
          pagination={false}
          locale={{ emptyText: <Empty description="No shifts" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit shift — ${editing.code}` : 'New shift'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        okText={editing ? 'Save' : 'Create'}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 40 }]}>
                <Input placeholder="MORNING" />
              </Form.Item>
            </Col>
            <Col span={14}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Morning" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="start" label="Start" rules={[{ required: true }]}>
                <TimePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="end" label="End" rules={[{ required: true }]}
                tooltip="If end is before start, the shift crosses midnight (e.g. 22:00 → 06:00).">
                <TimePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="breakMinutes" label="Break (min)" rules={[{ required: true }]}>
                <InputNumber min={0} max={1439} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="color"
                label="Calendar color"
                getValueFromEvent={(c) => typeof c === 'string' ? c : c?.toHexString?.()}
              >
                <ColorPicker showText defaultFormat="hex" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Roster grid tab ─────────────────────────────────────────────────────────

function RosterGridTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_WRITE)

  const [shifts, setShifts] = useState<Shift[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([])
  const [weekStart, setWeekStart] = useState(dayjs().startOf('week'))
  const [grid, setGrid] = useState<RosterGrid | null>(null)
  const [loading, setLoading] = useState(false)
  const [picker, setPicker] = useState<{ employeeId: string; date: string } | null>(null)
  const [pickedShiftId, setPickedShiftId] = useState<string | undefined>()

  const weekEnd = useMemo(() => weekStart.add(6, 'day'), [weekStart])

  // Load shifts + employees on mount.
  useEffect(() => {
    Promise.all([
      shiftsApi.list(true),
      employeesApi.list({ size: 200 }),
    ])
      .then(([s, e]) => {
        setShifts(s)
        setEmployees(e.content)
        // default selection: first 10
        setSelectedEmployees(e.content.slice(0, 10).map((emp) => emp.id))
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
  }, [message])

  // Load grid whenever week or selection changes.
  useEffect(() => {
    if (selectedEmployees.length === 0) {
      setGrid({ from: weekStart.format('YYYY-MM-DD'), to: weekEnd.format('YYYY-MM-DD'), employees: [] })
      return
    }
    setLoading(true)
    rosterApi
      .grid(selectedEmployees, weekStart.format('YYYY-MM-DD'), weekEnd.format('YYYY-MM-DD'))
      .then(setGrid)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load grid'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line
  }, [weekStart.format('YYYY-MM-DD'), selectedEmployees.join(','), message])

  const days = useMemo(() => {
    const out: dayjs.Dayjs[] = []
    for (let i = 0; i < 7; i++) out.push(weekStart.add(i, 'day'))
    return out
  }, [weekStart])

  const findEntry = (gridRow: RosterGrid['employees'][number], date: string): RosterEntryResponse | undefined =>
    gridRow.entries.find((e) => e.rosterDate === date)

  const submitAssign = async () => {
    if (!picker || !pickedShiftId) return
    try {
      await rosterApi.assign({
        employeeId: picker.employeeId,
        shiftId: pickedShiftId,
        rosterDate: picker.date,
      })
      message.success('Shift assigned')
      setPicker(null)
      setPickedShiftId(undefined)
      // refresh
      const fresh = await rosterApi.grid(
        selectedEmployees, weekStart.format('YYYY-MM-DD'), weekEnd.format('YYYY-MM-DD'))
      setGrid(fresh)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Assign failed',
      )
    }
  }

  const removeEntry = async (entry: RosterEntryResponse) => {
    try {
      await rosterApi.remove(entry.id)
      message.success('Shift cleared')
      const fresh = await rosterApi.grid(
        selectedEmployees, weekStart.format('YYYY-MM-DD'), weekEnd.format('YYYY-MM-DD'))
      setGrid(fresh)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Clear failed',
      )
    }
  }

  const employeeOptions = employees.map((e) => ({
    value: e.id,
    label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
  }))

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card size="small">
        <Row gutter={12} align="middle">
          <Col flex="auto">
            <Space>
              <Button onClick={() => setWeekStart(weekStart.subtract(1, 'week'))}>← Prev week</Button>
              <DatePicker
                value={weekStart}
                onChange={(d) => d && setWeekStart(d.startOf('week'))}
                picker="week"
                style={{ width: 200 }}
              />
              <Button onClick={() => setWeekStart(weekStart.add(1, 'week'))}>Next week →</Button>
              <Text strong>
                {weekStart.format('MMM D')} – {weekEnd.format('MMM D, YYYY')}
              </Text>
            </Space>
          </Col>
        </Row>
        <Row style={{ marginTop: 12 }}>
          <Col span={24}>
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              placeholder="Pick employees to roster"
              value={selectedEmployees}
              onChange={setSelectedEmployees}
              options={employeeOptions}
              style={{ width: '100%' }}
              maxTagCount="responsive"
            />
          </Col>
        </Row>
      </Card>

      {loading ? <Spin /> : grid && grid.employees.length === 0 ? (
        <Empty description="Pick at least one employee to render the grid." />
      ) : (
        <Card style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ ...cellHeader, textAlign: 'left', minWidth: 180 }}>Employee</th>
                {days.map((d) => (
                  <th key={d.format('YYYY-MM-DD')}
                      style={{
                        ...cellHeader,
                        background: d.day() === 0 || d.day() === 6 ? '#fafafa' : undefined,
                      }}>
                    <div>{d.format('ddd')}</div>
                    <Text type="secondary" style={{ fontSize: 11 }}>{d.format('MMM D')}</Text>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid?.employees.map((row) => (
                <tr key={row.employeeId}>
                  <td style={cell}>
                    <Text strong>{row.employeeName ?? '—'}</Text>
                  </td>
                  {days.map((d) => {
                    const date = d.format('YYYY-MM-DD')
                    const entry = findEntry(row, date)
                    return (
                      <td key={date}
                          style={{
                            ...cell,
                            background: d.day() === 0 || d.day() === 6 ? '#fafafa' : undefined,
                            cursor: canEdit ? 'pointer' : 'default',
                          }}
                          onClick={() => {
                            if (!canEdit) return
                            if (!entry) {
                              setPickedShiftId(undefined)
                              setPicker({ employeeId: row.employeeId, date })
                            }
                          }}>
                        {entry ? (
                          <Tooltip
                            title={
                              <>
                                <div>{entry.shiftName}</div>
                                <div>{entry.shiftStart?.slice(0, 5)} → {entry.shiftEnd?.slice(0, 5)}</div>
                                {entry.locked && <div>🔒 Locked</div>}
                              </>
                            }
                          >
                            <div
                              style={{
                                background: entry.shiftColor ?? '#1677ff',
                                color: 'white',
                                padding: '4px 8px',
                                borderRadius: 4,
                                fontSize: 12,
                                fontWeight: 600,
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                              }}
                              onClick={(e) => {
                                e.stopPropagation()
                                if (!canEdit) return
                                setPickedShiftId(entry.shiftId)
                                setPicker({ employeeId: row.employeeId, date })
                              }}
                            >
                              <span>{entry.shiftCode}</span>
                              {!entry.locked && canEdit && (
                                <Popconfirm
                                  title="Clear this shift?"
                                  onConfirm={(ev) => { ev?.stopPropagation(); void removeEntry(entry) }}
                                  onCancel={(ev) => ev?.stopPropagation()}
                                >
                                  <span
                                    onClick={(ev) => ev.stopPropagation()}
                                    style={{ marginLeft: 6, opacity: 0.7, cursor: 'pointer' }}
                                  >×</span>
                                </Popconfirm>
                              )}
                            </div>
                          </Tooltip>
                        ) : canEdit ? (
                          <Text type="secondary" style={{ fontSize: 12 }}>+ assign</Text>
                        ) : (
                          <Text type="secondary">—</Text>
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <Modal
        open={!!picker}
        title={picker ? `Assign shift — ${dayjs(picker.date).format('ddd, MMM D')}` : ''}
        onCancel={() => { setPicker(null); setPickedShiftId(undefined) }}
        onOk={submitAssign}
        okText="Assign"
        okButtonProps={{ disabled: !pickedShiftId }}
      >
        <Select
          style={{ width: '100%' }}
          placeholder="Pick a shift"
          value={pickedShiftId}
          onChange={setPickedShiftId}
          options={shifts.map((s) => ({
            value: s.id,
            label: (
              <Space>
                {s.color && (
                  <div style={{ width: 12, height: 12, borderRadius: 2, background: s.color }} />
                )}
                <span>{s.code} — {s.startTime.slice(0, 5)} → {s.endTime.slice(0, 5)}</span>
              </Space>
            ),
          }))}
        />
      </Modal>
    </Space>
  )
}

const cellHeader: React.CSSProperties = {
  border: '1px solid #f0f0f0',
  padding: '8px 10px',
  background: '#fafbfc',
  textAlign: 'center',
  fontWeight: 600,
}

const cell: React.CSSProperties = {
  border: '1px solid #f0f0f0',
  padding: '6px 8px',
  minWidth: 100,
  height: 56,
  verticalAlign: 'middle',
}

// ─── Open Shifts tab (M482) ──────────────────────────────────────────────────

function OpenShiftsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [items, setItems] = useState<OpenShift[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<OpenShiftReq>()
  const [saving, setSaving] = useState(false)
  const [shifts, setShifts] = useState<Shift[]>([])
  const [profile, setProfile] = useState<any>(null)

  const load = () => {
    setLoading(true)
    openShiftsApi.list()
      .then(setItems)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load open shifts'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    shiftsApi.list(true).then(setShifts).catch(() => {})
    selfApi.profile().then(setProfile).catch(() => {})
  }, [])

  const startCreate = () => {
    form.resetFields()
    form.setFieldsValue({ slots: 1, shiftDate: dayjs().format('YYYY-MM-DD') as any })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    const payload: OpenShiftReq = {
      shiftId: values.shiftId,
      shiftDate: typeof values.shiftDate === 'string' ? values.shiftDate : dayjs(values.shiftDate).format('YYYY-MM-DD'),
      orgUnitId: values.orgUnitId,
      slots: values.slots,
      notes: values.notes,
    }
    setSaving(true)
    openShiftsApi.create(payload)
      .then(() => {
        message.success('Open shift created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to create'))
      .finally(() => setSaving(false))
  }

  const handleClaim = (id: string) => {
    if (!profile?.employeeId) {
      message.error('Unable to resolve your employee ID')
      return
    }
    openShiftsApi.claim(id, profile.employeeId)
      .then(() => {
        message.success('Shift claimed')
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to claim'))
  }

  const columns: ColumnsType<OpenShift> = [
    {
      title: 'Date',
      dataIndex: 'shiftDate',
      key: 'shiftDate',
      render: d => dayjs(d).format('YYYY-MM-DD (ddd)'),
    },
    { title: 'Shift', dataIndex: 'shiftId', key: 'shiftId', render: id => id.slice(0, 8) },
    { title: 'Slots', dataIndex: 'slots', key: 'slots', align: 'center' },
    { title: 'Filled', dataIndex: 'filled', key: 'filled', align: 'center' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: s => <Tag color={s === 'OPEN' ? 'green' : 'default'}>{s}</Tag>,
    },
    { title: 'Notes', dataIndex: 'notes', key: 'notes', render: n => n || '—' },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => rec.status === 'OPEN' && (
        <Button size="small" type="link" onClick={() => handleClaim(rec.id)}>Claim</Button>
      ),
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="end">
          {canWrite && (
            <Button type="primary" onClick={startCreate}>Create Open Shift</Button>
          )}
        </Row>
        <Table
          dataSource={items}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Modal
        title="Create Open Shift"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Shift" name="shiftId" rules={[{ required: true }]}>
            <Select
              showSearch
              placeholder="Select shift"
              filterOption={(input, opt) => String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())}
            >
              {shifts.map(s => (
                <Select.Option key={s.id} value={s.id} label={s.name}>
                  {s.code} — {s.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="Date" name="shiftDate" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Slots" name="slots" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Org Unit (optional)" name="orgUnitId">
            <Input />
          </Form.Item>
          <Form.Item label="Notes" name="notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ─── Swap Requests tab (M482) ────────────────────────────────────────────────

function SwapRequestsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [requests, setRequests] = useState<ShiftSwapRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string | undefined>('PENDING')
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<SwapRequestDto>()
  const [saving, setSaving] = useState(false)
  const [profile, setProfile] = useState<any>(null)
  const [myRosterEntries, setMyRosterEntries] = useState<RosterEntryResponse[]>([])

  const load = () => {
    setLoading(true)
    swapRequestsApi.list(statusFilter)
      .then(setRequests)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load swap requests'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [statusFilter])

  useEffect(() => {
    selfApi.profile().then(p => {
      setProfile(p)
      if (p?.id) {
        rosterApi.forEmployee(p.id, dayjs().subtract(7, 'day').format('YYYY-MM-DD'), dayjs().add(14, 'day').format('YYYY-MM-DD'))
          .then(setMyRosterEntries).catch(() => {})
      }
    }).catch(() => {})
  }, [])

  const startCreate = () => {
    form.resetFields()
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    swapRequestsApi.create(values)
      .then(() => {
        message.success('Swap request created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to create'))
      .finally(() => setSaving(false))
  }

  const handleApprove = (id: string) => {
    swapRequestsApi.approve(id)
      .then(() => {
        message.success('Swap approved')
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to approve'))
  }

  const handleReject = (id: string, reason: string) => {
    if (!reason) {
      message.error('Reason is required')
      return
    }
    swapRequestsApi.reject(id, reason)
      .then(() => {
        message.success('Swap rejected')
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to reject'))
  }

  const columns: ColumnsType<ShiftSwapRequest> = [
    { title: 'No.', dataIndex: 'requestNo', key: 'requestNo' },
    { title: 'From', dataIndex: 'fromEmployeeId', key: 'fromEmployeeId', render: id => id.slice(0, 8) },
    { title: 'To', dataIndex: 'toEmployeeId', key: 'toEmployeeId', render: id => id.slice(0, 8) },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: s => <Tag color={SWAP_STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Requested',
      dataIndex: 'requestedAt',
      key: 'requestedAt',
      render: t => dayjs(t).format('YYYY-MM-DD HH:mm'),
    },
    { title: 'Notes', dataIndex: 'notes', key: 'notes', render: n => n || '—' },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => {
        const isRequester = profile?.employeeId === rec.fromEmployeeId
        return canWrite && !isRequester && rec.status === 'PENDING' && (
          <Space>
            <Button size="small" type="link" onClick={() => handleApprove(rec.id)}>Approve</Button>
            <Popconfirm
              title="Reject swap"
              description={<Input.TextArea placeholder="Reason" rows={2} onChange={e => (rec as any)._reason = e.target.value} />}
              onConfirm={() => handleReject(rec.id, (rec as any)._reason || '')}
            >
              <Button size="small" type="link" danger>Reject</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <Text>Status:</Text>
              <Select value={statusFilter} onChange={setStatusFilter} style={{ width: 150 }} allowClear placeholder="All">
                <Select.Option value="PENDING">Pending</Select.Option>
                <Select.Option value="APPROVED">Approved</Select.Option>
                <Select.Option value="REJECTED">Rejected</Select.Option>
              </Select>
            </Space>
          </Col>
          <Col>
            <Button type="primary" onClick={startCreate}>Request Swap</Button>
          </Col>
        </Row>
        <Table
          dataSource={requests}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Modal
        title="Request Shift Swap"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="My Roster Entry" name="rosterEntryId" rules={[{ required: true }]}>
            <Select placeholder="Select your shift to swap">
              {myRosterEntries.map(e => (
                <Select.Option key={e.id} value={e.id}>
                  {e.shiftName || e.shiftCode || 'Shift'} — {e.id.slice(0, 8)}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="Swap With Employee" name="toEmployeeId" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Form.Item label="Notes" name="notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ─── Page shell ──────────────────────────────────────────────────────────────

export function RosterPage() {
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Shift roster</Title>
      <Text type="secondary">
        Define shifts and assign them to employees by day. Click an empty cell to assign,
        the small × to clear. Locked entries (after publish) require the swap flow to change.
      </Text>
      <Tabs
        items={[
          { key: 'grid', label: 'Weekly roster', children: <RosterGridTab /> },
          { key: 'shifts', label: 'Shift catalog', children: <ShiftCatalogTab /> },
          { key: 'open-shifts', label: 'Open Shifts', children: <OpenShiftsTab /> },
          { key: 'swap-requests', label: 'Swap Requests', children: <SwapRequestsTab /> },
        ]}
      />
    </Space>
  )
}
