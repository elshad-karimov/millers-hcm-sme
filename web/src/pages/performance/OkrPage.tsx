// HCM_12 M391 — OKR objectives + key results + check-ins (PRD §5.6 / §8).
// Six alignment levels (Company → Individual) via parent links; objective progress
// rolls up from key results server-side (weighted average — see OkrMath).

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
  Progress,
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
  OKR_LEVEL_LABEL,
  okrsApi,
  performanceApi,
  type OkrCheckInResponse,
  type OkrConfidence,
  type OkrKeyResultRequest,
  type OkrKeyResultResponse,
  type OkrLevel,
  type OkrMeasurementType,
  type OkrObjectiveRequest,
  type OkrObjectiveResponse,
  type ReviewCycle,
} from '../../api/performance'
import { EmployeePicker } from '../../components/EmployeePicker'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

const LEVEL_COLOR: Record<OkrLevel, string> = {
  COMPANY: 'magenta',
  LEGAL_ENTITY: 'volcano',
  BUSINESS_UNIT: 'orange',
  DEPARTMENT: 'gold',
  TEAM: 'cyan',
  INDIVIDUAL: 'blue',
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  CLOSED: 'green',
  CANCELLED: 'default',
  DONE: 'green',
  AT_RISK: 'red',
}

const CONFIDENCE_COLOR: Record<OkrConfidence, string> = {
  HIGH: 'green',
  MEDIUM: 'gold',
  LOW: 'red',
}

const LEVEL_OPTIONS = (Object.keys(OKR_LEVEL_LABEL) as OkrLevel[]).map((k) => ({
  value: k,
  label: OKR_LEVEL_LABEL[k],
}))

const CONFIDENCE_OPTIONS = (['HIGH', 'MEDIUM', 'LOW'] as OkrConfidence[]).map((c) => ({
  value: c,
  label: c.charAt(0) + c.slice(1).toLowerCase(),
}))

type ObjectiveFormValues = Omit<
  OkrObjectiveRequest,
  'periodStart' | 'periodEnd' | 'dueDate'
> & {
  periodStart?: dayjs.Dayjs | null
  periodEnd?: dayjs.Dayjs | null
  dueDate?: dayjs.Dayjs | null
}

type KrFormValues = Omit<OkrKeyResultRequest, 'dueDate'> & { dueDate?: dayjs.Dayjs | null }

