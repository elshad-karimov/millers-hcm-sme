// HCM_12 M390 — KPI library + per-cycle assignments (PRD §7).
// HR Admin curates the library; HR/managers assign KPIs to employees per cycle and
// record measurements. Achievement % and 1–5 rating are computed server-side
// (LINEAR achievement÷20 or THRESHOLD bands — see KpiScoringMath).

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  KPI_DATA_SOURCE_LABEL,
  KPI_FREQUENCY_LABEL,
  KPI_SCORING_LABEL,
  kpisApi,
  performanceApi,
  type KpiAssignmentResponse,
  type KpiDataSource,
  type KpiFrequency,
  type KpiRequest,
  type KpiResponse,
  type KpiResultResponse,
  type KpiScoringModel,
  type ReviewCycle,
} from '../../api/performance'
import { employeesApi, type Employee } from '../../api/employees'
import { EmployeePicker } from '../../components/EmployeePicker'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<string, string> = {
  ASSIGNED: 'blue',
  IN_PROGRESS: 'gold',
  MEASURED: 'green',
  CANCELLED: 'default',
}

function ratingTag(rating?: number | null) {
  if (rating == null) return <Text type="secondary">—</Text>
  const color = rating >= 4.5 ? 'green' : rating >= 3.5 ? 'cyan' : rating >= 2.5 ? 'gold' : 'red'
  return <Tag color={color}>{Number(rating).toFixed(2)}</Tag>
}

