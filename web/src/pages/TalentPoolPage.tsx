// M87 — Talent pool / candidate CRM page.
// Left: filter sidebar (pool status + tag multi-select + free-text query)
// Middle: results table with tag chips per row
// Right: detail drawer (tags + notes timeline) opened by clicking a row.

import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Input,
  List,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  talentPoolApi,
  type CandidateNote,
  type CandidateNoteKind,
  type CandidatePoolStatus,
  type CandidateTag,
  type PoolCandidateRow,
} from '../api/talentPool'

const POOL_COLOR: Record<CandidatePoolStatus, string> = {
  ACTIVE: 'green',
  PASSIVE: 'gold',
  ARCHIVED: 'default',
  DO_NOT_CONTACT: 'red',
}

const KIND_COLOR: Record<CandidateNoteKind, string> = {
  NOTE: 'default',
  CALL: 'blue',
  EMAIL: 'cyan',
  MEETING: 'purple',
  EVENT: 'magenta',
  REFERRAL: 'green',
  OTHER: 'default',
}

const POOL_STATUSES: CandidatePoolStatus[] = [
  'ACTIVE', 'PASSIVE', 'ARCHIVED', 'DO_NOT_CONTACT',
]
const NOTE_KINDS: CandidateNoteKind[] = [
  'NOTE', 'CALL', 'EMAIL', 'MEETING', 'EVENT', 'REFERRAL', 'OTHER',
]

