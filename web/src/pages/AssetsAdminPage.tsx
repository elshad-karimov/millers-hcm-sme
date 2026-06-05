// M124 — Cross-employee asset administration page.
//
// HR can see every asset in one place, filter by status / type, drill
// into a per-asset event log timeline, and reissue an asset from one
// employee to another in a single transaction.
//
// The employee-scoped CRUD (assign, update, close) still lives under
// /employees/:id (M72) — this page is intentionally read-mostly +
// reissue, the high-leverage admin actions.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { assetsAdminApi, type AssetEventResponse, type AssetEventType, type AssetReissueRequest } from '../api/assetsAdmin'
import type { Asset, AssetStatus, AssetType } from '../api/assetsNotesRewards'
import { employeesApi, type Employee } from '../api/employees'

const { Title, Text, Paragraph } = Typography

const STATUS_COLOR: Record<AssetStatus, string> = {
  ASSIGNED: 'blue',
  RETURNED: 'green',
  LOST: 'red',
  DAMAGED: 'orange',
  WRITTEN_OFF: 'default',
}

const EVENT_COLOR: Record<AssetEventType, string> = {
  ASSIGN: 'blue',
  RETURN: 'green',
  MARK_LOST: 'red',
  MARK_DAMAGED: 'orange',
  WRITE_OFF: 'gray',
  REASSIGN: 'purple',
  UPDATE_CONDITION: 'cyan',
}

const ASSET_TYPES: AssetType[] = [
  'LAPTOP', 'MOBILE_PHONE', 'SIM_CARD', 'VEHICLE', 'ACCESS_CARD',
  'UNIFORM', 'TOOLS', 'EQUIPMENT', 'LOCKER', 'FUEL_CARD',
  'CREDIT_CARD', 'TABLET', 'HEADSET', 'OTHER',
]

