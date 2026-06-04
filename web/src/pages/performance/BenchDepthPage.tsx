// M94 — Succession bench depth report.
// Per-manager readiness counts (Ready Now / Ready Soon / Long-term / Dev),
// sorted deepest bench first. HR-only (HR_READ on the backend).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  successionApi,
  type BenchReport,
  type BenchRow,
  type DevelopmentEmployee,
  type DevelopmentList,
} from '../../api/succession'
import { performanceApi, type ReviewCycle } from '../../api/performance'
import {
  learningPathsApi,
  pathAssignmentsApi,
  type BulkAssignResult,
  type LearningPath,
  type SuggestedPath,
} from '../../api/learningPaths'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

export function BenchDepthPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canAssign = hasRole(...RoleSets.HR_WRITE)
  const navigate = useNavigate()
  const { cycleId: routeCycleId } = useParams<{ cycleId?: string }>()
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>(routeCycleId)
  const [report, setReport] = useState<BenchReport | null>(null)
  const [loading, setLoading] = useState(true)

  // ── M96 — development drill state ─────────────────────────────────────
  const [drillManager, setDrillManager] = useState<BenchRow | null>(null)
  const [drillData, setDrillData] = useState<DevelopmentList | null>(null)
  const [drillLoading, setDrillLoading] = useState(false)
  const [paths, setPaths] = useState<LearningPath[]>([])
  const [assignTarget, setAssignTarget] = useState<DevelopmentEmployee | null>(null)
  const [suggestions, setSuggestions] = useState<SuggestedPath[]>([])
  const [suggestionsLoading, setSuggestionsLoading] = useState(false)
  // M101 — bulk assign state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bulkOpen, setBulkOpen] = useState(false)
  const [bulkForm] = Form.useForm<{
    pathId: string
    targetCompletionDate?: ReturnType<typeof dayjs>
    notes?: string
  }>()
  const [bulkAssigning, setBulkAssigning] = useState(false)
  const [bulkResult, setBulkResult] = useState<BulkAssignResult | null>(null)
  const [assignForm] = Form.useForm<{
    pathId: string
    targetCompletionDate?: ReturnType<typeof dayjs>
    notes?: string
  }>()
  const [assigning, setAssigning] = useState(false)

  useEffect(() => {
    performanceApi
      .cycles()
      .then((cs) => {
        setCycles(cs)
        if (!cycleId && cs.length) setCycleId(cs[0].id)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cycles'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!cycleId) return
    setLoading(true)
    successionApi
      .bench(cycleId)
      .then(setReport)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load bench'))
      .finally(() => setLoading(false))
  }, [cycleId, message])

  // ── M101 — bulk assign helper ─────────────────────────────────────────
  const submitBulkAssign = async () => {
    const v = await bulkForm.validateFields()
    setBulkAssigning(true)
    try {
      const result = await pathAssignmentsApi.bulkAssign(v.pathId, {
        employeeIds: [...selectedIds],
        targetCompletionDate: v.targetCompletionDate?.format('YYYY-MM-DD'),
        notes: v.notes,
      })
      setBulkResult(result)
      setBulkOpen(false)
      bulkForm.resetFields()
      setSelectedIds(new Set())
      if (result.succeeded > 0) await refreshDrill()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Bulk assign failed',
      )
    } finally {
      setBulkAssigning(false)
    }
  }

  // ── M96 — drill open / refresh / assign helpers ──────────────────────
  const openDrill = (r: BenchRow) => {
    if (!cycleId) return
    setDrillManager(r)
    setDrillLoading(true)
    Promise.all([
      successionApi.development(cycleId, r.managerId),
      paths.length === 0 ? learningPathsApi.list(true) : Promise.resolve(paths),
    ])
      .then(([dev, ps]) => {
        setDrillData(dev)
        if (paths.length === 0) setPaths(ps)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load development list'),
      )
      .finally(() => setDrillLoading(false))
  }

  const refreshDrill = async () => {
    if (!cycleId || !drillManager) return
    const fresh = await successionApi.development(cycleId, drillManager.managerId)
    setDrillData(fresh)
  }

  const submitAssign = async () => {
    if (!assignTarget) return
    const v = await assignForm.validateFields()
    setAssigning(true)
    try {
      await pathAssignmentsApi.assign(v.pathId, {
        employeeId: assignTarget.employeeId,
        targetCompletionDate: v.targetCompletionDate?.format('YYYY-MM-DD'),
        notes: v.notes,
      })
      message.success(`Assigned to ${assignTarget.employeeName}`)
      setAssignTarget(null)
      assignForm.resetFields()
      await refreshDrill()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Assign failed',
      )
    } finally {
      setAssigning(false)
    }
  }

  // Roll-up totals across all managers so the header summary mirrors the grid.
  const totals = report
    ? report.rows.reduce(
        (acc, r) => ({
          readyNow: acc.readyNow + r.readyNow,
          readySoon: acc.readySoon + r.readySoon,
          readyLongTerm: acc.readyLongTerm + r.readyLongTerm,
          underDevelopment: acc.underDevelopment + r.underDevelopment,
        }),
        { readyNow: 0, readySoon: 0, readyLongTerm: 0, underDevelopment: 0 },
      )
    : null

  const cols: ColumnsType<BenchRow> = [
    {
      title: 'Manager',
      dataIndex: 'managerName',
      fixed: 'left',
      width: 220,
      render: (v: string, r) => (
        <Link to={`/employees/${r.managerId}`}>{v}</Link>
      ),
    },
    {
      title: 'Reports',
      dataIndex: 'totalReports',
      width: 100,
      align: 'center',
      sorter: (a, b) => a.totalReports - b.totalReports,
    },
    {
      title: 'Placed',
      dataIndex: 'placedReports',
      width: 100,
      align: 'center',
      render: (v: number, r) => (
        <Tag color={v === r.totalReports ? 'green' : 'orange'}>
          {v} / {r.totalReports}
        </Tag>
      ),
    },
    {
      title: 'Ready Now',
      dataIndex: 'readyNow',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readyNow - b.readyNow,
      defaultSortOrder: 'descend',
      render: (v: number) =>
        v > 0 ? <Tag color="green">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Ready Soon',
      dataIndex: 'readySoon',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readySoon - b.readySoon,
      render: (v: number) =>
        v > 0 ? <Tag color="blue">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Long-term',
      dataIndex: 'readyLongTerm',
      width: 120,
      align: 'center',
      sorter: (a, b) => a.readyLongTerm - b.readyLongTerm,
      render: (v: number) =>
        v > 0 ? <Tag color="gold">{v}</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'Development',
      dataIndex: 'underDevelopment',
      width: 130,
      align: 'center',
      sorter: (a, b) => a.underDevelopment - b.underDevelopment,
      render: (v: number, r) =>
        v > 0 ? (
          <Tooltip title="Drill into the development bucket — see who's on which path">
            <a onClick={() => openDrill(r)}>
              <Tag color="orange" style={{ cursor: 'pointer' }}>{v}</Tag>
            </a>
          </Tooltip>
        ) : (
          <Text type="secondary">0</Text>
        ),
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Link to={`/performance/succession/${cycleId}`} state={{ managerId: r.managerId }}>
          Open grid
        </Link>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Bench depth</Title>
        <Space>
          <Text type="secondary">Cycle:</Text>
          <Select
            style={{ minWidth: 280 }}
            value={cycleId}
            onChange={(v) => {
              setCycleId(v)
              navigate(`/performance/succession/${v}/bench`, { replace: true })
            }}
            options={cycles.map((c) => ({ value: c.id, label: c.name }))}
            placeholder="Pick a cycle"
          />
        </Space>
      </Space>

      {loading || !report || !totals ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
          <Spin />
        </div>
      ) : (
        <>
          <Card size="small">
            <Row gutter={16}>
              <Col span={6}>
                <Statistic title="Managers" value={report.totalManagers} />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Ready Now"
                  value={totals.readyNow}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Ready Soon"
                  value={totals.readySoon}
                  valueStyle={{ color: '#1677ff' }}
                />
              </Col>
              <Col span={4}>
                <Statistic
                  title="Long-term"
                  value={totals.readyLongTerm}
                  valueStyle={{ color: '#d48806' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="Under development"
                  value={totals.underDevelopment}
                  valueStyle={
                    totals.underDevelopment > 0 ? { color: '#fa8c16' } : {}
                  }
                />
              </Col>
            </Row>
          </Card>

          <Card title={`Per-manager bench (${report.cycleName})`}>
            <Table
              rowKey="managerId"
              columns={cols}
              dataSource={report.rows}
              pagination={{ pageSize: 25 }}
              size="small"
              scroll={{ x: 1000 }}
            />
          </Card>
        </>
      )}

      {/* M96/M101 — Development drill drawer */}
      <Drawer
        open={!!drillManager}
        title={
          drillManager
            ? `Development bucket — ${drillManager.managerName} (${drillManager.underDevelopment})`
            : ''
        }
        onClose={() => {
          setDrillManager(null)
          setDrillData(null)
          setSelectedIds(new Set())
        }}
        width={640}
        footer={
          canAssign && drillData && drillData.employees.length > 0 ? (
            <Space>
              <Button
                type="primary"
                disabled={selectedIds.size === 0}
                onClick={() => {
                  setBulkOpen(true)
                  bulkForm.resetFields()
                  // Pre-load suggestions for the first selected employee.
                  const firstId = [...selectedIds][0]
                  if (firstId) {
                    setSuggestionsLoading(true)
                    pathAssignmentsApi
                      .suggestions(firstId)
                      .then(setSuggestions)
                      .catch(() => setSuggestions([]))
                      .finally(() => setSuggestionsLoading(false))
                  }
                }}
              >
                Assign to {selectedIds.size} selected…
              </Button>
              <Button
                size="small"
                onClick={() => {
                  const allIds = new Set(drillData.employees.map((e) => e.employeeId))
                  setSelectedIds(selectedIds.size === allIds.size ? new Set() : allIds)
                }}
              >
                {selectedIds.size === drillData.employees.length ? 'Deselect all' : 'Select all'}
              </Button>
            </Space>
          ) : null
        }
      >
        {drillLoading || !drillData ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
            <Spin />
          </div>
        ) : drillData.employees.length === 0 ? (
          <Empty description="Nobody in the development bucket — placement may be incomplete." />
        ) : (
          <List
            itemLayout="vertical"
            dataSource={drillData.employees}
            renderItem={(emp) => (
              <List.Item
                key={emp.employeeId}
                style={{
                  background: selectedIds.has(emp.employeeId)
                    ? 'rgba(22,119,255,0.05)' : undefined,
                  borderRadius: 6,
                  paddingLeft: 8,
                }}
                actions={
                  canAssign
                    ? [
                        <Checkbox
                          key="select"
                          checked={selectedIds.has(emp.employeeId)}
                          onChange={(ev) => {
                            const next = new Set(selectedIds)
                            ev.target.checked
                              ? next.add(emp.employeeId)
                              : next.delete(emp.employeeId)
                            setSelectedIds(next)
                          }}
                        >
                          Select
                        </Checkbox>,
                        <Button
                          key="assign"
                          type="primary"
                          size="small"
                          onClick={() => {
                            setAssignTarget(emp)
                            assignForm.resetFields()
                            // M98 — fetch ranked suggestions for this
                            // employee. Falls back to the full template list
                            // if there are no gaps recorded yet.
                            setSuggestionsLoading(true)
                            pathAssignmentsApi
                              .suggestions(emp.employeeId)
                              .then(setSuggestions)
                              .catch(() => setSuggestions([]))
                              .finally(() => setSuggestionsLoading(false))
                          }}
                        >
                          Assign path…
                        </Button>,
                      ]
                    : undefined
                }
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Link to={`/employees/${emp.employeeId}`}>{emp.employeeName}</Link>
                      <Tag>{emp.department ?? '—'}</Tag>
                    </Space>
                  }
                  description={
                    <Space size="large">
                      <Text type="secondary">
                        Performance {emp.performanceRating?.toFixed(2)} · Potential {emp.potentialRating?.toFixed(2)}
                      </Text>
                      {emp.recommendation && <Tag>{emp.recommendation.replace(/_/g, ' ')}</Tag>}
                    </Space>
                  }
                />
                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Active learning paths ({emp.activeAssignments.length})
                  </Text>
                  {emp.activeAssignments.length === 0 ? (
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary">No active assignments — start one with the button.</Text>
                    </div>
                  ) : (
                    <List
                      size="small"
                      dataSource={emp.activeAssignments}
                      renderItem={(a) => (
                        <List.Item style={{ padding: '6px 0' }}>
                          <Space size="middle" style={{ width: '100%', justifyContent: 'space-between' }}>
                            <Link to={`/learning/paths`}>{a.pathName}</Link>
                            <Space>
                              <Tag color="blue">{a.status.replace(/_/g, ' ')}</Tag>
                              <Progress
                                percent={a.progressPercent}
                                size="small"
                                style={{ width: 120 }}
                              />
                            </Space>
                          </Space>
                        </List.Item>
                      )}
                    />
                  )}
                </div>
              </List.Item>
            )}
          />
        )}
      </Drawer>

      {/* Assign path modal (per-employee from drill) */}
      <Modal
        open={!!assignTarget}
        title={assignTarget ? `Assign a learning path to ${assignTarget.employeeName}` : ''}
        onCancel={() => setAssignTarget(null)}
        onOk={submitAssign}
        confirmLoading={assigning}
        okText="Assign"
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            name="pathId"
            label={
              <Space>
                <span>Learning path</span>
                {suggestionsLoading ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>(ranking by skills gap…)</Text>
                ) : suggestions.length > 0 && suggestions[0].competenciesCovered > 0 ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>(ranked by competency gaps)</Text>
                ) : null}
              </Space>
            }
            rules={[{ required: true, message: 'Pick a path' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Pick a curriculum"
              options={
                // M98 — prefer the ranked suggestions when we have them.
                // Each option carries the coverage tag inline; tooltips
                // attached via title attribute since AntD Select options
                // don't accept JSX children for the dropdown item itself.
                (suggestions.length > 0 ? suggestions : null)
                  ? suggestions.map((s) => ({
                      value: s.pathId,
                      // The `label` is used for both filter-by-search AND
                      // the selected-value chip. Keep it plain so search works.
                      label:
                        `${s.pathCode} — ${s.pathName}` +
                        (s.competenciesCovered > 0
                          ? `  · covers ${s.competenciesCovered} gap${s.competenciesCovered === 1 ? '' : 's'} (+${s.totalLevelLift})`
                          : '') +
                        (s.alreadyAssigned ? '  · already assigned' : ''),
                      disabled: s.alreadyAssigned,
                      title:
                        s.coveredCompetencyNames.length > 0
                          ? `Closes gaps in: ${s.coveredCompetencyNames.join(', ')}`
                          : undefined,
                    }))
                  : paths.map((p) => ({
                      value: p.id,
                      label: `${p.pathNo} — ${p.name}`,
                    }))
              }
            />
          </Form.Item>
          <Form.Item name="targetCompletionDate" label="Target completion (optional)">
            <DatePicker style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea
              rows={3}
              placeholder="Why this path? Linked review / manager input?"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* M101 — Bulk assign modal (one path → N selected employees) */}
      <Modal
        open={bulkOpen}
        title={`Assign a path to ${selectedIds.size} selected employee${selectedIds.size === 1 ? '' : 's'}`}
        onCancel={() => setBulkOpen(false)}
        onOk={submitBulkAssign}
        confirmLoading={bulkAssigning}
        okText={`Assign to ${selectedIds.size}`}
      >
        <Form form={bulkForm} layout="vertical">
          <Form.Item
            name="pathId"
            label={
              <Space>
                <span>Learning path</span>
                {suggestionsLoading ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>(ranking by skills gap…)</Text>
                ) : suggestions.length > 0 && suggestions[0].competenciesCovered > 0 ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>(ranked by competency gaps)</Text>
                ) : null}
              </Space>
            }
            rules={[{ required: true, message: 'Pick a path' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Pick a curriculum"
              options={
                suggestions.length > 0
                  ? suggestions.map((s) => ({
                      value: s.pathId,
                      label:
                        `${s.pathCode} — ${s.pathName}` +
                        (s.competenciesCovered > 0
                          ? `  · covers ${s.competenciesCovered} gap${s.competenciesCovered === 1 ? '' : 's'} (+${s.totalLevelLift})`
                          : '') +
                        (s.alreadyAssigned ? '  · already assigned' : ''),
                      disabled: false, // in bulk, HR may override the already-assigned check
                      title:
                        s.coveredCompetencyNames.length > 0
                          ? `Closes gaps in: ${s.coveredCompetencyNames.join(', ')}`
                          : undefined,
                    }))
                  : paths.map((p) => ({
                      value: p.id,
                      label: `${p.pathNo} — ${p.name}`,
                    }))
              }
            />
          </Form.Item>
          <Form.Item name="targetCompletionDate" label="Target completion (optional)">
            <DatePicker style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea
              rows={3}
              placeholder="e.g. Required for Q3 promotion eligibility"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* M101 — Bulk assign result summary */}
      <Modal
        open={!!bulkResult}
        title="Bulk assign result"
        onCancel={() => setBulkResult(null)}
        footer={<Button type="primary" onClick={() => setBulkResult(null)}>Close</Button>}
      >
        {bulkResult && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Row gutter={16}>
              <Col span={8}><Statistic title="Succeeded" value={bulkResult.succeeded}
                valueStyle={{ color: '#52c41a' }} /></Col>
              <Col span={8}><Statistic title="Skipped" value={bulkResult.skipped}
                valueStyle={bulkResult.skipped > 0 ? { color: '#fa8c16' } : {}} /></Col>
              <Col span={8}><Statistic title="Failed" value={bulkResult.failed}
                valueStyle={bulkResult.failed > 0 ? { color: '#ff4d4f' } : {}} /></Col>
            </Row>
            {(bulkResult.skipped > 0 || bulkResult.failed > 0) && (
              <Alert
                type="info"
                showIcon
                message="Skipped = employee already had an active assignment for this path."
                description={
                  bulkResult.failed > 0
                    ? `${bulkResult.failed} assignment(s) failed due to unexpected errors.`
                    : undefined
                }
              />
            )}
          </Space>
        )}
      </Modal>
    </Space>
  )
}
