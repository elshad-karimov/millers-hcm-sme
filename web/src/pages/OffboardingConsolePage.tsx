import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, Drawer, Input, Progress, Row, Select, Space,
  Statistic, Table, Tag, Typography, message
} from 'antd'
import { CheckSquareOutlined, ClearOutlined, CommentOutlined, DeploymentUnitOutlined, DollarOutlined, InboxOutlined, LaptopOutlined, LogoutOutlined, UserDeleteOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import {
  getOffboardingOverview, listOffboardingCases, updateOffboardingCaseStatus,
  getOffboardingCaseChecklist, listClearances, signOffClearance,
  listAssetReturns, updateAssetReturn, listItAccess, updateItAccess,
  listHandoverTasks, createHandoverTask, updateHandoverTask,
  getExitInterview, saveExitInterview,
  getOrCreateSettlement, addSettlementComponent, deleteSettlementComponent,
  transitionSettlementStatus,
  type AssignmentResponse, type AssetReturnResponse, type AssetReturnStatus,
  type AssetReturnUpdateRequest, type ClearanceDepartment, type ClearanceResponse,
  type ClearanceStatus, type ExitInterviewRequest, type ExitInterviewResponse,
  type HandoverStatus, type HandoverTaskResponse,
  type ItAccessResponse, type ItAccessStatus, type ItAccessUpdateRequest,
  type OffboardingCaseResponse, type OffboardingCaseStatus,
  type OffboardingOverviewResponse, type PageResponse,
  type SettlementComponentRequest, type SettlementResponse, type SettlementStatus,
} from '../api/offboarding'
import { checklistsApi, TASK_STATUS_COLOR, type TaskStatusResponse } from '../api/checklists'

const { Title } = Typography

const CLEARANCE_STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  CLEARED: 'green',
  CLEARED_WITH_DEDUCTION: 'orange',
  NOT_CLEARED: 'red',
  WAIVED: 'purple',
}

const CLEARANCE_STATUSES: ClearanceStatus[] = [
  'NOT_STARTED', 'IN_PROGRESS', 'CLEARED', 'CLEARED_WITH_DEDUCTION', 'NOT_CLEARED', 'WAIVED',
]

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  IN_PROGRESS: 'blue',
  UNDER_NOTICE: 'geekblue',
  CLEARANCE_PENDING: 'orange',
  SETTLEMENT_PENDING: 'purple',
  COMPLETED: 'green',
  CANCELLED: 'default',
  REVERSED: 'red',
}

const SOURCE_COLOR: Record<string, string> = {
  RESIGNATION: 'blue',
  TERMINATION: 'red',
  OTHER: 'default',
}

const TRANSITIONS: Record<OffboardingCaseStatus, OffboardingCaseStatus[]> = {
  DRAFT: ['SUBMITTED'],
  SUBMITTED: ['PENDING_APPROVAL', 'CANCELLED'],
  PENDING_APPROVAL: ['APPROVED', 'CANCELLED'],
  APPROVED: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['UNDER_NOTICE', 'CLEARANCE_PENDING', 'CANCELLED'],
  UNDER_NOTICE: ['CLEARANCE_PENDING', 'CANCELLED'],
  CLEARANCE_PENDING: ['SETTLEMENT_PENDING', 'CANCELLED'],
  SETTLEMENT_PENDING: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
  REVERSED: [],
}

