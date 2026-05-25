import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  calibrationApi,
  type CalibrationBoardEntry,
  type CalibrationSession,
  type CalibrationSessionStatus,
} from '../../api/calibrationApi'

const { Title, Text } = Typography

const STATUS_COLOR: Record<CalibrationSessionStatus, string> = {
  SCHEDULED: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
}

const RECOMMENDATIONS = [
  'PROMOTE',
  'RETAIN',
  'DEVELOP',
  'PIP',
  'SEPARATE',
]

const BANDS = [
  '5 - Exceptional',
  '4 - Exceeds',
  '3 - Meets',
  '2 - Needs Improvement',
  '1 - Unsatisfactory',
]

// ── Types for the editable board state ──────────────────────────────────────

interface BoardRowEdit {
  finalRating?: number | null
  finalBand?: string | null
  recommendation?: string | null
  bonusPercent?: number | null
  calibrationNotes?: string | null
  saving?: boolean
}

// ────────────────────────────────────────────────────────────────────────────

export function CalibrationPage() {
  const { cycleId } = useParams<{ cycleId: string }>()
  const { message } = AntdApp.useApp()

  // Sessions state
  const [sessions, setSessions] = useState<CalibrationSession[]>([])
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [activeSession, setActiveSession] = useState<CalibrationSession | null>(null)

  // Board state
  const [cycleName, setCycleName] = useState<string>('')
  const [boardEntries, setBoardEntries] = useState<CalibrationBoardEntry[]>([])
  const [distribution, setDistribution] = useState<Record<string, number>>({})
  const [boardLoading, setBoardLoading] = useState(false)

  // Per-row edit state, keyed by reviewId
  const [edits, setEdits] = useState<Record<string, BoardRowEdit>>({})

  // Create-session modal
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [createForm] = Form.useForm()
  const [creating, setCreating] = useState(false)

  // ── Data loading ─────────────────────────────────────────────────────────

  const loadSessions = () => {
    if (!cycleId) return
    setSessionsLoading(true)
    calibrationApi
      .listSessions(cycleId)
      .then((data) => {
        setSessions(data)
        // The active session is the most recent IN_PROGRESS one (or null)
        const inProgress = data.find((s) => s.status === 'IN_PROGRESS') ?? null
        setActiveSession(inProgress)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load sessions'),
      )
      .finally(() => setSessionsLoading(false))
  }

  const loadBoard = () => {
    if (!cycleId) return
    setBoardLoading(true)
    calibrationApi
      .getBoard(cycleId)
      .then((data) => {
        setCycleName(data.cycleName)
        setBoardEntries(data.entries)
        setDistribution(data.ratingDistribution)
        // Initialise edits from board data
        const init: Record<string, BoardRowEdit> = {}
        for (const e of data.entries) {
          init[e.reviewId] = {
            finalRating: e.finalRating ?? undefined,
            finalBand: e.finalBand ?? undefined,
            recommendation: e.recommendation ?? undefined,
            bonusPercent: e.bonusPercent ?? undefined,
            calibrationNotes: e.calibrationNotes ?? undefined,
          }
        }
        setEdits(init)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load board'),
      )
      .finally(() => setBoardLoading(false))
  }

  useEffect(() => {
    loadSessions()
    loadBoard()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId])

  // ── Session actions ──────────────────────────────────────────────────────

  const handleCreate = async () => {
    if (!cycleId) return
    try {
      const values = await createForm.validateFields()
      setCreating(true)
      await calibrationApi.createSession(cycleId, {
        name: values.name,
        scheduledAt: values.scheduledAt ? values.scheduledAt.toISOString() : undefined,
        facilitator: values.facilitator,
        notes: values.notes,
      })
      message.success('Session created')
      setShowCreateModal(false)
      createForm.resetFields()
      loadSessions()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; errorFields?: unknown[] }
      if (!e.errorFields) {
        message.error(e?.response?.data?.message ?? 'Failed to create session')
      }
    } finally {
      setCreating(false)
    }
  }

  const handleStart = async (session: CalibrationSession) => {
    try {
      await calibrationApi.startSession(session.id)
      message.success(`Session "${session.name}" started`)
      loadSessions()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Failed to start session')
    }
  }

  const handleComplete = async (session: CalibrationSession) => {
    try {
      await calibrationApi.completeSession(session.id)
      message.success(`Session "${session.name}" completed`)
      loadSessions()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Failed to complete session')
    }
  }

  // ── Board row save ───────────────────────────────────────────────────────

  const handleSaveRow = async (entry: CalibrationBoardEntry) => {
    if (!activeSession) {
      message.warning('No IN_PROGRESS session — start a session first')
      return
    }
    const edit = edits[entry.reviewId] ?? {}
    setEdits((prev) => ({ ...prev, [entry.reviewId]: { ...edit, saving: true } }))
    try {
      await calibrationApi.calibrateReview(activeSession.id, entry.reviewId, {
        finalRating: edit.finalRating ?? undefined,
        finalBand: edit.finalBand ?? undefined,
        recommendation: edit.recommendation ?? undefined,
        bonusPercent: edit.bonusPercent ?? undefined,
        calibrationNotes: edit.calibrationNotes ?? undefined,
      })
      message.success(`Saved — ${entry.employeeName}`)
      loadBoard()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Failed to save calibration')
    } finally {
      setEdits((prev) => ({ ...prev, [entry.reviewId]: { ...edit, saving: false } }))
    }
  }

  const updateEdit = (reviewId: string, patch: Partial<BoardRowEdit>) => {
    setEdits((prev) => ({ ...prev, [reviewId]: { ...(prev[reviewId] ?? {}), ...patch } }))
  }

  // ── Sessions table columns ───────────────────────────────────────────────

  const sessionColumns: ColumnsType<CalibrationSession> = [
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Scheduled',
      dataIndex: 'scheduledAt',
      width: 180,
      render: (v: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'),
    },
    { title: 'Facilitator', dataIndex: 'facilitator', width: 160 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: CalibrationSessionStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: '',
      width: 200,
      render: (_, s) => (
        <Space size={4}>
          {s.status === 'SCHEDULED' && (
            <Button size="small" type="primary" onClick={() => handleStart(s)}>
              Start
            </Button>
          )}
          {s.status === 'IN_PROGRESS' && (
            <Button size="small" onClick={() => handleComplete(s)}>
              Complete
            </Button>
          )}
        </Space>
      ),
    },
  ]

  // ── Board table columns ──────────────────────────────────────────────────

  const boardColumns: ColumnsType<CalibrationBoardEntry> = [
    { title: 'Employee', dataIndex: 'employeeName', width: 180, fixed: 'left' },
    { title: 'Department', dataIndex: 'department', width: 160 },
    {
      title: 'Self',
      dataIndex: 'selfRating',
      width: 80,
      align: 'center',
      render: (v: number | null) => (v != null ? v.toFixed(2) : '—'),
    },
    {
      title: 'Manager',
      dataIndex: 'managerRating',
      width: 90,
      align: 'center',
      render: (v: number | null) => (v != null ? v.toFixed(2) : '—'),
    },
    {
      title: 'Final Rating',
      width: 130,
      render: (_, e) => (
        <InputNumber
          min={0}
          max={5}
          step={0.1}
          precision={2}
          size="small"
          style={{ width: 110 }}
          value={edits[e.reviewId]?.finalRating ?? undefined}
          onChange={(v) => updateEdit(e.reviewId, { finalRating: v ?? undefined })}
          disabled={!activeSession}
        />
      ),
    },
    {
      title: 'Final Band',
      width: 200,
      render: (_, e) => (
        <Select
          size="small"
          allowClear
          style={{ width: 180 }}
          value={edits[e.reviewId]?.finalBand ?? undefined}
          onChange={(v) => updateEdit(e.reviewId, { finalBand: v ?? undefined })}
          options={BANDS.map((b) => ({ value: b, label: b }))}
          disabled={!activeSession}
        />
      ),
    },
    {
      title: 'Recommendation',
      width: 160,
      render: (_, e) => (
        <Select
          size="small"
          allowClear
          style={{ width: 140 }}
          value={edits[e.reviewId]?.recommendation ?? undefined}
          onChange={(v) => updateEdit(e.reviewId, { recommendation: v ?? undefined })}
          options={RECOMMENDATIONS.map((r) => ({ value: r, label: r }))}
          disabled={!activeSession}
        />
      ),
    },
    {
      title: 'Bonus %',
      width: 110,
      render: (_, e) => (
        <InputNumber
          min={0}
          max={100}
          step={0.5}
          precision={2}
          size="small"
          style={{ width: 90 }}
          value={edits[e.reviewId]?.bonusPercent ?? undefined}
          onChange={(v) => updateEdit(e.reviewId, { bonusPercent: v ?? undefined })}
          disabled={!activeSession}
        />
      ),
    },
    {
      title: 'Notes',
      render: (_, e) => (
        <Input
          size="small"
          value={edits[e.reviewId]?.calibrationNotes ?? ''}
          onChange={(ev) => updateEdit(e.reviewId, { calibrationNotes: ev.target.value })}
          disabled={!activeSession}
        />
      ),
    },
    {
      title: '',
      width: 80,
      fixed: 'right',
      render: (_, e) => (
        <Button
          size="small"
          type="primary"
          loading={edits[e.reviewId]?.saving}
          onClick={() => handleSaveRow(e)}
          disabled={!activeSession}
        >
          Save
        </Button>
      ),
    },
  ]

  // ── Rating distribution tags ─────────────────────────────────────────────

  const distColors: Record<string, string> = {
    'Exceptional': 'gold',
    'Exceeds': 'green',
    'Meets': 'blue',
    'Needs Improvement': 'orange',
    'Unsatisfactory': 'red',
    'Unrated': 'default',
  }

  const distColorFor = (band: string) => {
    for (const [key, color] of Object.entries(distColors)) {
      if (band.includes(key)) return color
    }
    return 'default'
  }

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      {/* Header */}
      <Card
        title={
          <Title level={4} style={{ margin: 0 }}>
            Calibration — {cycleName || cycleId}
          </Title>
        }
        extra={
          <Button type="primary" onClick={() => setShowCreateModal(true)}>
            New Session
          </Button>
        }
      >
        {activeSession ? (
          <Text type="success">
            Active session: <strong>{activeSession.name}</strong> (IN_PROGRESS) — changes below
            will be applied to this session.
          </Text>
        ) : (
          <Text type="secondary">
            No active session. Start a session to enable calibration edits.
          </Text>
        )}
      </Card>

      {/* Sessions list */}
      <Card title="Sessions">
        <Table
          rowKey="id"
          loading={sessionsLoading}
          columns={sessionColumns}
          dataSource={sessions}
          pagination={false}
          size="small"
        />
      </Card>

      {/* Rating distribution */}
      {Object.keys(distribution).length > 0 && (
        <Card title="Rating Distribution">
          <Space wrap>
            {Object.entries(distribution).map(([band, count]) => (
              <Tag key={band} color={distColorFor(band)} style={{ fontSize: 13, padding: '2px 10px' }}>
                {band}: <strong>{count}</strong>
              </Tag>
            ))}
          </Space>
        </Card>
      )}

      {/* Calibration board */}
      <Card title={`Calibration Board (${boardEntries.length} reviews)`}>
        <Table
          rowKey="reviewId"
          loading={boardLoading}
          columns={boardColumns}
          dataSource={boardEntries}
          pagination={{ pageSize: 50 }}
          scroll={{ x: 'max-content' }}
          size="small"
        />
      </Card>

      {/* Create session modal */}
      <Modal
        title="New Calibration Session"
        open={showCreateModal}
        onOk={handleCreate}
        onCancel={() => {
          setShowCreateModal(false)
          createForm.resetFields()
        }}
        confirmLoading={creating}
        okText="Create"
      >
        <Form form={createForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Session name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="e.g. Q4 2025 Calibration Meeting" />
          </Form.Item>
          <Form.Item name="scheduledAt" label="Scheduled at">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="facilitator" label="Facilitator">
            <Input placeholder="HR facilitator name" />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