export function TalentPoolPage() {
  const { message } = AntdApp.useApp()

  // Filters
  const [status, setStatus] = useState<CandidatePoolStatus | undefined>('ACTIVE')
  const [tagFilter, setTagFilter] = useState<string[]>([])
  const [q, setQ] = useState<string>('')
  const [knownTags, setKnownTags] = useState<string[]>([])

  // Results
  const [page, setPage] = useState(0)
  const [size] = useState(20)
  const [rows, setRows] = useState<PoolCandidateRow[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)

  // Detail drawer
  const [selected, setSelected] = useState<PoolCandidateRow | null>(null)
  const [tags, setTags] = useState<CandidateTag[]>([])
  const [notes, setNotes] = useState<CandidateNote[]>([])
  const [newTag, setNewTag] = useState<string>('')
  const [newNoteKind, setNewNoteKind] = useState<CandidateNoteKind>('NOTE')
  const [newNoteBody, setNewNoteBody] = useState<string>('')
  const [newNoteDate, setNewNoteDate] = useState<ReturnType<typeof dayjs> | null>(null)

  useEffect(() => {
    talentPoolApi
      .knownTags()
      .then(setKnownTags)
      .catch(() => {
        // The filter still works without the dictionary
      })
  }, [])

  const load = () => {
    setLoading(true)
    talentPoolApi
      .search({ status, tag: tagFilter.length ? tagFilter : undefined, q: q || undefined, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load talent pool'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(load, [status, tagFilter, page]) // q applies via the Search button

  const openDetail = (row: PoolCandidateRow) => {
    setSelected(row)
    setNewTag('')
    setNewNoteKind('NOTE')
    setNewNoteBody('')
    setNewNoteDate(null)
    Promise.all([
      talentPoolApi.tagsOf(row.id),
      talentPoolApi.notesOf(row.id),
    ])
      .then(([t, n]) => {
        setTags(t)
        setNotes(n)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load detail'),
      )
  }

  const refreshDetail = () => {
    if (!selected) return
    Promise.all([
      talentPoolApi.tagsOf(selected.id),
      talentPoolApi.notesOf(selected.id),
    ])
      .then(([t, n]) => {
        setTags(t)
        setNotes(n)
      })
      .catch(() => {})
  }

  const addTag = async () => {
    if (!selected || !newTag.trim()) return
    try {
      await talentPoolApi.addTag(selected.id, newTag.trim())
      setNewTag('')
      refreshDetail()
      load()
      message.success('Tag added')
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Add tag failed',
      )
    }
  }

  const removeTag = async (tagId: string) => {
    if (!selected) return
    try {
      await talentPoolApi.removeTag(selected.id, tagId)
      refreshDetail()
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Remove tag failed',
      )
    }
  }

  const addNote = async () => {
    if (!selected || !newNoteBody.trim()) return
    try {
      await talentPoolApi.addNote(selected.id, {
        kind: newNoteKind,
        body: newNoteBody.trim(),
        contactDate: newNoteDate ? newNoteDate.format('YYYY-MM-DD') : undefined,
      })
      setNewNoteBody('')
      setNewNoteDate(null)
      refreshDetail()
      message.success('Note added')
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Add note failed',
      )
    }
  }

  const changeStatus = async (newStatus: CandidatePoolStatus) => {
    if (!selected) return
    try {
      await talentPoolApi.changePoolStatus(selected.id, { newStatus })
      message.success(`Status → ${newStatus}`)
      load()
      setSelected({ ...selected, poolStatus: newStatus })
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Status change failed',
      )
    }
  }

  const columns: ColumnsType<PoolCandidateRow> = useMemo(() => [
    {
      title: 'Candidate',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <a onClick={() => openDetail(r)}>{r.firstName} {r.lastName}</a>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {r.candidateNo}
          </Typography.Text>
        </Space>
      ),
    },
    { title: 'Email', dataIndex: 'email', render: (v) => v ?? '—' },
    { title: 'Phone', dataIndex: 'phone', render: (v) => v ?? '—' },
    {
      title: 'Exp',
      dataIndex: 'experienceYears',
      width: 80,
      render: (v?: number | null) => v != null ? `${v} yr` : '—',
    },
    {
      title: 'Status',
      dataIndex: 'poolStatus',
      width: 140,
      render: (v: CandidatePoolStatus) => (
        <Tag color={POOL_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Last contacted',
      dataIndex: 'lastContactedAt',
      width: 180,
      render: (v?: string | null) => v ? new Date(v).toLocaleDateString() : '—',
    },
    {
      title: 'Tags',
      render: (_, r) => (
        <Space wrap size={4}>
          {r.tags.map((t) => <Tag key={t}>{t}</Tag>)}
        </Space>
      ),
    },
  ], [])

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Talent pool
      </Typography.Title>

      <Card size="small">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={6}>
            <Select
              placeholder="Pool status"
              style={{ width: '100%' }}
              allowClear
              value={status}
              onChange={(v) => {
                setStatus(v)
                setPage(0)
              }}
              options={POOL_STATUSES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
            />
          </Col>
          <Col xs={24} md={10}>
            <Select
              mode="tags"
              placeholder="Filter by tags (AND)"
              style={{ width: '100%' }}
              value={tagFilter}
              onChange={(v) => {
                setTagFilter(v)
                setPage(0)
              }}
              options={knownTags.map((t) => ({ value: t, label: t }))}
            />
          </Col>
          <Col xs={24} md={8}>
            <Input.Search
              placeholder="Name, email, skills…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onSearch={() => {
                setPage(0)
                load()
              }}
              allowClear
            />
          </Col>
        </Row>
      </Card>

      <Card size="small">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          onRow={(r) => ({ onClick: () => openDetail(r) })}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            onChange: (p) => setPage(p - 1),
            showSizeChanger: false,
          }}
          locale={{ emptyText: <Empty description="No candidates match" /> }}
        />
      </Card>

      <Drawer
        open={!!selected}
        onClose={() => setSelected(null)}
        width={620}
        title={selected ? `${selected.firstName} ${selected.lastName} — ${selected.candidateNo}` : ''}
        extra={
          selected && (
            <Space>
              <Tag color={POOL_COLOR[selected.poolStatus]}>
                {selected.poolStatus.replace(/_/g, ' ')}
              </Tag>
              <Link to={`/recruitment/candidates/${selected.id}/edit`}>
                <Button size="small">Edit</Button>
              </Link>
            </Space>
          )
        }
      >
        {selected && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card size="small" title="Pool status"
              extra={
                <Select
                  size="small"
                  value={selected.poolStatus}
                  style={{ width: 180 }}
                  onChange={changeStatus}
                  options={POOL_STATUSES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
                />
              }
            >
              <Space direction="vertical" size={4}>
                <Typography.Text type="secondary">Email: {selected.email ?? '—'}</Typography.Text>
                <Typography.Text type="secondary">Phone: {selected.phone ?? '—'}</Typography.Text>
                <Typography.Text type="secondary">
                  Created: {new Date(selected.createdAt).toLocaleString()}
                </Typography.Text>
                <Typography.Text type="secondary">
                  Last contacted: {selected.lastContactedAt
                    ? new Date(selected.lastContactedAt).toLocaleString()
                    : 'never'}
                </Typography.Text>
              </Space>
            </Card>

            <Card size="small" title={`Tags (${tags.length})`}>
              <Space wrap style={{ marginBottom: 12 }}>
                {tags.length === 0 ? (
                  <Typography.Text type="secondary">No tags yet</Typography.Text>
                ) : (
                  tags.map((t) => (
                    <Tag key={t.id} closable onClose={() => removeTag(t.id)}>
                      {t.tag}
                    </Tag>
                  ))
                )}
              </Space>
              <Space>
                <Input
                  placeholder="Add tag"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                  onPressEnter={addTag}
                  style={{ width: 220 }}
                />
                <Button onClick={addTag}>Add</Button>
              </Space>
            </Card>

            <Card size="small" title="Add a note">
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space wrap>
                  <Select
                    value={newNoteKind}
                    onChange={setNewNoteKind}
                    style={{ width: 150 }}
                    options={NOTE_KINDS.map((k) => ({ value: k, label: k }))}
                  />
                  <DatePicker
                    placeholder="Contact date (optional)"
                    value={newNoteDate}
                    onChange={setNewNoteDate}
                  />
                </Space>
                <Input.TextArea
                  rows={3}
                  placeholder="What happened?"
                  value={newNoteBody}
                  onChange={(e) => setNewNoteBody(e.target.value)}
                />
                <Button type="primary" onClick={addNote} disabled={!newNoteBody.trim()}>
                  Add note
                </Button>
              </Space>
            </Card>

            <Card size="small" title={`Activity (${notes.length})`}>
              {notes.length === 0 ? (
                <Empty description="No activity recorded" />
              ) : (
                <List
                  dataSource={notes}
                  renderItem={(n) => (
                    <List.Item key={n.id}>
                      <List.Item.Meta
                        title={
                          <Space>
                            <Tag color={KIND_COLOR[n.kind]}>{n.kind}</Tag>
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              {new Date(n.createdAt).toLocaleString()}
                              {n.contactDate && ` — ${n.contactDate}`}
                              {n.createdBy && ` — ${n.createdBy}`}
                            </Typography.Text>
                          </Space>
                        }
                        description={
                          <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                            {n.body}
                          </Typography.Paragraph>
                        }
                      />
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Space>
        )}
      </Drawer>
    </Space>
  )
}
