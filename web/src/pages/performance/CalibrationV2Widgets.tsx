// M121 — Calibration board v2 widgets.
//
// Three self-contained pieces dropped onto CalibrationPage:
//   - CalibrationDistributionChart  — actual vs target bars per band
//   - CalibrationTargetEditor       — modal that PUTs the per-cycle targets
//   - CalibrationEditLogDrawer      — read-only drawer of in-session edits
//
// Kept separate from the parent page so the diff stays small and the
// widgets can be reused on a manager dashboard later.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Drawer,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import dayjs from 'dayjs'
import {
  calibrationApi,
  type BoardCell,
  type CalibrationEditLog,
} from '../../api/calibrationApi'

const { Text, Title, Paragraph } = Typography

const BAND_LABEL: Record<string, string> = {
  '5 - Exceptional': '5',
  '4 - Exceeds': '4',
  '3 - Meets': '3',
  '2 - Needs Improvement': '2',
  '1 - Unsatisfactory': '1',
}

// ────────────────────────────────────────────────────────────────────────────
// Distribution chart — actual vs target bars
// ────────────────────────────────────────────────────────────────────────────

export function CalibrationDistributionChart({
  cells,
}: {
  cells: Record<string, BoardCell>
}) {
  const data = useMemo(
    () => Object.entries(cells).map(([band, cell]) => ({
      band: BAND_LABEL[band] ?? band,
      actual: cell.actualPercent ?? 0,
      target: cell.targetPercent ?? 0,
      count: cell.actualCount,
    })),
    [cells],
  )

  if (data.length === 0) return null

  return (
    <div style={{ width: '100%', height: 220 }}>
      <ResponsiveContainer>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="band" />
          <YAxis unit="%" />
          <Tooltip
            formatter={(value: unknown, name: unknown) =>
              name === 'count' ? Number(value) : `${Number(value).toFixed(1)}%`
            }
          />
          <Legend />
          <Bar dataKey="actual" fill="#1677ff" name="Actual %" />
          <Bar dataKey="target" fill="#cf9c00" name="Target %" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

// ────────────────────────────────────────────────────────────────────────────
// Delta table — actual vs target with green/red delta column
// ────────────────────────────────────────────────────────────────────────────

type DeltaRow = {
  band: string
  actualCount: number
  actualPercent: number
  targetPercent?: number | null
  delta?: number | null
}

export function CalibrationDeltaTable({
  cells,
}: {
  cells: Record<string, BoardCell>
}) {
  const rows: DeltaRow[] = Object.entries(cells).map(([band, cell]) => ({
    band,
    actualCount: cell.actualCount,
    actualPercent: cell.actualPercent,
    targetPercent: cell.targetPercent,
    delta: cell.delta,
  }))
  return (
    <Table<DeltaRow>
      size="small"
      pagination={false}
      rowKey="band"
      dataSource={rows}
      columns={[
        { title: 'Band', dataIndex: 'band', width: 200 },
        {
          title: 'Actual',
          render: (_: unknown, r: DeltaRow) =>
            `${r.actualCount} (${r.actualPercent?.toFixed?.(1) ?? r.actualPercent}%)`,
          width: 140,
        },
        {
          title: 'Target',
          render: (_: unknown, r: DeltaRow) =>
            r.targetPercent == null ? <Text type="secondary">—</Text> : `${r.targetPercent}%`,
          width: 100,
        },
        {
          title: 'Δ',
          render: (_: unknown, r: DeltaRow) => {
            if (r.delta == null) return <Text type="secondary">—</Text>
            const v = Number(r.delta)
            const colour = Math.abs(v) < 0.5 ? '#888' : v > 0 ? '#52c41a' : '#cf1322'
            return <Text style={{ color: colour }}>{v > 0 ? '+' : ''}{v.toFixed(1)}%</Text>
          },
          width: 100,
        },
      ]}
    />
  )
}

// ────────────────────────────────────────────────────────────────────────────
// Target editor modal
// ────────────────────────────────────────────────────────────────────────────

const CANONICAL_BANDS = [
  '5 - Exceptional',
  '4 - Exceeds',
  '3 - Meets',
  '2 - Needs Improvement',
  '1 - Unsatisfactory',
]

export function CalibrationTargetEditor({
  cycleId,
  open,
  onClose,
  onSaved,
}: {
  cycleId: string
  open: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const { message } = AntdApp.useApp()
  const [values, setValues] = useState<Record<string, number | null>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    calibrationApi
      .getTargets(cycleId)
      .then((data) => setValues(data ?? {}))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load targets'))
  }, [open, cycleId, message])

  const sum = CANONICAL_BANDS.reduce((acc, b) => acc + (values[b] ?? 0), 0)

  const save = async () => {
    setSaving(true)
    try {
      await calibrationApi.saveTargets(cycleId, values)
      message.success('Targets saved')
      onSaved()
      onClose()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="Save targets"
      confirmLoading={saving}
      title="Calibration target distribution"
      width={520}
    >
      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        The expected % of reviews in each band. Sum must not exceed 100%; the
        residual is reported as "Unrated" on the board.
      </Paragraph>
      <Table
        size="small"
        pagination={false}
        rowKey="band"
        dataSource={CANONICAL_BANDS.map((b) => ({ band: b }))}
        columns={[
          { title: 'Band', dataIndex: 'band' },
          {
            title: 'Target %',
            width: 140,
            render: (_: unknown, row: { band: string }) => (
              <InputNumber
                min={0}
                max={100}
                step={1}
                value={values[row.band] ?? undefined}
                onChange={(v) =>
                  setValues((prev) => ({ ...prev, [row.band]: typeof v === 'number' ? v : null }))
                }
                style={{ width: 100 }}
                addonAfter="%"
              />
            ),
          },
        ]}
      />
      <div style={{ marginTop: 12 }}>
        <Tag color={sum > 100 ? 'red' : 'blue'}>
          Sum: {sum.toFixed(1)}% / 100%
        </Tag>
      </div>
    </Modal>
  )
}

// ────────────────────────────────────────────────────────────────────────────
// Edit-log drawer
// ────────────────────────────────────────────────────────────────────────────

export function CalibrationEditLogDrawer({
  sessionId,
  sessionName,
  open,
  onClose,
}: {
  sessionId: string | null
  sessionName: string
  open: boolean
  onClose: () => void
}) {
  const { message } = AntdApp.useApp()
  const [entries, setEntries] = useState<CalibrationEditLog[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open || !sessionId) return
    setLoading(true)
    calibrationApi
      .editLog(sessionId)
      .then(setEntries)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load edit log'))
      .finally(() => setLoading(false))
  }, [open, sessionId, message])

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={`Edit log — ${sessionName}`}
      width={720}
    >
      <Paragraph type="secondary" style={{ fontSize: 12 }}>
        Every calibrate action while the session was IN_PROGRESS. Use the
        before/after JSON to audit who suggested what.
      </Paragraph>
      <Table
        size="small"
        rowKey="id"
        loading={loading}
        dataSource={entries}
        pagination={{ pageSize: 25 }}
        columns={[
          {
            title: 'When',
            dataIndex: 'editedAt',
            render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
            width: 160,
          },
          { title: 'By', dataIndex: 'editedBy', width: 130 },
          {
            title: 'Review',
            dataIndex: 'reviewId',
            render: (v: string) => <Text code style={{ fontSize: 11 }}>{v.slice(0, 8)}…</Text>,
            width: 110,
          },
          {
            title: 'Change',
            render: (_: unknown, r: CalibrationEditLog) => (
              <details>
                <summary style={{ cursor: 'pointer', color: '#1677ff' }}>view diff</summary>
                <Space direction="vertical" style={{ marginTop: 6 }}>
                  <Text type="secondary" style={{ fontSize: 11 }}>BEFORE</Text>
                  <pre style={{ background: '#fafafa', padding: 6, fontSize: 11 }}>
                    {JSON.stringify(r.beforeJson ?? {}, null, 2)}
                  </pre>
                  <Text type="secondary" style={{ fontSize: 11 }}>AFTER</Text>
                  <pre style={{ background: '#f6ffed', padding: 6, fontSize: 11 }}>
                    {JSON.stringify(r.afterJson ?? {}, null, 2)}
                  </pre>
                </Space>
              </details>
            ),
          },
        ]}
      />
    </Drawer>
  )
}

// ────────────────────────────────────────────────────────────────────────────
// Lock indicator helper
// ────────────────────────────────────────────────────────────────────────────

export function CalibrationLockTag({ locked }: { locked?: boolean }) {
  if (!locked) return null
  return <Tag color="purple" style={{ fontSize: 10 }}>LOCKED</Tag>
}

// ────────────────────────────────────────────────────────────────────────────
// Re-export the title type for convenience
// ────────────────────────────────────────────────────────────────────────────

export const CalibrationV2Title = { Title }