export function OffboardingConsolePage() {
  const [overview, setOverview] = useState<OffboardingOverviewResponse | null>(null)
  const [cases, setCases] = useState<PageResponse<OffboardingCaseResponse> | null>(null)
  const [loading, setLoading] = useState(false)
  const [advancing, setAdvancing] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<OffboardingCaseStatus | undefined>()
  const [tick, setTick] = useState(0)
  const [drawerCase, setDrawerCase] = useState<OffboardingCaseResponse | null>(null)
  const [checklist, setChecklist] = useState<AssignmentResponse | null>(null)
  const [checklistLoading, setChecklistLoading] = useState(false)
  const [updatingTask, setUpdatingTask] = useState<string | null>(null)

  const [clearanceCase, setClearanceCase] = useState<OffboardingCaseResponse | null>(null)
  const [clearances, setClearances] = useState<ClearanceResponse[]>([])
  const [clearanceLoading, setClearanceLoading] = useState(false)
  const [signingOff, setSigningOff] = useState<ClearanceDepartment | null>(null)

  const [assetCase, setAssetCase] = useState<OffboardingCaseResponse | null>(null)
  const [assetReturns, setAssetReturns] = useState<AssetReturnResponse[]>([])
  const [assetLoading, setAssetLoading] = useState(false)
  const [updatingAsset, setUpdatingAsset] = useState<string | null>(null)

  const [itCase, setItCase] = useState<OffboardingCaseResponse | null>(null)
  const [itAccess, setItAccess] = useState<ItAccessResponse[]>([])
  const [itLoading, setItLoading] = useState(false)
  const [updatingIt, setUpdatingIt] = useState<string | null>(null)

  const [settlementCase, setSettlementCase] = useState<OffboardingCaseResponse | null>(null)
  const [settlement, setSettlement] = useState<SettlementResponse | null>(null)
  const [settlementLoading, setSettlementLoading] = useState(false)
  const [newComp, setNewComp] = useState<SettlementComponentRequest>({ componentType: 'SALARY', description: '', amount: 0 })
  const [addingComp, setAddingComp] = useState(false)
  const [transitioningSettlement, setTransitioningSettlement] = useState(false)

  const [exitCase, setExitCase] = useState<OffboardingCaseResponse | null>(null)
  const [exitInterview, setExitInterview] = useState<ExitInterviewResponse | null>(null)
  const [exitLoading, setExitLoading] = useState(false)
  const [savingExit, setSavingExit] = useState(false)
  const [exitForm, setExitForm] = useState<ExitInterviewRequest>({})

  const [handoverCase, setHandoverCase] = useState<OffboardingCaseResponse | null>(null)
  const [handoverTasks, setHandoverTasks] = useState<HandoverTaskResponse[]>([])
  const [handoverLoading, setHandoverLoading] = useState(false)
  const [updatingHandover, setUpdatingHandover] = useState<string | null>(null)
  const [newTaskTitle, setNewTaskTitle] = useState('')
  const [addingTask, setAddingTask] = useState(false)

  const refresh = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    getOffboardingOverview().then(setOverview).catch(() => null)
  }, [tick])

  useEffect(() => {
    setLoading(true)
    listOffboardingCases({ status: statusFilter, page, size: 20 })
      .then(setCases)
      .catch(() => message.error('Failed to load cases'))
      .finally(() => setLoading(false))
  }, [tick, page, statusFilter])

  const openAssets = (c: OffboardingCaseResponse) => {
    setAssetCase(c)
    setAssetReturns([])
    setAssetLoading(true)
    listAssetReturns(c.id)
      .then(setAssetReturns)
      .catch(() => message.error('Failed to load asset returns'))
      .finally(() => setAssetLoading(false))
  }

  const doUpdateAsset = async (returnId: string, req: AssetReturnUpdateRequest) => {
    if (!assetCase) return
    setUpdatingAsset(returnId)
    try {
      const updated = await updateAssetReturn(assetCase.id, returnId, req)
      setAssetReturns(prev => prev.map(r => r.id === returnId ? updated : r))
    } catch {
      message.error('Failed to update asset return')
    } finally {
      setUpdatingAsset(null)
    }
  }

  const openItAccess = (c: OffboardingCaseResponse) => {
    setItCase(c)
    setItAccess([])
    setItLoading(true)
    listItAccess(c.id)
      .then(setItAccess)
      .catch(() => message.error('Failed to load IT access data'))
      .finally(() => setItLoading(false))
  }

  const doUpdateIt = async (accessId: string, req: ItAccessUpdateRequest) => {
    if (!itCase) return
    setUpdatingIt(accessId)
    try {
      const updated = await updateItAccess(itCase.id, accessId, req)
      setItAccess(prev => prev.map(r => r.id === accessId ? updated : r))
    } catch {
      message.error('Failed to update IT access')
    } finally {
      setUpdatingIt(null)
    }
  }

  const openSettlement = async (c: OffboardingCaseResponse) => {
    setSettlementCase(c)
    setSettlement(null)
    setSettlementLoading(true)
    try {
      const s = await getOrCreateSettlement(c.id)
      setSettlement(s)
    } catch {
      message.error('Failed to load settlement')
    }
    setSettlementLoading(false)
  }

  const doAddComponent = async () => {
    if (!settlement || !newComp.description.trim()) return
    setAddingComp(true)
    try {
      const s2 = await addSettlementComponent(settlement.id, newComp)
      setSettlement(prev => prev ? { ...prev, components: [...prev.components, s2] } : prev)
      setNewComp({ componentType: 'SALARY', description: '', amount: 0 })
      const fresh = await getOrCreateSettlement(settlementCase!.id)
      setSettlement(fresh)
    } catch {
      message.error('Failed to add component')
    }
    setAddingComp(false)
  }

  const doDeleteComponent = async (compId: string) => {
    if (!settlementCase) return
    try {
      await deleteSettlementComponent(compId)
      const fresh = await getOrCreateSettlement(settlementCase.id)
      setSettlement(fresh)
    } catch {
      message.error('Failed to delete component')
    }
  }

  const doTransitionSettlement = async (status: SettlementStatus) => {
    if (!settlement) return
    setTransitioningSettlement(true)
    try {
      const updated = await transitionSettlementStatus(settlement.id, status)
      setSettlement(updated)
    } catch {
      message.error('Failed to update settlement status')
    }
    setTransitioningSettlement(false)
  }

  const openExitInterview = async (c: OffboardingCaseResponse) => {
    setExitCase(c)
    setExitInterview(null)
    setExitForm({})
    setExitLoading(true)
    const ei = await getExitInterview(c.id)
    if (ei) {
      setExitInterview(ei)
      setExitForm({
        overallRating: ei.overallRating, wouldRecommend: ei.wouldRecommend,
        reasonForLeaving: ei.reasonForLeaving, feedback: ei.feedback,
        improvementSuggestions: ei.improvementSuggestions,
        conductedBy: ei.conductedBy, interviewMode: ei.interviewMode,
        followUpRequired: ei.followUpRequired, followUpNotes: ei.followUpNotes,
      })
    }
    setExitLoading(false)
  }

  const doSaveExitInterview = async () => {
    if (!exitCase) return
    setSavingExit(true)
    try {
      const saved = await saveExitInterview(exitCase.id, exitForm)
      setExitInterview(saved)
      message.success('Exit interview saved')
    } catch {
      message.error('Failed to save exit interview')
    } finally {
      setSavingExit(false)
    }
  }

  const openHandover = (c: OffboardingCaseResponse) => {
    setHandoverCase(c)
    setHandoverTasks([])
    setHandoverLoading(true)
    listHandoverTasks(c.id)
      .then(setHandoverTasks)
      .catch(() => message.error('Failed to load handover tasks'))
      .finally(() => setHandoverLoading(false))
  }

  const doUpdateHandover = async (taskId: string, status: HandoverStatus) => {
    setUpdatingHandover(taskId)
    try {
      const updated = await updateHandoverTask(taskId, { handoverStatus: status })
      setHandoverTasks(prev => prev.map(t => t.id === taskId ? updated : t))
    } catch {
      message.error('Failed to update handover task')
    } finally {
      setUpdatingHandover(null)
    }
  }

  const doAddHandoverTask = async () => {
    if (!handoverCase || !newTaskTitle.trim()) return
    setAddingTask(true)
    try {
      const created = await createHandoverTask(handoverCase.id, { title: newTaskTitle.trim() })
      setHandoverTasks(prev => [...prev, created])
      setNewTaskTitle('')
    } catch {
      message.error('Failed to create handover task')
    } finally {
      setAddingTask(false)
    }
  }

  const openClearance = (c: OffboardingCaseResponse) => {
    setClearanceCase(c)
    setClearances([])
    setClearanceLoading(true)
    listClearances(c.id)
      .then(setClearances)
      .catch(() => message.error('Failed to load clearance data'))
      .finally(() => setClearanceLoading(false))
  }

  const doSignOff = async (dept: ClearanceDepartment, status: ClearanceStatus, deductionAmount?: number, notes?: string) => {
    if (!clearanceCase) return
    setSigningOff(dept)
    try {
      const updated = await signOffClearance(clearanceCase.id, dept, { status, deductionAmount, notes })
      setClearances(prev => prev.map(c => c.department === dept ? updated : c))
    } catch {
      message.error('Failed to update clearance')
    } finally {
      setSigningOff(null)
    }
  }

  const openChecklist = (c: OffboardingCaseResponse) => {
    setDrawerCase(c)
    setChecklist(null)
    setChecklistLoading(true)
    getOffboardingCaseChecklist(c.id)
      .then(setChecklist)
      .catch(() => message.error('No exit checklist found for this case'))
      .finally(() => setChecklistLoading(false))
  }

  const tickTask = async (task: TaskStatusResponse) => {
    const next = task.status === 'PENDING' ? 'IN_PROGRESS'
               : task.status === 'IN_PROGRESS' ? 'DONE'
               : null
    if (!next) return
    setUpdatingTask(task.id)
    try {
      const updated = await checklistsApi.updateTask(task.id, { status: next })
      setChecklist(updated)
    } catch {
      message.error('Failed to update task')
    } finally {
      setUpdatingTask(null)
    }
  }

  const advance = async (id: string, status: OffboardingCaseStatus) => {
    setAdvancing(id + status)
    try {
      await updateOffboardingCaseStatus(id, status)
      message.success('Status updated')
      refresh()
    } catch {
      message.error('Failed to update status')
    } finally {
      setAdvancing(null)
    }
  }

  const columns = [
    {
      title: 'Case',
      dataIndex: 'caseNo',
      render: (v: string, r: OffboardingCaseResponse) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontWeight: 600 }}>{v}</span>
          <Tag color={SOURCE_COLOR[r.source]}>{r.source}</Tag>
        </Space>
      ),
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{id.slice(0, 8)}…</span>,
    },
    {
      title: 'Status',
      dataIndex: 'caseStatus',
      render: (v: OffboardingCaseStatus) => <Tag color={STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Last Working Date',
      dataIndex: 'lastWorkingDate',
      render: (v?: string) => v ?? '—',
    },
    {
      title: 'Exit Reason',
      dataIndex: 'exitReason',
      render: (v?: string) => v ?? '—',
    },
    {
      title: 'Actions',
      render: (_: unknown, r: OffboardingCaseResponse) => {
        const next = TRANSITIONS[r.caseStatus] ?? []
        return (
          <Space>
            <Button
              size="small"
              icon={<CheckSquareOutlined />}
              onClick={() => openChecklist(r)}
            >
              Checklist
            </Button>
            <Button
              size="small"
              icon={<InboxOutlined />}
              onClick={() => openAssets(r)}
            >
              Assets
            </Button>
            <Button
              size="small"
              icon={<LaptopOutlined />}
              onClick={() => openItAccess(r)}
            >
              IT Access
            </Button>
            <Button
              size="small"
              icon={<DollarOutlined />}
              onClick={() => openSettlement(r)}
            >
              Settlement
            </Button>
            <Button
              size="small"
              icon={<CommentOutlined />}
              onClick={() => openExitInterview(r)}
            >
              Exit Interview
            </Button>
            <Button
              size="small"
              icon={<DeploymentUnitOutlined />}
              onClick={() => openHandover(r)}
            >
              Handover
            </Button>
            <Button
              size="small"
              icon={<ClearOutlined />}
              onClick={() => openClearance(r)}
            >
              Clearance
            </Button>
            {next.map(s => (
              <Button
                key={s}
                size="small"
                danger={s === 'CANCELLED'}
                loading={advancing === r.id + s}
                onClick={() => advance(r.id, s)}
              >
                {s.replace(/_/g, ' ')}
              </Button>
            ))}
          </Space>
        )
      },
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} align="center">
        <LogoutOutlined style={{ fontSize: 22 }} />
        <Title level={3} style={{ margin: 0 }}>Offboarding Console</Title>
        <Link to="/lifecycle/offboarding/resignations/new">
          <Button type="primary" icon={<UserDeleteOutlined />}>New Resignation</Button>
        </Link>
      </Space>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card><Statistic title="Active Cases" value={overview?.activeCases ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Leaving This Week" value={overview?.leavingThisWeek ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Leaving This Month" value={overview?.leavingThisMonth ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="Total Cases" value={overview?.totalCases ?? 0} /></Card>
        </Col>
      </Row>

      <Card
        title="Offboarding Cases"
        extra={
          <Select
            allowClear
            placeholder="Filter by status"
            style={{ width: 200 }}
            value={statusFilter}
            onChange={v => { setStatusFilter(v); setPage(0) }}
            options={Object.keys(TRANSITIONS).map(s => ({
              label: s.replace(/_/g, ' '),
              value: s,
            }))}
          />
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={cases?.content ?? []}
          columns={columns}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: cases?.totalElements ?? 0,
            onChange: p => setPage(p - 1),
          }}
        />
      </Card>
      <Drawer
        title={settlementCase ? `Final Settlement — ${settlementCase.caseNo}` : 'Final Settlement'}
        open={settlementCase !== null}
        onClose={() => setSettlementCase(null)}
        width={600}
        loading={settlementLoading}
      >
        {settlement && (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Row gutter={12}>
              <Col span={8}><Statistic title="Gross" value={settlement.totalGross} suffix={settlement.currency} precision={2} /></Col>
              <Col span={8}><Statistic title="Deductions" value={settlement.totalDeductions} suffix={settlement.currency} precision={2} valueStyle={{ color: '#fa541c' }} /></Col>
              <Col span={8}><Statistic title="Net Payable" value={settlement.netPayable} suffix={settlement.currency} precision={2} valueStyle={{ color: '#52c41a' }} /></Col>
            </Row>
            <Space>
              <Tag color={settlement.status === 'PAID' ? 'green' : settlement.status === 'APPROVED' ? 'cyan' : settlement.status === 'CANCELLED' ? 'red' : 'default'}>
                {settlement.status}
              </Tag>
              {settlement.status === 'DRAFT' && (
                <Button size="small" loading={transitioningSettlement} onClick={() => doTransitionSettlement('PENDING_APPROVAL')}>
                  Submit for Approval
                </Button>
              )}
              {settlement.status === 'PENDING_APPROVAL' && (
                <Button size="small" type="primary" loading={transitioningSettlement} onClick={() => doTransitionSettlement('APPROVED')}>
                  Approve
                </Button>
              )}
              {settlement.status === 'APPROVED' && (
                <Button size="small" type="primary" loading={transitioningSettlement} onClick={() => doTransitionSettlement('PAID')}>
                  Mark Paid
                </Button>
              )}
            </Space>
            <Card size="small" title="Add Component">
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                <Space.Compact style={{ width: '100%' }}>
                  <Select
                    style={{ width: 160 }}
                    value={newComp.componentType}
                    onChange={v => setNewComp(c => ({ ...c, componentType: v }))}
                    options={['SALARY','GRATUITY','LEAVE_ENCASHMENT','NOTICE_PAY','BONUS','ALLOWANCE','DEDUCTION','ASSET_DEDUCTION','OTHER']
                      .map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
                  />
                  <Input
                    placeholder="Description"
                    value={newComp.description}
                    onChange={e => setNewComp(c => ({ ...c, description: e.target.value }))}
                  />
                </Space.Compact>
                <Space.Compact style={{ width: '100%' }}>
                  <Input
                    type="number"
                    placeholder="Amount"
                    value={newComp.amount}
                    onChange={e => setNewComp(c => ({ ...c, amount: parseFloat(e.target.value) || 0 }))}
                  />
                  <Select
                    style={{ width: 130 }}
                    value={newComp.isDeduction ? 'deduction' : 'earning'}
                    onChange={v => setNewComp(c => ({ ...c, isDeduction: v === 'deduction' }))}
                    options={[{ label: 'Earning', value: 'earning' }, { label: 'Deduction', value: 'deduction' }]}
                  />
                  <Button type="primary" loading={addingComp} onClick={doAddComponent}>Add</Button>
                </Space.Compact>
              </Space>
            </Card>
            {settlement.components.length > 0 && (
              <Space direction="vertical" style={{ width: '100%' }} size={6}>
                {settlement.components.map(comp => (
                  <Card key={comp.id} size="small"
                    style={{ borderLeft: `3px solid ${comp.isDeduction ? '#fa541c' : '#52c41a'}` }}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Space direction="vertical" size={0}>
                        <span style={{ fontWeight: 600 }}>{comp.description}</span>
                        <span style={{ color: '#888', fontSize: 12 }}>{comp.componentType.replace(/_/g, ' ')}</span>
                      </Space>
                      <Space>
                        <span style={{ fontWeight: 600, color: comp.isDeduction ? '#fa541c' : '#52c41a' }}>
                          {comp.isDeduction ? '-' : '+'}{comp.amount.toFixed(2)} {settlement.currency}
                        </span>
                        <Button size="small" danger onClick={() => doDeleteComponent(comp.id)}>✕</Button>
                      </Space>
                    </Space>
                  </Card>
                ))}
              </Space>
            )}
          </Space>
        )}
      </Drawer>

      <Drawer
        title={exitCase ? `Exit Interview — ${exitCase.caseNo}` : 'Exit Interview'}
        open={exitCase !== null}
        onClose={() => setExitCase(null)}
        width={520}
        loading={exitLoading}
        extra={
          <Button type="primary" loading={savingExit} onClick={doSaveExitInterview}>
            Save
          </Button>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          {exitInterview && (
            <Tag color="green">Recorded — {exitInterview.conductedAt?.slice(0, 10)}</Tag>
          )}
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Conducted By</div>
            <Input
              value={exitForm.conductedBy ?? ''}
              onChange={e => setExitForm(f => ({ ...f, conductedBy: e.target.value }))}
              placeholder="Interviewer name"
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Interview Mode</div>
            <Select
              style={{ width: '100%' }}
              value={exitForm.interviewMode}
              onChange={v => setExitForm(f => ({ ...f, interviewMode: v }))}
              allowClear
              options={['IN_PERSON', 'VIDEO', 'PHONE', 'WRITTEN'].map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Overall Rating (1–5)</div>
            <Select
              style={{ width: '100%' }}
              value={exitForm.overallRating}
              onChange={v => setExitForm(f => ({ ...f, overallRating: v }))}
              allowClear
              options={[1, 2, 3, 4, 5].map(n => ({ label: `${n} — ${['Very Poor', 'Poor', 'Average', 'Good', 'Excellent'][n - 1]}`, value: n }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Would Recommend?</div>
            <Select
              style={{ width: '100%' }}
              value={exitForm.wouldRecommend}
              onChange={v => setExitForm(f => ({ ...f, wouldRecommend: v }))}
              allowClear
              options={[{ label: 'Yes', value: true }, { label: 'No', value: false }]}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Reason for Leaving</div>
            <Input.TextArea
              rows={3}
              value={exitForm.reasonForLeaving ?? ''}
              onChange={e => setExitForm(f => ({ ...f, reasonForLeaving: e.target.value }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Feedback</div>
            <Input.TextArea
              rows={3}
              value={exitForm.feedback ?? ''}
              onChange={e => setExitForm(f => ({ ...f, feedback: e.target.value }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>Improvement Suggestions</div>
            <Input.TextArea
              rows={3}
              value={exitForm.improvementSuggestions ?? ''}
              onChange={e => setExitForm(f => ({ ...f, improvementSuggestions: e.target.value }))}
            />
          </div>
        </Space>
      </Drawer>

      <Drawer
        title={handoverCase ? `Knowledge Handover — ${handoverCase.caseNo}` : 'Knowledge Handover'}
        open={handoverCase !== null}
        onClose={() => setHandoverCase(null)}
        width={540}
        loading={handoverLoading}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder="New handover task title…"
              value={newTaskTitle}
              onChange={e => setNewTaskTitle(e.target.value)}
              onPressEnter={doAddHandoverTask}
            />
            <Button type="primary" loading={addingTask} onClick={doAddHandoverTask}>
              Add
            </Button>
          </Space.Compact>
          {handoverTasks.map(task => (
            <Card key={task.id} size="small"
              style={{ borderLeft: `3px solid ${task.handoverStatus === 'TRANSFERRED' ? '#52c41a' : task.handoverStatus === 'IN_PROGRESS' ? '#1677ff' : '#d9d9d9'}` }}>
              <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                <Space direction="vertical" size={2}>
                  <span style={{ fontWeight: 600 }}>{task.title}</span>
                  {task.handoverTo && <span style={{ color: '#888', fontSize: 12 }}>To: {task.handoverTo}</span>}
                  {task.dueDate && <span style={{ color: '#888', fontSize: 12 }}>Due: {task.dueDate}</span>}
                  <Tag color={task.handoverStatus === 'TRANSFERRED' ? 'green' : task.handoverStatus === 'IN_PROGRESS' ? 'processing' : task.handoverStatus === 'WAIVED' ? 'purple' : 'default'}>
                    {task.handoverStatus.replace(/_/g, ' ')}
                  </Tag>
                </Space>
                <Select
                  size="small"
                  style={{ width: 140 }}
                  value={task.handoverStatus}
                  loading={updatingHandover === task.id}
                  onChange={(s: HandoverStatus) => doUpdateHandover(task.id, s)}
                  options={(['PENDING','IN_PROGRESS','TRANSFERRED','WAIVED'] as HandoverStatus[])
                    .map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
                />
              </Space>
            </Card>
          ))}
          {!handoverLoading && handoverTasks.length === 0 && (
            <div style={{ color: '#888', textAlign: 'center', marginTop: 24 }}>
              No handover tasks yet. Add tasks above.
            </div>
          )}
        </Space>
      </Drawer>

      <Drawer
        title={itCase ? `IT Access Removal — ${itCase.caseNo}` : 'IT Access Removal'}
        open={itCase !== null}
        onClose={() => setItCase(null)}
        width={520}
        loading={itLoading}
      >
        {itAccess.length > 0 && (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {itAccess.map(row => (
              <Card key={row.id} size="small"
                style={{ borderLeft: `3px solid ${row.accessStatus === 'REMOVED' ? '#52c41a' : row.accessStatus === 'IN_PROGRESS' ? '#1677ff' : '#d9d9d9'}` }}>
                <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                  <Space direction="vertical" size={2}>
                    <span style={{ fontWeight: 600 }}>{row.displayLabel}</span>
                    <Tag color={row.accessStatus === 'REMOVED' ? 'green' : row.accessStatus === 'IN_PROGRESS' ? 'processing' : 'default'}>
                      {row.accessStatus.replace(/_/g, ' ')}
                    </Tag>
                    {row.handledBy && <span style={{ color: '#888', fontSize: 12 }}>By: {row.handledBy}</span>}
                    {row.reference && <span style={{ color: '#888', fontSize: 12 }}>Ref: {row.reference}</span>}
                  </Space>
                  <Select
                    size="small"
                    style={{ width: 150 }}
                    value={row.accessStatus}
                    loading={updatingIt === row.id}
                    onChange={(status: ItAccessStatus) => doUpdateIt(row.id, { accessStatus: status })}
                    options={(['PENDING','IN_PROGRESS','REMOVED','KEPT_TEMPORARILY','NOT_APPLICABLE','WAIVED'] as ItAccessStatus[])
                      .map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
                  />
                </Space>
              </Card>
            ))}
          </Space>
        )}
        {!itLoading && itAccess.length === 0 && (
          <div style={{ color: '#888', textAlign: 'center', marginTop: 40 }}>
            IT access rows are created automatically when the case becomes active.
          </div>
        )}
      </Drawer>

      <Drawer
        title={assetCase ? `Asset Returns — ${assetCase.caseNo}` : 'Asset Returns'}
        open={assetCase !== null}
        onClose={() => setAssetCase(null)}
        width={540}
        loading={assetLoading}
      >
        {assetReturns.length > 0 && (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {assetReturns.map(row => (
              <Card key={row.id} size="small"
                style={{ borderLeft: `3px solid ${row.returnStatus === 'RETURNED' ? '#52c41a' : row.returnStatus === 'MISSING' ? '#ff4d4f' : '#d9d9d9'}` }}>
                <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                  <Space direction="vertical" size={2}>
                    <span style={{ fontWeight: 600 }}>{row.assetName}</span>
                    <span style={{ color: '#888', fontSize: 12 }}>{row.assetType}{row.assetIdentifier ? ` · ${row.assetIdentifier}` : ''}</span>
                    <Tag color={row.returnStatus === 'RETURNED' ? 'green' : row.returnStatus === 'MISSING' ? 'red' : row.returnStatus === 'RETURNED_DAMAGED' ? 'orange' : 'default'}>
                      {row.returnStatus.replace(/_/g, ' ')}
                    </Tag>
                    {row.returnDate && <span style={{ color: '#888', fontSize: 12 }}>Returned: {row.returnDate}</span>}
                    {row.deductionAmount != null && (
                      <span style={{ color: '#fa541c', fontSize: 12 }}>Deduction: {row.deductionAmount} AZN</span>
                    )}
                  </Space>
                  <Select
                    size="small"
                    style={{ width: 160 }}
                    value={row.returnStatus}
                    loading={updatingAsset === row.id}
                    onChange={(status: AssetReturnStatus) => doUpdateAsset(row.id, { returnStatus: status })}
                    options={(['PENDING_RETURN','RETURNED','RETURNED_DAMAGED','MISSING','DEDUCTION_APPROVED','WRITTEN_OFF','WAIVED'] as AssetReturnStatus[])
                      .map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
                  />
                </Space>
              </Card>
            ))}
          </Space>
        )}
        {!assetLoading && assetReturns.length === 0 && (
          <div style={{ color: '#888', textAlign: 'center', marginTop: 40 }}>
            No assets assigned. Asset return rows are created automatically when the case becomes active.
          </div>
        )}
      </Drawer>

      <Drawer
        title={drawerCase ? `Exit Checklist — ${drawerCase.caseNo}` : 'Exit Checklist'}
        open={drawerCase !== null}
        onClose={() => setDrawerCase(null)}
        width={520}
        loading={checklistLoading}
      >
        {checklist && (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <div>
              <Progress
                percent={checklist.progressPercent}
                status={checklist.status === 'COMPLETED' ? 'success' : 'active'}
              />
              <div style={{ color: '#888', fontSize: 12 }}>
                {checklist.completedTasks}/{checklist.totalTasks} tasks done
                {' · '}{checklist.requiredCompleted}/{checklist.requiredTotal} required
              </div>
            </div>
            {checklist.tasks.map(task => (
              <Card
                key={task.id}
                size="small"
                style={{ borderLeft: `3px solid ${task.required ? '#1677ff' : '#d9d9d9'}` }}
              >
                <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                  <Space direction="vertical" size={2}>
                    <span style={{ fontWeight: 600 }}>{task.title}</span>
                    {task.description && <span style={{ color: '#888', fontSize: 12 }}>{task.description}</span>}
                    <Space size={4}>
                      <Tag color={TASK_STATUS_COLOR[task.status]}>{task.status}</Tag>
                      {task.ownerRole && <Tag>{task.ownerRole}</Tag>}
                    </Space>
                  </Space>
                  {task.status !== 'DONE' && task.status !== 'SKIPPED' && (
                    <Button
                      size="small"
                      loading={updatingTask === task.id}
                      onClick={() => tickTask(task)}
                    >
                      {task.status === 'PENDING' ? 'Start' : 'Done'}
                    </Button>
                  )}
                </Space>
              </Card>
            ))}
          </Space>
        )}
        {!checklistLoading && !checklist && (
          <div style={{ color: '#888', textAlign: 'center', marginTop: 40 }}>
            No exit checklist has been assigned to this case yet.
          </div>
        )}
      </Drawer>

      <Drawer
        title={clearanceCase ? `Clearance — ${clearanceCase.caseNo}` : 'Clearance'}
        open={clearanceCase !== null}
        onClose={() => setClearanceCase(null)}
        width={560}
        loading={clearanceLoading}
      >
        {clearances.length > 0 && (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {(() => {
              const resolved = clearances.filter(c =>
                ['CLEARED', 'CLEARED_WITH_DEDUCTION', 'WAIVED'].includes(c.status)).length
              return (
                <Progress
                  percent={Math.round((resolved / clearances.length) * 100)}
                  format={() => `${resolved}/${clearances.length}`}
                  status={resolved === clearances.length ? 'success' : 'active'}
                />
              )
            })()}
            {clearances.map(row => (
              <Card key={row.department} size="small"
                style={{ borderLeft: `3px solid ${CLEARANCE_STATUS_COLOR[row.status] ?? '#d9d9d9'}` }}>
                <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                  <Space direction="vertical" size={2}>
                    <span style={{ fontWeight: 600 }}>{row.department}</span>
                    <Tag color={CLEARANCE_STATUS_COLOR[row.status]}>{row.status.replace(/_/g, ' ')}</Tag>
                    {row.clearedBy && (
                      <span style={{ color: '#888', fontSize: 12 }}>
                        by {row.clearedBy} · {row.clearedAt?.slice(0, 10)}
                      </span>
                    )}
                    {row.deductionAmount != null && (
                      <span style={{ color: '#fa541c', fontSize: 12 }}>
                        Deduction: {row.deductionAmount} AZN
                      </span>
                    )}
                    {row.notes && <span style={{ color: '#888', fontSize: 12 }}>{row.notes}</span>}
                  </Space>
                  <Select
                    size="small"
                    style={{ width: 160 }}
                    value={row.status}
                    loading={signingOff === row.department}
                    onChange={status => doSignOff(row.department, status as ClearanceStatus)}
                    options={CLEARANCE_STATUSES.map(s => ({ label: s.replace(/_/g, ' '), value: s }))}
                  />
                </Space>
              </Card>
            ))}
          </Space>
        )}
        {!clearanceLoading && clearances.length === 0 && (
          <div style={{ color: '#888', textAlign: 'center', marginTop: 40 }}>
            Clearance records will be created automatically when the case becomes active.
          </div>
        )}
      </Drawer>
    </div>
  )
}
