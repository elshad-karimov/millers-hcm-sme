import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, Drawer, Progress, Row, Select, Space,
  Statistic, Table, Tag, Typography, message
} from 'antd'
import { CheckSquareOutlined, ClearOutlined, InboxOutlined, LaptopOutlined, LogoutOutlined, UserDeleteOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import {
  getOffboardingOverview, listOffboardingCases, updateOffboardingCaseStatus,
  getOffboardingCaseChecklist, listClearances, signOffClearance,
  listAssetReturns, updateAssetReturn, listItAccess, updateItAccess,
  type AssignmentResponse, type AssetReturnResponse, type AssetReturnStatus,
  type AssetReturnUpdateRequest, type ClearanceDepartment, type ClearanceResponse,
  type ClearanceStatus, type ItAccessResponse, type ItAccessStatus,
  type ItAccessUpdateRequest, type OffboardingCaseResponse, type OffboardingCaseStatus,
  type OffboardingOverviewResponse, type PageResponse,
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
