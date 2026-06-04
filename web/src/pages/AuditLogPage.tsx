// M114 — Audit-log browser.
//
// SYSTEM_ADMIN + HR_ADMIN + AUDITOR only. Backed by the monthly-partitioned
// audit_log table — server enforces the role gate plus a date range so
// Postgres can prune partitions on every query.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { auditApi, type AuditLogDetail, type AuditLogRow } from '../api/audit'

const { Title, Text, Paragraph } = Typography

const ACTION_COLOR: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  CANCEL: 'red',
  ARCHIVE: 'default',
  END: 'orange',
  RECONCILE_HEADCOUNT: 'purple',
  ADJUST_OCCUPANCY: 'cyan',
  STATUS_CHANGE: 'gold',
  TRANSITION: 'gold',
  ASSIGN: 'green',
  REASSIGN: 'blue',
  SWAP: 'purple',
  LOCK: 'orange',
  REMOVE: 'red',
  GENERATE_ROSTER: 'cyan',
  ENROL: 'green',
  WAIVE: 'default',
  TERMINATE: 'red',
}

function actionColor(action: string): string {
  return ACTION_COLOR[action] ?? 'geekblue'
}

/** Pretty-print JSON; tolerate non-JSON gracefully. */
function pretty(s?: string | null): string {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

export function AuditLogPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<AuditLogRow[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(50)

  // Filter state
  const [range, setRange] = useState<[ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]>([
    dayjs().subtract(7, 'day').startOf('day'),
    dayjs().endOf('day'),
  ])
  const [moduleVal, setModuleVal] = useState<string | undefined>()
  const [entityName, setEntityName] = useState<string | undefined>()
  const [entityId, setEntityId] = useState('')
  const [actionVal, setActionVal] = useState<string | undefined>()
  const [actor, setActor] = useState('')

  // Drop-down option lists
  const [modules, setModules] = useState<string[]>([])
  const [entities, setEntities] = useState<string[]>([])
  const [actions, setActions] = useState<string[]>([])

  // Detail drawer
  const [detail, setDetail] = useState<AuditLogDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // Load static modules list on mount.
  useEffect(() => {
    auditApi
      .modules()
      .then(setModules)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load modules'))
  }, [message])

  // When module changes, refresh dependent dropdowns and reset their values.
  useEffect(() => {
    if (!moduleVal) {
      setEntities([])
      setActions([])
      setEntityName(undefined)
      setActionVal(undefined)
      return
    }
    Promise.all([
      auditApi.entities(moduleVal),
      auditApi.actions(moduleVal),
    ])
      .then(([e, a]) => {
        setEntities(e)
        setActions(a)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load filters'))
  }, [moduleVal, message])

  const search = (overridePage?: number) => {
    setLoading(true)
    const p = overridePage ?? page
    auditApi
      .search({
        from: range[0].toISOString(),
        to: range[1].toISOString(),
        module: moduleVal,
        entityName,
        entityId: entityId.trim() || undefined,
        action: actionVal,
        actor: actor.trim() || undefined,
        page: p,
        size: pageSize,
      })
      .then((r) => {
        setRows(r.content)
        setTotal(r.totalElements)
        setPage(r.page)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Search failed'),
      )
      .finally(() => setLoading(false))
  }

  // Initial query + re-query when range changes (cheap signal — partition pruning).
  useEffect(() => {
    search(0)
    // eslint-disable-next-line
  }, [range[0].toISOString(), range[1].toISOString()])

  const openDetail = (id: string) => {
    setDetailLoading(true)
    auditApi
      .get(id)
      .then(setDetail)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load detail'))
      .finally(() => setDetailLoading(false))
  }

  const columns: ColumnsType<AuditLogRow> = [
    {
      title: 'Time',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{dayjs(v).format('YYYY-MM-DD')}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{dayjs(v).format('HH:mm:ss')}</Text>
        </Space>
      ),
    },
    { title: 'Actor', dataIndex: 'actor', width: 150 },
    {
      title: 'Module',
      dataIndex: 'module',
      width: 130,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Entity',
      width: 200,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text>{r.entityName}</Text>
          {r.entityId && (
            <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
              {r.entityId.length > 20 ? r.entityId.slice(0, 8) + '…' : r.entityId}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Action',
      dataIndex: 'action',
      width: 160,
      render: (v: string) => <Tag color={actionColor(v)}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Diff',
      width: 90,
      render: (_, r) => (
        <Space size={4}>
          {r.hasOldValue && <Tag color="orange" style={{ marginInlineEnd: 0 }}>old</Tag>}
          {r.hasNewValue && <Tag color="green" style={{ marginInlineEnd: 0 }}>new</Tag>}
        </Space>
      ),
    },
    {
      title: 'IP',
      dataIndex: 'ipAddress',
      width: 130,
      render: (v?: string | null) => v ?? <Text type="secondary">—</Text>,
    },
    {
      title: '',
      width: 70,
      render: (_, r) => (
        <Button size="small" onClick={() => openDetail(r.id)}>View</Button>
      ),
    },
  ]

  const moduleOptions = useMemo(() => modules.map((m) => ({ value: m, label: m })), [modules])
  const entityOptions = useMemo(() => entities.map((e) => ({ value: e, label: e })), [entities])
  const actionOptions = useMemo(() => actions.map((a) => ({ value: a, label: a })), [actions])

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Audit log</Title>
      <Text type="secondary">
        Immutable trail of every audited mutation across the platform. Every entry records the
        actor, IP, before/after JSON payload, and the entity context. Date range hits the
        monthly-partitioned table — narrower ranges return faster.
      </Text>

      <Card>
        <Row gutter={[12, 12]}>
          <Col xs={24} md={10}>
            <Text strong>Date range</Text>
            <DatePicker.RangePicker
              showTime
              value={range}
              onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
              allowClear={false}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={12} md={4}>
            <Text strong>Module</Text>
            <Select
              allowClear
              showSearch
              placeholder="Any"
              value={moduleVal}
              onChange={setModuleVal}
              options={moduleOptions}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={12} md={4}>
            <Text strong>Entity</Text>
            <Select
              allowClear
              showSearch
              placeholder="Any"
              value={entityName}
              onChange={setEntityName}
              options={entityOptions}
              disabled={!moduleVal}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={12} md={3}>
            <Text strong>Action</Text>
            <Select
              allowClear
              showSearch
              placeholder="Any"
              value={actionVal}
              onChange={setActionVal}
              options={actionOptions}
              disabled={!moduleVal}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={12} md={3}>
            <Text strong>Actor</Text>
            <Input
              placeholder="username"
              value={actor}
              onChange={(e) => setActor(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} md={6}>
            <Text strong>Entity ID</Text>
            <Input
              placeholder="UUID or business no."
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} md={18} style={{ textAlign: 'right', alignSelf: 'end' }}>
            <Space>
              <Button onClick={() => {
                setModuleVal(undefined)
                setEntityName(undefined)
                setEntityId('')
                setActionVal(undefined)
                setActor('')
              }}>Reset</Button>
              <Button type="primary" onClick={() => search(0)}>Search</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card>
        {loading ? <Spin /> : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={rows}
            size="small"
            pagination={{
              current: page + 1,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: ['25', '50', '100', '200'],
              onChange: (p, sz) => {
                setPage(p - 1)
                setPageSize(sz)
                setLoading(true)
                auditApi.search({
                  from: range[0].toISOString(),
                  to: range[1].toISOString(),
                  module: moduleVal,
                  entityName,
                  entityId: entityId.trim() || undefined,
                  action: actionVal,
                  actor: actor.trim() || undefined,
                  page: p - 1,
                  size: sz,
                })
                  .then((r) => { setRows(r.content); setTotal(r.totalElements) })
                  .catch((e) => message.error(e?.response?.data?.message ?? 'Failed'))
                  .finally(() => setLoading(false))
              },
            }}
            locale={{ emptyText: <Empty description="No audit entries match the filter" /> }}
          />
        )}
      </Card>

      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        width={900}
        title={detail ? `${detail.module} · ${detail.entityName} · ${detail.action}` : ''}
      >
        {detailLoading || !detail ? <Spin /> : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Card size="small">
              <Row gutter={12}>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>WHEN</Text>
                  <div>{dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss.SSS')}</div>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>ACTOR</Text>
                  <div><Text strong>{detail.actor}</Text></div>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>IP</Text>
                  <div>{detail.ipAddress ?? <Text type="secondary">—</Text>}</div>
                </Col>
              </Row>
              <Row gutter={12} style={{ marginTop: 12 }}>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>MODULE</Text>
                  <div><Tag>{detail.module}</Tag></div>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>ENTITY</Text>
                  <div>{detail.entityName}</div>
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 11 }}>ACTION</Text>
                  <div><Tag color={actionColor(detail.action)}>{detail.action.replace(/_/g, ' ')}</Tag></div>
                </Col>
              </Row>
              {detail.entityId && (
                <Row gutter={12} style={{ marginTop: 12 }}>
                  <Col span={24}>
                    <Text type="secondary" style={{ fontSize: 11 }}>ENTITY ID</Text>
                    <div style={{ fontFamily: 'monospace' }}>{detail.entityId}</div>
                  </Col>
                </Row>
              )}
            </Card>

            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Card size="small" title={
                  <Space>
                    <Tag color="orange">Before</Tag>
                    <Text>old_value</Text>
                  </Space>
                }>
                  {detail.oldValue ? (
                    <pre style={{
                      margin: 0,
                      fontSize: 11,
                      maxHeight: 500,
                      overflow: 'auto',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}>{pretty(detail.oldValue)}</pre>
                  ) : (
                    <Tooltip title="A null old_value usually means a CREATE — there was nothing before this row existed.">
                      <Text type="secondary">— (null)</Text>
                    </Tooltip>
                  )}
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card size="small" title={
                  <Space>
                    <Tag color="green">After</Tag>
                    <Text>new_value</Text>
                  </Space>
                }>
                  {detail.newValue ? (
                    <pre style={{
                      margin: 0,
                      fontSize: 11,
                      maxHeight: 500,
                      overflow: 'auto',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}>{pretty(detail.newValue)}</pre>
                  ) : (
                    <Tooltip title="A null new_value usually means a DELETE — the row no longer exists after this action.">
                      <Text type="secondary">— (null)</Text>
                    </Tooltip>
                  )}
                </Card>
              </Col>
            </Row>

            <Paragraph type="secondary" style={{ fontSize: 11, marginBottom: 0 }}>
              Audit entries are append-only — they are never updated or deleted (PRD 14.5).
              Each row is stored in the monthly partition matching its created_at.
            </Paragraph>
          </Space>
        )}
      </Drawer>
    </Space>
  )
}
