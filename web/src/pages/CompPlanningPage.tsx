// M118 — Compensation planning workbench.
//
// HR Admin manages cycles (open / close, set budget pool).
// Managers + HR open the Workbench tab to propose raises against the
// current pool, watching the budget meter as they go. HR_ADMIN approves
// or rejects each submitted proposal; approval writes through to
// EmployeeCompensation via the existing M40 effective-dated machinery.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Slider,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  compPlanningApi,
  type AllocationRow,
  type CompCycleStatus,
  type CycleRequest,
  type CycleResponse,
  type DecisionRequest,
  type ProposalResponse,
  type SimulationRequest,
  type SimulationResponse,
  type TeamGrid,
  type TeamRow,
} from '../api/compPlanning'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<CompCycleStatus, string> = {
  DRAFT: 'default',
  OPEN: 'green',
  CLOSED: 'blue',
  CANCELLED: 'red',
}

function fmt(n?: number | null): string {
  if (n == null) return '—'
  return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ─── Cycles tab ──────────────────────────────────────────────────────────────

function CyclesTab({ onPick }: { onPick: (id: string) => void }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycles, setCycles] = useState<CycleResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<CycleResponse | null>(null)
  const [form] = Form.useForm<{
    code: string
    name: string
    description?: string
    window: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
    poolTotal?: number
    currency?: string
  }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    compPlanningApi
      .listCycles()
      .then(setCycles)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cycles'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])  // eslint-disable-line

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      window: [dayjs(), dayjs().add(30, 'day')],
      currency: 'AZN',
      poolTotal: 0,
    })
    setOpen(true)
  }

  const startEdit = (c: CycleResponse) => {
    setEditing(c)
    form.setFieldsValue({
      code: c.code,
      name: c.name,
      description: c.description ?? undefined,
      window: [dayjs(c.opensOn), dayjs(c.closesOn)],
      poolTotal: c.poolTotal,
      currency: c.currency,
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const req: CycleRequest = {
      code: v.code,
      name: v.name,
      description: v.description,
      opensOn: v.window[0].format('YYYY-MM-DD'),
      closesOn: v.window[1].format('YYYY-MM-DD'),
      poolTotal: v.poolTotal ?? 0,
      currency: v.currency || 'AZN',
    }
    setSaving(true)
    try {
      if (editing) {
        await compPlanningApi.updateCycle(editing.id, req)
        message.success('Cycle updated')
      } else {
        await compPlanningApi.createCycle(req)
        message.success('Cycle created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const change = async (id: string, status: CompCycleStatus) => {
    try {
      await compPlanningApi.changeStatus(id, status)
      message.success(`Status changed to ${status}`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Status change failed',
      )
    }
  }

  const cols: ColumnsType<CycleResponse> = [
    {
      title: 'Cycle',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <a onClick={() => onPick(r.id)}>{r.name}</a>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.code}</Text>
        </Space>
      ),
    },
    {
      title: 'Window',
      width: 200,
      render: (_, r) => (
        <Text style={{ fontSize: 12 }}>
          {dayjs(r.opensOn).format('MMM D')} – {dayjs(r.closesOn).format('MMM D, YYYY')}
        </Text>
      ),
    },
    {
      title: 'Status',
      width: 100,
      align: 'center',
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    {
      title: 'Pool',
      width: 200,
      render: (_, r) => (
        <Space direction="vertical" size={0} style={{ width: '100%' }}>
          <Text style={{ fontSize: 12 }}>
            {fmt(r.poolCommitted)} / {fmt(r.poolTotal)} {r.currency}
          </Text>
          <Progress
            percent={r.poolTotal > 0
              ? Math.min(100, Math.round((r.poolCommitted / r.poolTotal) * 100))
              : 0}
            size="small"
            status={r.poolCommitted > r.poolTotal ? 'exception' : 'active'}
            format={() => ''}
          />
        </Space>
      ),
    },
    {
      title: 'Proposals',
      dataIndex: 'proposalCount',
      width: 100,
      align: 'right',
      render: (v: number) => v === 0 ? <Text type="secondary">—</Text> : <Tag color="blue">{v}</Tag>,
    },
    {
      title: '',
      width: 280,
      render: (_, r) => canWrite ? (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <>
              <Button size="small" onClick={() => startEdit(r)}>Edit</Button>
              <Button size="small" type="primary" onClick={() => change(r.id, 'OPEN')}>
                Open
              </Button>
            </>
          )}
          {r.status === 'OPEN' && (
            <Popconfirm title="Close this cycle? No more edits after." onConfirm={() => change(r.id, 'CLOSED')}>
              <Button size="small">Close</Button>
            </Popconfirm>
          )}
          {(r.status === 'DRAFT' || r.status === 'OPEN') && (
            <Popconfirm title="Cancel this cycle?" onConfirm={() => change(r.id, 'CANCELLED')}>
              <Button size="small" danger>Cancel</Button>
            </Popconfirm>
          )}
        </Space>
      ) : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
        {canWrite && <Button type="primary" onClick={startCreate}>New cycle…</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={cycles}
          size="small"
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: <Empty description="No cycles yet" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit cycle — ${editing.code}` : 'New cycle'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        okText={editing ? 'Save' : 'Create'}
        width={620}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true }]}>
                <Input placeholder="MERIT-2026" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="2026 annual merit cycle" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description (optional)">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={14}>
              <Form.Item name="window" label="Window" rules={[{ required: true }]}>
                <DatePicker.RangePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="poolTotal" label="Pool total (monthly delta)">
                <InputNumber min={0} step={1000} style={{ width: '100%' }} precision={2} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="currency" label="Currency">
                <Input placeholder="AZN" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Workbench tab ───────────────────────────────────────────────────────────

function WorkbenchTab({ initialCycleId }: { initialCycleId?: string }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canPropose = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)
  const canApprove = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycles, setCycles] = useState<CycleResponse[]>([])
  const [selectedCycleId, setSelectedCycleId] = useState<string | undefined>(initialCycleId)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([])
  const [grid, setGrid] = useState<TeamGrid | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<{ row: TeamRow; cycleId: string } | null>(null)
  const [editForm] = Form.useForm<{ proposedSalary: number; rationale?: string; effectiveOn?: ReturnType<typeof dayjs> }>()
  const [decideOpen, setDecideOpen] = useState<ProposalResponse | null>(null)
  const [decideForm] = Form.useForm<{ decision: 'APPROVED' | 'REJECTED'; note?: string; effectiveOn?: ReturnType<typeof dayjs> }>()

  useEffect(() => {
    Promise.all([
      compPlanningApi.listCycles(),
      employeesApi.list({ size: 500 }),
    ])
      .then(([c, e]) => {
        setCycles(c)
        setEmployees(e.content)
        if (!selectedCycleId && c.length > 0) {
          const firstOpen = c.find((cy) => cy.status === 'OPEN')
          setSelectedCycleId((firstOpen ?? c[0]).id)
        }
        // Default selection: every employee — caller can narrow.
        setSelectedEmployees(e.content.slice(0, 50).map((emp) => emp.id))
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line
  }, [])

  useEffect(() => {
    if (!selectedCycleId || selectedEmployees.length === 0) {
      setGrid(null)
      return
    }
    compPlanningApi
      .teamGrid(selectedCycleId, selectedEmployees)
      .then(setGrid)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load team grid'))
  }, [selectedCycleId, selectedEmployees.join(','), message])  // eslint-disable-line

  const reload = async () => {
    if (!selectedCycleId) return
    const [cs, g] = await Promise.all([
      compPlanningApi.listCycles(),
      compPlanningApi.teamGrid(selectedCycleId, selectedEmployees),
    ])
    setCycles(cs)
    setGrid(g)
  }

  const openEditor = (row: TeamRow) => {
    if (!selectedCycleId) return
    setEditing({ row, cycleId: selectedCycleId })
    const startSalary = row.proposal?.proposedSalary ?? row.currentSalary
    editForm.setFieldsValue({
      proposedSalary: startSalary,
      rationale: row.proposal?.rationale ?? undefined,
      effectiveOn: row.proposal?.effectiveOn ? dayjs(row.proposal.effectiveOn) : undefined,
    })
  }

  const submitEditor = async () => {
    if (!editing) return
    const v = await editForm.validateFields()
    try {
      await compPlanningApi.upsertProposal({
        cycleId: editing.cycleId,
        employeeId: editing.row.employeeId,
        proposedSalary: v.proposedSalary,
        rationale: v.rationale,
        effectiveOn: v.effectiveOn ? v.effectiveOn.format('YYYY-MM-DD') : undefined,
      })
      message.success('Proposal saved')
      setEditing(null)
      reload()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    }
  }

  const submitForDecision = async (proposalId: string) => {
    try {
      await compPlanningApi.submitProposal(proposalId)
      message.success('Proposal submitted')
      reload()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Submit failed',
      )
    }
  }

  const openDecide = (p: ProposalResponse) => {
    decideForm.setFieldsValue({
      decision: 'APPROVED',
      effectiveOn: p.effectiveOn ? dayjs(p.effectiveOn) : dayjs().add(1, 'day'),
    })
    setDecideOpen(p)
  }

  const submitDecide = async () => {
    if (!decideOpen) return
    const v = await decideForm.validateFields()
    const req: DecisionRequest = {
      decision: v.decision,
      note: v.note,
      effectiveOn: v.effectiveOn?.format('YYYY-MM-DD'),
    }
    try {
      await compPlanningApi.decideProposal(decideOpen.id, req)
      message.success(`Proposal ${v.decision.toLowerCase()}`)
      setDecideOpen(null)
      reload()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Decision failed',
      )
    }
  }

  const cycle = useMemo(() => cycles.find((c) => c.id === selectedCycleId), [cycles, selectedCycleId])
  const cycleIsOpen = cycle?.status === 'OPEN'

  const cols: ColumnsType<TeamRow> = [
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.employeeName}</Text>
          {r.employeeNo && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
          )}
          {r.orgUnitLabel && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.orgUnitLabel}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Current',
      width: 130,
      align: 'right',
      render: (_, r) => <Text>{fmt(r.currentSalary)} {r.currency}</Text>,
    },
    {
      title: 'Proposed',
      width: 130,
      align: 'right',
      render: (_, r) => r.proposal
        ? <Text strong>{fmt(r.proposal.proposedSalary)}</Text>
        : <Text type="secondary">—</Text>,
    },
    {
      title: 'Δ',
      width: 100,
      align: 'right',
      render: (_, r) => r.proposal ? (
        <Space direction="vertical" size={0}>
          <Text style={{ color: r.proposal.deltaAmount > 0 ? '#52c41a'
            : r.proposal.deltaAmount < 0 ? '#ff4d4f' : undefined }}>
            {r.proposal.deltaAmount >= 0 ? '+' : ''}{fmt(r.proposal.deltaAmount)}
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {r.proposal.increasePercent != null
              ? `${r.proposal.increasePercent.toFixed(1)}%` : ''}
          </Text>
        </Space>
      ) : <Text type="secondary">—</Text>,
    },
    {
      title: 'Status',
      width: 110,
      render: (_, r) => r.proposal ? (
        <Tag color={
          r.proposal.status === 'APPROVED' ? 'green'
          : r.proposal.status === 'REJECTED' ? 'red'
          : r.proposal.status === 'SUBMITTED' ? 'blue' : 'default'
        }>{r.proposal.status}</Tag>
      ) : <Text type="secondary">—</Text>,
    },
    {
      title: '',
      width: 240,
      render: (_, r) => {
        if (!r.proposal) {
          return canPropose && cycleIsOpen ? (
            <Button size="small" onClick={() => openEditor(r)}>+ Propose</Button>
          ) : null
        }
        return (
          <Space size={4}>
            {canPropose && r.proposal.status === 'DRAFT' && (
              <>
                <Button size="small" onClick={() => openEditor(r)}>Edit</Button>
                <Button size="small" type="primary" onClick={() => submitForDecision(r.proposal!.id)}>
                  Submit
                </Button>
              </>
            )}
            {canApprove && r.proposal.status === 'SUBMITTED' && (
              <Button size="small" type="primary" onClick={() => openDecide(r.proposal!)}>
                Decide
              </Button>
            )}
          </Space>
        )
      },
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card size="small">
        <Row gutter={12} align="middle">
          <Col span={10}>
            <Text strong>Cycle:</Text>
            <Select
              style={{ width: '100%' }}
              value={selectedCycleId}
              onChange={setSelectedCycleId}
              options={cycles.map((c) => ({
                value: c.id,
                label: `${c.name} (${c.status})`,
              }))}
              placeholder="Pick a cycle"
            />
          </Col>
          <Col span={14}>
            <Text strong>Employees:</Text>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              showSearch
              optionFilterProp="label"
              value={selectedEmployees}
              onChange={setSelectedEmployees}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
              maxTagCount="responsive"
            />
          </Col>
        </Row>
      </Card>

      {grid && (
        <Row gutter={16}>
          <Col span={6}>
            <Card>
              <Statistic
                title="Pool total"
                value={grid.poolTotal}
                precision={2}
                suffix={grid.currency}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="Committed"
                value={grid.poolCommitted}
                precision={2}
                suffix={grid.currency}
                valueStyle={{
                  color: grid.poolCommitted > grid.poolTotal ? '#ff4d4f' : undefined,
                }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="Remaining"
                value={grid.poolRemaining}
                precision={2}
                suffix={grid.currency}
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Progress
                type="circle"
                size={70}
                percent={grid.poolTotal > 0
                  ? Math.min(100, Math.round((grid.poolCommitted / grid.poolTotal) * 100))
                  : 0}
                status={grid.poolCommitted > grid.poolTotal ? 'exception' : 'active'}
              />
            </Card>
          </Col>
        </Row>
      )}

      {grid?.poolCommitted != null && grid.poolCommitted > grid.poolTotal && (
        <Alert
          type="warning"
          showIcon
          message="Pool exceeded"
          description={`Committed (${fmt(grid.poolCommitted)} ${grid.currency}) is over the pool total (${fmt(grid.poolTotal)} ${grid.currency}). HR may decline some proposals.`}
        />
      )}

      <Card>
        <Table
          rowKey={(r) => r.employeeId}
          columns={cols}
          dataSource={grid?.rows ?? []}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="Pick a cycle and at least one employee." /> }}
        />
      </Card>

      <Modal
        open={!!editing}
        title={editing ? `Propose for ${editing.row.employeeName}` : ''}
        onCancel={() => setEditing(null)}
        onOk={submitEditor}
        okText="Save"
      >
        {editing && (
          <Form form={editForm} layout="vertical">
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={`Current: ${fmt(editing.row.currentSalary)} ${editing.row.currency}`}
            />
            <Form.Item name="proposedSalary" label="Proposed monthly salary"
              rules={[{ required: true }]}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="effectiveOn" label="Effective date (optional)">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="rationale" label="Rationale">
              <Input.TextArea rows={3} placeholder="Why this raise?" />
            </Form.Item>
          </Form>
        )}
      </Modal>

      <Modal
        open={!!decideOpen}
        title={decideOpen ? `Decide on ${decideOpen.employeeName}` : ''}
        onCancel={() => setDecideOpen(null)}
        onOk={submitDecide}
        okText="Apply"
      >
        {decideOpen && (
          <Form form={decideForm} layout="vertical">
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={
                <>
                  {fmt(decideOpen.currentSalary)} → <b>{fmt(decideOpen.proposedSalary)}</b>{' '}
                  ({decideOpen.increasePercent?.toFixed(1)}%, +{fmt(decideOpen.deltaAmount)})
                </>
              }
            />
            <Form.Item name="decision" label="Decision">
              <Select options={[
                { value: 'APPROVED', label: 'Approve' },
                { value: 'REJECTED', label: 'Reject' },
              ]} />
            </Form.Item>
            <Form.Item name="effectiveOn" label="Effective date (on approve)">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="note" label="Note">
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Space>
  )
}

// ─── Simulator tab (M129) ────────────────────────────────────────────────────
//
// HR slides the weighting knobs (perf / tenure / base) plus a floor and a
// cap, and the backend runs the pure-static `BonusSimulator` over the
// cycle's proposal cohort. Output is a per-employee allocation table
// with score, weight, allocated bonus, and % of base.

function SimulatorTab({ initialCycleId }: { initialCycleId?: string }) {
  const { message } = AntdApp.useApp()

  const [cycles, setCycles] = useState<CycleResponse[]>([])
  const [selectedCycleId, setSelectedCycleId] = useState<string | undefined>(initialCycleId)
  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<SimulationResponse | null>(null)

  // Default knobs match the backend service defaults so SPA + service
  // agree on what "leave it alone" means.
  const [poolOverride, setPoolOverride] = useState<number | null>(null)
  const [perfWeight, setPerfWeight] = useState(50)
  const [tenWeight, setTenWeight] = useState(20)
  const [baseWeight, setBaseWeight] = useState(30)
  const [floorPct, setFloorPct] = useState(0)
  const [capPct, setCapPct] = useState(0)

  useEffect(() => {
    compPlanningApi.listCycles()
      .then((cs) => {
        setCycles(cs)
        if (!selectedCycleId && cs.length > 0) {
          const firstOpen = cs.find((c) => c.status === 'OPEN' || c.status === 'CLOSED')
          setSelectedCycleId((firstOpen ?? cs[0]).id)
        }
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cycles'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line
  }, [])

  const cycle = useMemo(() => cycles.find((c) => c.id === selectedCycleId), [cycles, selectedCycleId])

  const run = async () => {
    if (!selectedCycleId) return
    const req: SimulationRequest = {
      poolTotal: poolOverride && poolOverride > 0 ? poolOverride : null,
      performanceWeight: perfWeight / 100,
      tenureWeight:      tenWeight  / 100,
      baseWeight:        baseWeight / 100,
      floorPercent:      floorPct,
      capPercent:        capPct,
    }
    setRunning(true)
    try {
      const r = await compPlanningApi.simulate(selectedCycleId, req)
      setResult(r)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Simulation failed',
      )
    } finally {
      setRunning(false)
    }
  }

  const cols: ColumnsType<AllocationRow> = [
    {
      title: 'Employee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.employeeName ?? r.employeeId}</Text>
          {r.employeeNo && <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>}
        </Space>
      ),
    },
    {
      title: 'Base',
      width: 110,
      align: 'right',
      render: (_, r) => <Text>{fmt(r.baseSalary)}</Text>,
    },
    {
      title: 'Rating',
      width: 80,
      align: 'right',
      render: (_, r) => r.performanceRating != null
        ? <Text>{r.performanceRating.toFixed(1)}</Text>
        : <Text type="secondary">—</Text>,
    },
    {
      title: 'Tenure',
      width: 80,
      align: 'right',
      render: (_, r) => <Text>{r.tenureMonths} mo</Text>,
    },
    {
      title: 'Score',
      width: 90,
      align: 'right',
      render: (_, r) => <Text>{r.score.toFixed(3)}</Text>,
    },
    {
      title: 'Weight',
      width: 90,
      align: 'right',
      render: (_, r) => <Text>{(r.weight * 100).toFixed(2)}%</Text>,
    },
    {
      title: 'Allocated',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <Text strong style={{ color: r.clamped ? '#fa8c16' : undefined }}>
          {fmt(r.allocatedBonus)}
        </Text>
      ),
    },
    {
      title: '% of base',
      width: 100,
      align: 'right',
      render: (_, r) => <Text>{r.percentOfBase.toFixed(2)}%</Text>,
    },
    {
      title: '',
      width: 80,
      align: 'center',
      render: (_, r) => r.clamped ? <Tag color="orange">clamped</Tag> : null,
    },
  ]

  if (loading) return <Spin />

  const weightSum = perfWeight + tenWeight + baseWeight
  const weightWarning = weightSum === 0
    ? 'All weights zero — pool will be split evenly.'
    : weightSum !== 100
      ? `Weights sum to ${weightSum}% (normalised at run time).`
      : null

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card size="small">
        <Row gutter={12} align="middle">
          <Col span={12}>
            <Text strong>Cycle:</Text>
            <Select
              style={{ width: '100%' }}
              value={selectedCycleId}
              onChange={(v) => { setSelectedCycleId(v); setResult(null) }}
              options={cycles.map((c) => ({
                value: c.id,
                label: `${c.name} (${c.status})`,
              }))}
              placeholder="Pick a cycle"
            />
          </Col>
          <Col span={8}>
            <Text strong>Pool override (optional):</Text>
            <InputNumber
              style={{ width: '100%' }}
              value={poolOverride ?? undefined}
              onChange={(v) => setPoolOverride(typeof v === 'number' ? v : null)}
              min={0}
              step={1000}
              precision={2}
              placeholder={cycle ? `Cycle default: ${fmt(cycle.poolTotal)} ${cycle.currency}` : ''}
            />
          </Col>
          <Col span={4} style={{ textAlign: 'right' }}>
            <Button type="primary" onClick={run} loading={running} disabled={!selectedCycleId}>
              Run simulation
            </Button>
          </Col>
        </Row>
      </Card>

      <Card title="Weighting & guard-rails" size="small">
        <Row gutter={16}>
          <Col span={8}>
            <Text>Performance weight</Text>
            <Slider min={0} max={100} value={perfWeight} onChange={setPerfWeight}
              marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Col>
          <Col span={8}>
            <Text>Tenure weight</Text>
            <Slider min={0} max={100} value={tenWeight} onChange={setTenWeight}
              marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Col>
          <Col span={8}>
            <Text>Base-salary weight</Text>
            <Slider min={0} max={100} value={baseWeight} onChange={setBaseWeight}
              marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Col>
        </Row>
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={8}>
            <Text>Floor (% of base)</Text>
            <Slider min={0} max={20} step={0.5} value={floorPct} onChange={setFloorPct}
              marks={{ 0: '0%', 5: '5%', 10: '10%', 20: '20%' }} />
          </Col>
          <Col span={8}>
            <Text>Cap (% of base)</Text>
            <Slider min={0} max={50} step={0.5} value={capPct} onChange={setCapPct}
              marks={{ 0: 'none', 10: '10%', 25: '25%', 50: '50%' }} />
          </Col>
          <Col span={8}>
            {weightWarning && <Alert type="info" showIcon message={weightWarning} />}
          </Col>
        </Row>
      </Card>

      {result && (
        <>
          <Row gutter={16}>
            <Col span={6}>
              <Card><Statistic title="Pool" value={result.poolTotal} precision={2} suffix={cycle?.currency} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Allocated" value={result.totalAllocated} precision={2}
                valueStyle={{ color: '#1677ff' }} suffix={cycle?.currency} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Residual" value={result.residual} precision={2}
                valueStyle={{ color: result.residual > 0 ? '#52c41a' : '#ff4d4f' }}
                suffix={cycle?.currency} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Clamped" value={result.clampedCount}
                suffix={`/ ${result.eligibleCount}`} /></Card>
            </Col>
          </Row>

          <Card>
            <Table
              rowKey={(r) => r.employeeId}
              columns={cols}
              dataSource={result.allocations}
              size="small"
              pagination={{ pageSize: 25 }}
              locale={{ emptyText: <Empty description="No eligible employees for this cycle." /> }}
              summary={() => (
                <Table.Summary fixed>
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0} colSpan={6}>
                      <Text strong>Totals</Text>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={6} align="right">
                      <Text strong>{fmt(result.totalAllocated)}</Text>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={7} colSpan={2} />
                  </Table.Summary.Row>
                </Table.Summary>
              )}
            />
          </Card>
        </>
      )}

      {!result && (
        <Card>
          <Empty description="Tune the knobs above, then press Run simulation." />
        </Card>
      )}
    </Space>
  )
}

// ─── Page shell ──────────────────────────────────────────────────────────────

export function CompPlanningPage() {
  const [tab, setTab] = useState('cycles')
  const [cycleId, setCycleId] = useState<string | undefined>()
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Compensation planning</Title>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: 'cycles',    label: 'Cycles',    children: (
            <CyclesTab onPick={(id) => { setCycleId(id); setTab('workbench') }} />
          ) },
          { key: 'workbench', label: 'Workbench', children: <WorkbenchTab initialCycleId={cycleId} /> },
          { key: 'simulator', label: 'Simulator', children: <SimulatorTab initialCycleId={cycleId} /> },
        ]}
      />
    </Space>
  )
}