export function OkrPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [rows, setRows] = useState<OkrObjectiveResponse[]>([])
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [loading, setLoading] = useState(true)
  const [levelFilter, setLevelFilter] = useState<OkrLevel | undefined>()
  const [cycleFilter, setCycleFilter] = useState<string | undefined>()

  // objective editor
  const [objOpen, setObjOpen] = useState(false)
  const [editingObj, setEditingObj] = useState<OkrObjectiveResponse | null>(null)
  const [savingObj, setSavingObj] = useState(false)
  const [objForm] = Form.useForm<ObjectiveFormValues>()

  // key result editor
  const [krFor, setKrFor] = useState<OkrObjectiveResponse | null>(null)
  const [editingKr, setEditingKr] = useState<OkrKeyResultResponse | null>(null)
  const [savingKr, setSavingKr] = useState(false)
  const [krForm] = Form.useForm<KrFormValues>()

  // check-in
  const [checkInFor, setCheckInFor] = useState<OkrObjectiveResponse | null>(null)
  const [savingCheckIn, setSavingCheckIn] = useState(false)
  const [checkInForm] = Form.useForm<{
    keyResultId?: string
    currentValue?: number
    confidence?: OkrConfidence
    comment?: string
  }>()

  // history drawer
  const [historyFor, setHistoryFor] = useState<OkrObjectiveResponse | null>(null)
  const [history, setHistory] = useState<OkrCheckInResponse[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([
      okrsApi.list({ cycleId: cycleFilter, level: levelFilter }),
      performanceApi.cycles(),
    ])
      .then(([o, c]) => {
        setRows(o)
        setCycles(c)
      })
      .catch(() => message.error('Failed to load OKRs'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [levelFilter, cycleFilter]) // eslint-disable-line react-hooks/exhaustive-deps

  const objectiveOptions = useMemo(
    () =>
      rows.map((o) => ({
        value: o.id,
        label: `[${OKR_LEVEL_LABEL[o.okrLevel]}] ${o.title}`,
      })),
    [rows],
  )

  // ── Objective save ────────────────────────────────────────────────────────

  const openObjEditor = (o?: OkrObjectiveResponse) => {
    setEditingObj(o ?? null)
    objForm.resetFields()
    if (o) {
      objForm.setFieldsValue({
        title: o.title,
        description: o.description ?? undefined,
        okrLevel: o.okrLevel,
        parentId: o.parentId,
        ownerEmployeeId: o.ownerEmployeeId,
        orgUnitId: o.orgUnitId,
        legalEntityId: o.legalEntityId,
        cycleId: o.cycleId,
        confidence: o.confidence,
        periodStart: o.periodStart ? dayjs(o.periodStart) : null,
        periodEnd: o.periodEnd ? dayjs(o.periodEnd) : null,
        dueDate: o.dueDate ? dayjs(o.dueDate) : null,
      })
    } else {
      objForm.setFieldsValue({ okrLevel: 'INDIVIDUAL', cycleId: cycleFilter })
    }
    setObjOpen(true)
  }

  const saveObj = async () => {
    const v = await objForm.validateFields()
    const req: OkrObjectiveRequest = {
      ...v,
      periodStart: v.periodStart ? v.periodStart.format('YYYY-MM-DD') : null,
      periodEnd: v.periodEnd ? v.periodEnd.format('YYYY-MM-DD') : null,
      dueDate: v.dueDate ? v.dueDate.format('YYYY-MM-DD') : null,
    }
    setSavingObj(true)
    try {
      if (editingObj) await okrsApi.update(editingObj.id, req)
      else await okrsApi.create(req)
      message.success(editingObj ? 'Objective updated' : 'Objective created')
      setObjOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSavingObj(false)
    }
  }

  const changeStatus = (o: OkrObjectiveResponse, status: 'CLOSED' | 'CANCELLED' | 'ACTIVE') => {
    Modal.confirm({
      title: `${status === 'ACTIVE' ? 'Reopen' : status === 'CLOSED' ? 'Close' : 'Cancel'} this objective?`,
      content: o.title,
      onOk: async () => {
        await okrsApi.changeStatus(o.id, status)
        message.success(`Objective ${status.toLowerCase()}`)
        load()
      },
    })
  }

  // ── Key result save ───────────────────────────────────────────────────────

  const openKrEditor = (obj: OkrObjectiveResponse, kr?: OkrKeyResultResponse) => {
    setKrFor(obj)
    setEditingKr(kr ?? null)
    krForm.resetFields()
    if (kr) {
      krForm.setFieldsValue({ ...kr, dueDate: kr.dueDate ? dayjs(kr.dueDate) : null })
    } else {
      krForm.setFieldsValue({ measurementType: 'NUMBER', baselineValue: 0, weightPercent: 0 })
    }
  }

  const saveKr = async () => {
    if (!krFor) return
    const v = await krForm.validateFields()
    const req: OkrKeyResultRequest = {
      ...v,
      dueDate: v.dueDate ? v.dueDate.format('YYYY-MM-DD') : null,
    }
    setSavingKr(true)
    try {
      if (editingKr) await okrsApi.updateKeyResult(editingKr.id, req)
      else await okrsApi.addKeyResult(krFor.id, req)
      message.success(editingKr ? 'Key result updated' : 'Key result added')
      setKrFor(null)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSavingKr(false)
    }
  }

  // ── Check-in ──────────────────────────────────────────────────────────────

  const saveCheckIn = async () => {
    if (!checkInFor) return
    const v = await checkInForm.validateFields()
    setSavingCheckIn(true)
    try {
      const updated = await okrsApi.checkIn(checkInFor.id, v)
      message.success(`Check-in recorded — progress ${updated.progressPercent}%`)
      setCheckInFor(null)
      checkInForm.resetFields()
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Check-in failed')
    } finally {
      setSavingCheckIn(false)
    }
  }

  const openHistory = (o: OkrObjectiveResponse) => {
    setHistoryFor(o)
    setLoadingHistory(true)
    okrsApi
      .checkIns(o.id)
      .then(setHistory)
      .catch(() => message.error('Failed to load check-ins'))
      .finally(() => setLoadingHistory(false))
  }

  // ── Render ────────────────────────────────────────────────────────────────

  const columns: ColumnsType<OkrObjectiveResponse> = [
    {
      title: 'Objective',
      key: 'title',
      render: (_, o) => (
        <Space direction="vertical" size={0}>
          <Text strong>{o.title}</Text>
          {o.parentTitle && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              ↑ {o.parentTitle}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Level',
      dataIndex: 'okrLevel',
      width: 120,
      render: (v: OkrLevel) => <Tag color={LEVEL_COLOR[v]}>{OKR_LEVEL_LABEL[v]}</Tag>,
    },
    {
      title: 'Owner',
      key: 'owner',
      width: 170,
      render: (_, o) => o.ownerName ?? '—',
    },
    {
      title: 'Progress',
      key: 'progress',
      width: 170,
      render: (_, o) => <Progress percent={Number(o.progressPercent)} size="small" />,
    },
    {
      title: 'Confidence',
      dataIndex: 'confidence',
      width: 100,
      render: (v: OkrConfidence | null) =>
        v ? <Tag color={CONFIDENCE_COLOR[v]}>{v}</Tag> : '—',
    },
    {
      title: 'Due',
      dataIndex: 'dueDate',
      width: 110,
      render: (v) => v ?? '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 260,
      render: (_, o) => (
        <Space size={4} wrap>
          {canWrite && o.status === 'ACTIVE' && (
            <>
              <Button
                size="small"
                type="primary"
                onClick={() => {
                  setCheckInFor(o)
                  checkInForm.resetFields()
                }}
              >
                Check-in
              </Button>
              <Button size="small" onClick={() => openKrEditor(o)}>
                + KR
              </Button>
              <Button size="small" onClick={() => openObjEditor(o)}>
                Edit
              </Button>
              <Button size="small" onClick={() => changeStatus(o, 'CLOSED')}>
                Close
              </Button>
            </>
          )}
          {canWrite && o.status !== 'ACTIVE' && (
            <Button size="small" onClick={() => changeStatus(o, 'ACTIVE')}>
              Reopen
            </Button>
          )}
          <Button size="small" onClick={() => openHistory(o)}>
            History
          </Button>
        </Space>
      ),
    },
  ]

  const krColumns: ColumnsType<OkrKeyResultResponse> = [
    { title: 'Key result', dataIndex: 'title' },
    { title: 'Type', dataIndex: 'measurementType', width: 100 },
    { title: 'Baseline', dataIndex: 'baselineValue', width: 90, align: 'right' },
    { title: 'Target', dataIndex: 'targetValue', width: 90, align: 'right' },
    {
      title: 'Current',
      dataIndex: 'currentValue',
      width: 90,
      align: 'right',
      render: (v) => (v != null ? v : '—'),
    },
    {
      title: 'Progress',
      key: 'progress',
      width: 150,
      render: (_, kr) => <Progress percent={Number(kr.progressPercent)} size="small" />,
    },
    {
      title: 'Weight',
      dataIndex: 'weightPercent',
      width: 80,
      align: 'right',
      render: (v) => (Number(v) > 0 ? `${v}%` : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
  ]

  if (loading && !rows.length) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            OKRs
          </Title>
          <Text type="secondary">
            Objectives &amp; key results — Company → Individual alignment; progress rolls up from
            key-result check-ins.
          </Text>
        </Col>
        <Col>
          {canWrite && (
            <Button type="primary" onClick={() => openObjEditor()}>
              New objective
            </Button>
          )}
        </Col>
      </Row>

      <Card size="small">
        <Space style={{ marginBottom: 12 }} wrap>
          <Select
            allowClear
            style={{ minWidth: 180 }}
            placeholder="All levels"
            value={levelFilter}
            onChange={setLevelFilter}
            options={LEVEL_OPTIONS}
          />
          <Select
            allowClear
            style={{ minWidth: 240 }}
            placeholder="All cycles"
            value={cycleFilter}
            onChange={setCycleFilter}
            options={cycles.map((c) => ({ value: c.id, label: c.name }))}
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          expandable={{
            expandedRowRender: (o) => (
              <Table
                rowKey="id"
                size="small"
                columns={
                  canWrite && o.status === 'ACTIVE'
                    ? [
                        ...krColumns,
                        {
                          title: '',
                          key: 'kr-actions',
                          width: 70,
                          render: (_: unknown, kr: OkrKeyResultResponse) => (
                            <Button size="small" onClick={() => openKrEditor(o, kr)}>
                              Edit
                            </Button>
                          ),
                        },
                      ]
                    : krColumns
                }
                dataSource={o.keyResults}
                pagination={false}
                locale={{ emptyText: 'No key results yet' }}
              />
            ),
          }}
        />
      </Card>

      {/* Objective editor */}
      <Modal
        title={editingObj ? 'Edit objective' : 'New objective'}
        open={objOpen}
        onCancel={() => setObjOpen(false)}
        onOk={saveObj}
        confirmLoading={savingObj}
        okText="Save"
        width={680}
        destroyOnClose
      >
        <Form form={objForm} layout="vertical">
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 300 }]}>
            <Input placeholder="Increase customer retention" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="okrLevel" label="Level" rules={[{ required: true }]}>
                <Select options={LEVEL_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="parentId" label="Aligns to (parent objective)">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={objectiveOptions.filter((x) => x.value !== editingObj?.id)}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                noStyle
                shouldUpdate={(prev, cur) => prev.okrLevel !== cur.okrLevel}
              >
                {({ getFieldValue }) => (
                  <Form.Item
                    name="ownerEmployeeId"
                    label="Owner"
                    rules={
                      getFieldValue('okrLevel') === 'INDIVIDUAL'
                        ? [{ required: true, message: 'Individual OKRs need an owner' }]
                        : []
                    }
                  >
                    <EmployeePicker allowClear placeholder="Select owner" />
                  </Form.Item>
                )}
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cycleId" label="Review cycle">
                <Select
                  allowClear
                  options={cycles.map((c) => ({ value: c.id, label: c.name }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="periodStart" label="Period start">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="periodEnd" label="Period end">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="dueDate" label="Due date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="confidence" label="Confidence">
            <Select allowClear options={CONFIDENCE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Key result editor */}
      <Modal
        title={
          editingKr
            ? 'Edit key result'
            : krFor
              ? `Add key result — ${krFor.title}`
              : ''
        }
        open={!!krFor}
        onCancel={() => setKrFor(null)}
        onOk={saveKr}
        confirmLoading={savingKr}
        okText="Save"
        width={600}
        destroyOnClose
      >
        <Form form={krForm} layout="vertical">
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 300 }]}>
            <Input placeholder="Raise NPS from 40 to 60" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="measurementType" label="Measurement" rules={[{ required: true }]}>
                <Select
                  options={(
                    ['NUMBER', 'PERCENT', 'CURRENCY', 'BOOLEAN'] as OkrMeasurementType[]
                  ).map((m) => ({ value: m, label: m.charAt(0) + m.slice(1).toLowerCase() }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="baselineValue" label="Baseline">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="targetValue"
                label="Target"
                rules={[{ required: true, type: 'number' }]}
              >
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="weightPercent" label="Weight %">
                <InputNumber style={{ width: '100%' }} min={0} max={100} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="confidence" label="Confidence">
                <Select allowClear options={CONFIDENCE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="dueDate" label="Due date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="ownerEmployeeId" label="KR owner">
            <EmployeePicker allowClear placeholder="Select KR owner" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Check-in modal */}
      <Modal
        title={checkInFor ? `Check-in — ${checkInFor.title}` : ''}
        open={!!checkInFor}
        onCancel={() => setCheckInFor(null)}
        onOk={saveCheckIn}
        confirmLoading={savingCheckIn}
        okText="Record"
        destroyOnClose
      >
        <Form form={checkInForm} layout="vertical">
          <Form.Item
            name="keyResultId"
            label="Key result (leave empty for an objective-level comment)"
          >
            <Select
              allowClear
              options={(checkInFor?.keyResults ?? [])
                .filter((kr) => kr.status !== 'CANCELLED')
                .map((kr) => ({
                  value: kr.id,
                  label: `${kr.title} (current ${kr.currentValue ?? '—'} / target ${kr.targetValue})`,
                }))}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.keyResultId !== cur.keyResultId}
          >
            {({ getFieldValue }) =>
              getFieldValue('keyResultId') ? (
                <Form.Item name="currentValue" label="New current value">
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="confidence" label="Confidence">
            <Select allowClear options={CONFIDENCE_OPTIONS} />
          </Form.Item>
          <Form.Item name="comment" label="Comment">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Check-in history drawer */}
      <Drawer
        title={historyFor ? `Check-ins — ${historyFor.title}` : ''}
        open={!!historyFor}
        onClose={() => setHistoryFor(null)}
        width={560}
      >
        {loadingHistory ? (
          <Spin />
        ) : history.length === 0 ? (
          <Text type="secondary">No check-ins yet.</Text>
        ) : (
          <Timeline
            items={history.map((c) => ({
              children: (
                <Space direction="vertical" size={0}>
                  <Text>
                    {c.oldValue != null || c.newValue != null
                      ? `${c.oldValue ?? '—'} → ${c.newValue ?? '—'}`
                      : 'Comment'}
                    {c.confidence && (
                      <Tag color={CONFIDENCE_COLOR[c.confidence]} style={{ marginLeft: 8 }}>
                        {c.confidence}
                      </Tag>
                    )}
                  </Text>
                  {c.comment && <Text type="secondary">{c.comment}</Text>}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {c.recordedBy ?? '—'} · {new Date(c.recordedAt).toLocaleString()}
                  </Text>
                </Space>
              ),
            }))}
          />
        )}
      </Drawer>
    </div>
  )
}