export function AssetsAdminPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<Asset[]>([])
  const [counts, setCounts] = useState<Record<AssetStatus, number> | null>(null)
  const [loading, setLoading] = useState(false)
  const [filters, setFilters] = useState<{ status?: AssetStatus; type?: AssetType }>({})
  const [employees, setEmployees] = useState<Employee[]>([])

  // history drawer
  const [historyFor, setHistoryFor] = useState<Asset | null>(null)
  const [history, setHistory] = useState<AssetEventResponse[]>([])

  // reissue modal
  const [reissueFor, setReissueFor] = useState<Asset | null>(null)
  const [reissueForm] = Form.useForm()

  const reload = () => {
    setLoading(true)
    assetsAdminApi.search(filters)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load assets'))
      .finally(() => setLoading(false))
    assetsAdminApi.counts().then(setCounts).catch(() => {})
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.status, filters.type])

  useEffect(() => {
    employeesApi.list({ size: 200 }).then((p) => setEmployees(p.content)).catch(() => {})
  }, [])

  const employeeName = (id?: string | null) => {
    if (!id) return '—'
    const e = employees.find((x) => x.id === id)
    return e ? `${e.firstName} ${e.lastName}` : id.slice(0, 8) + '…'
  }

  const openHistory = (asset: Asset) => {
    setHistoryFor(asset)
    assetsAdminApi.history(asset.id)
      .then(setHistory)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load history'))
  }

  const openReissue = (asset: Asset) => {
    reissueForm.resetFields()
    reissueForm.setFieldsValue({ effectiveAt: dayjs() })
    setReissueFor(asset)
  }

  const onReissue = async () => {
    if (!reissueFor) return
    try {
      const v = await reissueForm.validateFields()
      const body: AssetReissueRequest = {
        newEmployeeId: v.newEmployeeId,
        effectiveAt: v.effectiveAt.format('YYYY-MM-DD'),
        conditionAtReturn: v.conditionAtReturn ?? null,
        conditionAtAssignment: v.conditionAtAssignment ?? null,
        notes: v.notes ?? null,
      }
      await assetsAdminApi.reissue(reissueFor.id, body)
      message.success('Reissued')
      setReissueFor(null)
      reload()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Reissue failed')
    }
  }

  const columns = useMemo(() => [
    {
      title: 'Asset',
      dataIndex: 'assetName',
      render: (n: string, r: Asset) => (
        <Space direction="vertical" size={0}>
          <Text strong>{n}</Text>
          {r.assetIdentifier && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.assetIdentifier}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'assetType',
      render: (t: AssetType) => <Tag>{t}</Tag>,
      width: 130,
    },
    {
      title: 'Holder',
      dataIndex: 'employeeId',
      render: (id: string) => <Text>{employeeName(id)}</Text>,
      width: 180,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: AssetStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
      width: 110,
    },
    {
      title: 'Assigned',
      dataIndex: 'assignedAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
      width: 110,
    },
    {
      title: 'Condition',
      render: (_: unknown, r: Asset) => {
        const c = r.status === 'ASSIGNED' ? r.conditionAtAssignment : r.conditionAtReturn
        return c ? <Tag color="cyan">{c}</Tag> : <Text type="secondary">—</Text>
      },
      width: 110,
    },
    {
      title: 'Actions',
      render: (_: unknown, r: Asset) => (
        <Space>
          <a onClick={() => openHistory(r)}>History</a>
          {(r.status === 'ASSIGNED' || r.status === 'RETURNED') && (
            <a onClick={() => openReissue(r)}>Reissue</a>
          )}
        </Space>
      ),
    },
  ], [employees])

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ margin: 0, marginBottom: 16 }}>Asset administration</Title>

      <Space size="middle" style={{ marginBottom: 16 }}>
        {counts && (Object.keys(counts) as AssetStatus[]).map((s) => (
          <Card key={s} size="small" style={{ minWidth: 130 }}>
            <Statistic
              title={<Tag color={STATUS_COLOR[s]}>{s}</Tag>}
              value={counts[s] ?? 0}
              valueStyle={{ fontSize: 22 }}
            />
          </Card>
        ))}
      </Space>

      <Card size="small">
        <Space style={{ marginBottom: 12 }} wrap>
          <Select
            allowClear
            placeholder="Filter by status"
            style={{ width: 180 }}
            value={filters.status}
            onChange={(v) => setFilters({ ...filters, status: v })}
            options={(['ASSIGNED','RETURNED','LOST','DAMAGED','WRITTEN_OFF'] as AssetStatus[])
              .map((s) => ({ value: s, label: s }))}
          />
          <Select
            allowClear
            placeholder="Filter by type"
            style={{ width: 200 }}
            value={filters.type}
            onChange={(v) => setFilters({ ...filters, type: v })}
            options={ASSET_TYPES.map((t) => ({ value: t, label: t }))}
            showSearch
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* ── History drawer ──────────────────────────────────────── */}
      <Drawer
        open={!!historyFor}
        onClose={() => setHistoryFor(null)}
        width={620}
        title={historyFor ? `Timeline — ${historyFor.assetName}` : ''}
      >
        {historyFor && (
          <>
            <Paragraph type="secondary" style={{ fontSize: 12 }}>
              Append-only log of every state change. The most recent event is at the top.
            </Paragraph>
            <Timeline
              items={history.map((e) => ({
                key: e.id,
                color: EVENT_COLOR[e.eventType],
                children: (
                  <Space direction="vertical" size={2} style={{ width: '100%' }}>
                    <Space>
                      <Tag color={EVENT_COLOR[e.eventType]}>{e.eventType}</Tag>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {dayjs(e.occurredAt).format('YYYY-MM-DD HH:mm')}
                      </Text>
                    </Space>
                    {(e.previousStatus || e.newStatus) && (
                      <Text style={{ fontSize: 12 }}>
                        {(e.previousStatus ?? '—')} → {e.newStatus ?? '—'}
                      </Text>
                    )}
                    {(e.previousEmployeeId || e.newEmployeeId) && (
                      <Text style={{ fontSize: 12 }}>
                        Holder: {employeeName(e.previousEmployeeId)} → {employeeName(e.newEmployeeId)}
                      </Text>
                    )}
                    {e.condition && (
                      <Text style={{ fontSize: 12 }}>
                        Condition: <Tag color="cyan">{e.condition}</Tag>
                      </Text>
                    )}
                    {e.notes && (
                      <Text style={{ fontSize: 12, fontStyle: 'italic' }}>{e.notes}</Text>
                    )}
                    <Text type="secondary" style={{ fontSize: 11 }}>by {e.actor}</Text>
                  </Space>
                ),
              }))}
            />
          </>
        )}
      </Drawer>

      {/* ── Reissue modal ───────────────────────────────────────── */}
      <Modal
        open={!!reissueFor}
        onCancel={() => setReissueFor(null)}
        onOk={onReissue}
        okText="Reissue"
        title={reissueFor ? `Reissue ${reissueFor.assetName}` : ''}
      >
        {reissueFor && (
          <Form form={reissueForm} layout="vertical">
            <Paragraph type="secondary" style={{ fontSize: 12 }}>
              Current holder: <Text strong>{employeeName(reissueFor.employeeId)}</Text>.
              This will record a single REASSIGN event in the timeline.
            </Paragraph>
            <Form.Item
              name="newEmployeeId"
              label="New holder"
              rules={[{ required: true }]}
            >
              <Select
                showSearch
                placeholder="Pick employee"
                filterOption={(input, opt) =>
                  String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
                options={employees
                  .filter((e) => e.id !== reissueFor.employeeId)
                  .map((e) => ({ value: e.id, label: `${e.firstName} ${e.lastName} (${e.employeeNo})` }))}
              />
            </Form.Item>
            <Form.Item name="effectiveAt" label="Effective" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="conditionAtReturn" label="Condition at return (from prior holder)">
              <Input placeholder="e.g. GOOD" />
            </Form.Item>
            <Form.Item name="conditionAtAssignment" label="Condition at assignment (to new holder)">
              <Input placeholder="e.g. GOOD" />
            </Form.Item>
            <Form.Item name="notes" label="Notes">
              <Input.TextArea rows={2} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}