export function KpiPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canAdmin = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const canAssign = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [kpis, setKpis] = useState<KpiResponse[]>([])
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)

  // library editor
  const [kpiOpen, setKpiOpen] = useState(false)
  const [editingKpi, setEditingKpi] = useState<KpiResponse | null>(null)
  const [savingKpi, setSavingKpi] = useState(false)
  const [kpiForm] = Form.useForm<KpiRequest>()

  // assignments
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [assignments, setAssignments] = useState<KpiAssignmentResponse[]>([])
  const [loadingAssignments, setLoadingAssignments] = useState(false)
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignForm] = Form.useForm<{
    kpiId: string
    employeeId: string
    assignedTarget: number
    weightPercent?: number
  }>()
  const [savingAssign, setSavingAssign] = useState(false)

  // measure
  const [measuring, setMeasuring] = useState<KpiAssignmentResponse | null>(null)
  const [measureForm] = Form.useForm<{ actualValue: number; periodLabel?: string; note?: string }>()
  const [savingMeasure, setSavingMeasure] = useState(false)

  // history drawer
  const [historyFor, setHistoryFor] = useState<KpiAssignmentResponse | null>(null)
  const [history, setHistory] = useState<KpiResultResponse[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([kpisApi.list(), performanceApi.cycles(), employeesApi.list({ size: 500 })])
      .then(([k, c, e]) => {
        setKpis(k)
        setCycles(c)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
        if (!cycleId && c.length) setCycleId(c[0].id)
      })
      .catch(() => message.error('Failed to load KPIs'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const loadAssignments = (cid: string) => {
    setLoadingAssignments(true)
    kpisApi
      .assignments({ cycleId: cid })
      .then(setAssignments)
      .catch(() => message.error('Failed to load KPI assignments'))
      .finally(() => setLoadingAssignments(false))
  }
  useEffect(() => {
    if (cycleId) loadAssignments(cycleId)
  }, [cycleId]) // eslint-disable-line react-hooks/exhaustive-deps

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  // ── Library editor ────────────────────────────────────────────────────────

  const openKpiEditor = (k?: KpiResponse) => {
    setEditingKpi(k ?? null)
    kpiForm.setFieldsValue(
      k
        ? { ...k }
        : {
            kpiCode: '',
            kpiName: '',
            frequency: 'ANNUAL' as KpiFrequency,
            scoringModel: 'LINEAR' as KpiScoringModel,
            dataSource: 'MANUAL' as KpiDataSource,
            active: true,
          },
    )
    setKpiOpen(true)
  }

  const saveKpi = async () => {
    const v = await kpiForm.validateFields()
    setSavingKpi(true)
    try {
      if (editingKpi) await kpisApi.update(editingKpi.id, v)
      else await kpisApi.create(v)
      message.success(editingKpi ? 'KPI updated' : 'KPI created')
      setKpiOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSavingKpi(false)
    }
  }

  // ── Assignment actions ────────────────────────────────────────────────────

  const saveAssign = async () => {
    if (!cycleId) return
    const v = await assignForm.validateFields()
    setSavingAssign(true)
    try {
      await kpisApi.assign({ ...v, cycleId })
      message.success('KPI assigned')
      setAssignOpen(false)
      assignForm.resetFields()
      loadAssignments(cycleId)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Assignment failed')
    } finally {
      setSavingAssign(false)
    }
  }

  const saveMeasure = async () => {
    if (!measuring || !cycleId) return
    const v = await measureForm.validateFields()
    setSavingMeasure(true)
    try {
      const updated = await kpisApi.measure(measuring.id, v)
      message.success(
        `Recorded — achievement ${updated.achievementPercent ?? '—'}%, rating ${updated.rating ?? '—'}`,
      )
      setMeasuring(null)
      measureForm.resetFields()
      loadAssignments(cycleId)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Measurement failed')
    } finally {
      setSavingMeasure(false)
    }
  }

  const cancelAssignment = (a: KpiAssignmentResponse) => {
    Modal.confirm({
      title: 'Cancel this KPI assignment?',
      content: `${a.kpiName ?? a.kpiCode} for ${a.employeeName ?? a.employeeId}`,
      okText: 'Cancel assignment',
      okButtonProps: { danger: true },
      onOk: async () => {
        await kpisApi.cancelAssignment(a.id)
        message.success('Assignment cancelled')
        if (cycleId) loadAssignments(cycleId)
      },
    })
  }

  const openHistory = (a: KpiAssignmentResponse) => {
    setHistoryFor(a)
    setLoadingHistory(true)
    kpisApi
      .history(a.id)
      .then(setHistory)
      .catch(() => message.error('Failed to load history'))
      .finally(() => setLoadingHistory(false))
  }

  // ── Columns ───────────────────────────────────────────────────────────────

  const kpiColumns: ColumnsType<KpiResponse> = [
    { title: 'Code', dataIndex: 'kpiCode', width: 130, render: (v) => <Text code>{v}</Text> },
    { title: 'Name', dataIndex: 'kpiName' },
    { title: 'Category', dataIndex: 'category', width: 140, render: (v) => v ?? '—' },
    { title: 'Unit', dataIndex: 'measurementUnit', width: 90, render: (v) => v ?? '—' },
    {
      title: 'Default target',
      dataIndex: 'defaultTarget',
      width: 120,
      align: 'right',
      render: (v) => (v != null ? v : '—'),
    },
    {
      title: 'Frequency',
      dataIndex: 'frequency',
      width: 110,
      render: (v: KpiFrequency) => KPI_FREQUENCY_LABEL[v] ?? v,
    },
    {
      title: 'Scoring',
      dataIndex: 'scoringModel',
      width: 110,
      render: (v: KpiScoringModel) => <Tag color={v === 'LINEAR' ? 'blue' : 'purple'}>{v}</Tag>,
    },
    {
      title: 'Source',
      dataIndex: 'dataSource',
      width: 120,
      render: (v: KpiDataSource) => KPI_DATA_SOURCE_LABEL[v] ?? v,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
    ...(canAdmin
      ? [
          {
            title: '',
            key: 'actions',
            width: 80,
            render: (_: unknown, k: KpiResponse) => (
              <Button size="small" onClick={() => openKpiEditor(k)}>
                Edit
              </Button>
            ),
          } as ColumnsType<KpiResponse>[number],
        ]
      : []),
  ]

  const assignmentColumns: ColumnsType<KpiAssignmentResponse> = [
    {
      title: 'KPI',
      key: 'kpi',
      render: (_, a) => (
        <Space direction="vertical" size={0}>
          <Text strong>{a.kpiName ?? a.kpiCode ?? a.kpiId}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {a.kpiCode}
            {a.measurementUnit ? ` · ${a.measurementUnit}` : ''}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Employee',
      key: 'employee',
      render: (_, a) => {
        const e = empMap.get(a.employeeId)
        return a.employeeName ?? (e ? `${e.employeeNo} ${e.firstName} ${e.lastName}` : a.employeeId)
      },
    },
    { title: 'Target', dataIndex: 'assignedTarget', width: 100, align: 'right' },
    {
      title: 'Weight %',
      dataIndex: 'weightPercent',
      width: 90,
      align: 'right',
      render: (v) => (v != null && Number(v) > 0 ? v : '—'),
    },
    {
      title: 'Actual',
      dataIndex: 'actualValue',
      width: 100,
      align: 'right',
      render: (v) => (v != null ? v : '—'),
    },
    {
      title: 'Achievement',
      dataIndex: 'achievementPercent',
      width: 110,
      align: 'right',
      render: (v) => (v != null ? `${v}%` : '—'),
    },
    { title: 'Rating', dataIndex: 'rating', width: 90, render: (v) => ratingTag(v) },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 220,
      render: (_, a) => (
        <Space size={4}>
          {canAssign && a.status !== 'CANCELLED' && (
            <Button
              size="small"
              type="primary"
              onClick={() => {
                setMeasuring(a)
                measureForm.resetFields()
              }}
            >
              Measure
            </Button>
          )}
          <Button size="small" onClick={() => openHistory(a)}>
            History
          </Button>
          {canAssign && a.status !== 'CANCELLED' && a.status !== 'MEASURED' && (
            <Button size="small" danger onClick={() => cancelAssignment(a)}>
              Cancel
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const historyColumns: ColumnsType<KpiResultResponse> = [
    { title: 'Period', dataIndex: 'periodLabel', render: (v) => v ?? '—' },
    { title: 'Actual', dataIndex: 'actualValue', align: 'right', width: 90 },
    {
      title: 'Achievement',
      dataIndex: 'achievementPercent',
      align: 'right',
      width: 110,
      render: (v) => (v != null ? `${v}%` : '—'),
    },
    { title: 'Rating', dataIndex: 'rating', width: 90, render: (v) => ratingTag(v) },
    { title: 'Note', dataIndex: 'note', render: (v) => v ?? '—' },
    { title: 'By', dataIndex: 'recordedBy', width: 110, render: (v) => v ?? '—' },
    {
      title: 'When',
      dataIndex: 'recordedAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            KPIs
          </Title>
          <Text type="secondary">
            KPI library and per-cycle employee assignments. Ratings compute automatically from
            actual vs target.
          </Text>
        </Col>
      </Row>

      <Tabs
        defaultActiveKey="assignments"
        items={[
          {
            key: 'assignments',
            label: 'Assignments',
            children: (
              <Card size="small">
                <Space style={{ marginBottom: 12 }} wrap>
                  <Select
                    style={{ minWidth: 260 }}
                    placeholder="Review cycle"
                    value={cycleId}
                    onChange={setCycleId}
                    options={cycles.map((c) => ({ value: c.id, label: c.name }))}
                  />
                  {canAssign && (
                    <Button
                      type="primary"
                      disabled={!cycleId}
                      onClick={() => {
                        assignForm.resetFields()
                        setAssignOpen(true)
                      }}
                    >
                      Assign KPI
                    </Button>
                  )}
                </Space>
                <Table
                  rowKey="id"
                  size="small"
                  loading={loadingAssignments}
                  columns={assignmentColumns}
                  dataSource={assignments}
                  pagination={{ pageSize: 20, hideOnSinglePage: true }}
                />
              </Card>
            ),
          },
          {
            key: 'library',
            label: `Library (${kpis.length})`,
            children: (
              <Card size="small">
                {canAdmin && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => openKpiEditor()}
                  >
                    New KPI
                  </Button>
                )}
                <Table
                  rowKey="id"
                  size="small"
                  columns={kpiColumns}
                  dataSource={kpis}
                  pagination={{ pageSize: 20, hideOnSinglePage: true }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* Library editor */}
      <Modal
        title={editingKpi ? `Edit KPI — ${editingKpi.kpiCode}` : 'New KPI'}
        open={kpiOpen}
        onCancel={() => setKpiOpen(false)}
        onOk={saveKpi}
        confirmLoading={savingKpi}
        okText="Save"
        width={640}
        destroyOnClose
      >
        <Form form={kpiForm} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="kpiCode" label="Code" rules={[{ required: true, max: 40 }]}>
                <Input placeholder="SALES_TARGET" disabled={!!editingKpi} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="kpiName" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input placeholder="Quarterly sales target" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="category" label="Category">
                <Input placeholder="Sales" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="measurementUnit" label="Unit">
                <Input placeholder="AZN / % / count" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="defaultTarget" label="Default target">
                <InputNumber style={{ width: '100%' }} min={0} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="frequency" label="Frequency" rules={[{ required: true }]}>
                <Select
                  options={(Object.keys(KPI_FREQUENCY_LABEL) as KpiFrequency[]).map((k) => ({
                    value: k,
                    label: KPI_FREQUENCY_LABEL[k],
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="scoringModel" label="Scoring model" rules={[{ required: true }]}>
                <Select
                  options={(Object.keys(KPI_SCORING_LABEL) as KpiScoringModel[]).map((k) => ({
                    value: k,
                    label: KPI_SCORING_LABEL[k],
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="dataSource" label="Data source" rules={[{ required: true }]}>
                <Select
                  options={(Object.keys(KPI_DATA_SOURCE_LABEL) as KpiDataSource[]).map((k) => ({
                    value: k,
                    label: KPI_DATA_SOURCE_LABEL[k],
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* Assign modal */}
      <Modal
        title="Assign KPI"
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={saveAssign}
        confirmLoading={savingAssign}
        okText="Assign"
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item name="kpiId" label="KPI" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={kpis
                .filter((k) => k.active)
                .map((k) => ({ value: k.id, label: `${k.kpiCode} — ${k.kpiName}` }))}
              onChange={(id) => {
                const k = kpis.find((x) => x.id === id)
                if (k?.defaultTarget != null)
                  assignForm.setFieldValue('assignedTarget', k.defaultTarget)
              }}
            />
          </Form.Item>
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="assignedTarget"
                label="Target"
                rules={[{ required: true, type: 'number', min: 0.01 }]}
              >
                <InputNumber style={{ width: '100%' }} min={0.01} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="weightPercent" label="Weight % (within KPI section)">
                <InputNumber style={{ width: '100%' }} min={0} max={100} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* Measure modal */}
      <Modal
        title={
          measuring
            ? `Record measurement — ${measuring.kpiName ?? measuring.kpiCode} (target ${measuring.assignedTarget}${measuring.measurementUnit ? ' ' + measuring.measurementUnit : ''})`
            : ''
        }
        open={!!measuring}
        onCancel={() => setMeasuring(null)}
        onOk={saveMeasure}
        confirmLoading={savingMeasure}
        okText="Record"
        destroyOnClose
      >
        <Form form={measureForm} layout="vertical">
          <Form.Item
            name="actualValue"
            label="Actual value"
            rules={[{ required: true, type: 'number' }]}
          >
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="periodLabel" label="Period (e.g. 2026-Q2)">
            <Input maxLength={40} />
          </Form.Item>
          <Form.Item name="note" label="Note">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* History drawer */}
      <Drawer
        title={
          historyFor ? `Measurement history — ${historyFor.kpiName ?? historyFor.kpiCode}` : ''
        }
        open={!!historyFor}
        onClose={() => setHistoryFor(null)}
        width={720}
      >
        <Table
          rowKey="id"
          size="small"
          loading={loadingHistory}
          columns={historyColumns}
          dataSource={history}
          pagination={false}
        />
      </Drawer>
    </div>
  )
}
