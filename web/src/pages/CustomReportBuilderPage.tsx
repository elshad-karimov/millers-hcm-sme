// M119 — Custom report builder.
//
// Two-panel layout:
//   left  — spec editor (source picker, field checklist, filter rows, sort rows)
//   right — saved-reports list + preview grid
//
// The builder posts the in-flight spec to /preview to render results; saving
// upserts on (owner, name). All validation happens server-side via
// CustomReportSqlBuilder.validate — the SPA just shows whatever error
// message comes back.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  Input,
  List,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  customReportsApi,
  type CatalogResponse,
  type CustomReportDetail,
  type CustomReportSummary,
  type FieldCatalog,
  type FilterOp,
  type FilterSpec,
  type RunResponse,
  type SaveRequest,
  type SortDirection,
  type SortSpec,
  type SourceCatalog,
} from '../api/customReports'

const { Title, Text, Paragraph } = Typography

const EMPTY_DRAFT: SaveRequest = {
  name: '',
  description: '',
  sourceKey: '',
  fieldKeys: [],
  filters: [],
  sorts: [],
  rowLimit: 1000,
  shared: false,
}

export function CustomReportBuilderPage() {
  const { message } = AntdApp.useApp()
  const [catalog, setCatalog] = useState<CatalogResponse | null>(null)
  const [saved, setSaved] = useState<CustomReportSummary[]>([])
  const [loadingSaved, setLoadingSaved] = useState(false)
  const [draft, setDraft] = useState<SaveRequest>(EMPTY_DRAFT)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [preview, setPreview] = useState<RunResponse | null>(null)
  const [running, setRunning] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    customReportsApi.catalog().then(setCatalog).catch((e) =>
      message.error(e?.response?.data?.message ?? 'Failed to load catalog'),
    )
    reloadSaved()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reloadSaved = () => {
    setLoadingSaved(true)
    customReportsApi
      .list()
      .then(setSaved)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load reports'))
      .finally(() => setLoadingSaved(false))
  }

  const source: SourceCatalog | undefined = useMemo(
    () => catalog?.sources.find((s) => s.key === draft.sourceKey),
    [catalog, draft.sourceKey],
  )

  const fieldByKey: Record<string, FieldCatalog> = useMemo(() => {
    const m: Record<string, FieldCatalog> = {}
    source?.fields.forEach((f) => { m[f.key] = f })
    return m
  }, [source])

  const opsForField = (fieldKey: string) => {
    const f = fieldByKey[fieldKey]
    if (!f || !catalog) return []
    return catalog.ops.filter((o) => o.compatibleTypes.includes(f.type))
  }

  // ── source change resets dependent state ───────────────────────────────

  const onChangeSource = (sourceKey: string) => {
    setDraft({ ...EMPTY_DRAFT, sourceKey, name: draft.name, description: draft.description })
    setEditingId(null)
    setPreview(null)
  }

  // ── field list ──────────────────────────────────────────────────────────

  const toggleField = (key: string, checked: boolean) => {
    setDraft({
      ...draft,
      fieldKeys: checked
        ? [...draft.fieldKeys, key]
        : draft.fieldKeys.filter((k) => k !== key),
    })
  }

  // ── filters ─────────────────────────────────────────────────────────────

  const addFilter = () => {
    if (!source) return
    const firstFilterable = source.fields.find((f) => f.filterable)
    if (!firstFilterable) return
    const op: FilterOp = 'EQ'
    setDraft({
      ...draft,
      filters: [...draft.filters, { fieldKey: firstFilterable.key, op, values: [''] }],
    })
  }

  const updateFilter = (idx: number, patch: Partial<FilterSpec>) => {
    setDraft({
      ...draft,
      filters: draft.filters.map((f, i) => (i === idx ? { ...f, ...patch } : f)),
    })
  }

  const removeFilter = (idx: number) => {
    setDraft({ ...draft, filters: draft.filters.filter((_, i) => i !== idx) })
  }

  // ── sorts ───────────────────────────────────────────────────────────────

  const addSort = () => {
    if (!source) return
    const firstSortable = source.fields.find((f) => f.sortable)
    if (!firstSortable) return
    setDraft({
      ...draft,
      sorts: [...draft.sorts, { fieldKey: firstSortable.key, direction: 'ASC' }],
    })
  }

  const updateSort = (idx: number, patch: Partial<SortSpec>) => {
    setDraft({
      ...draft,
      sorts: draft.sorts.map((s, i) => (i === idx ? { ...s, ...patch } : s)),
    })
  }

  const removeSort = (idx: number) => {
    setDraft({ ...draft, sorts: draft.sorts.filter((_, i) => i !== idx) })
  }

  // ── actions ─────────────────────────────────────────────────────────────

  const run = async () => {
    if (!draft.sourceKey || draft.fieldKeys.length === 0) {
      message.warning('Pick a source and at least one field first')
      return
    }
    setRunning(true)
    try {
      const result = await customReportsApi.runPreview(draft)
      setPreview(result)
      if (result.truncated) {
        message.warning(`Showing first ${result.rowLimit} rows — refine filters to see more`)
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Preview failed')
    } finally {
      setRunning(false)
    }
  }

  const save = async () => {
    if (!draft.name) {
      message.warning('Name is required to save')
      return
    }
    setSaving(true)
    try {
      const saved = await customReportsApi.save(draft)
      setEditingId(saved.id)
      message.success(`Saved "${saved.name}"`)
      reloadSaved()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const loadSaved = async (id: string) => {
    try {
      const detail: CustomReportDetail = await customReportsApi.get(id)
      setDraft({
        name: detail.name,
        description: detail.description ?? '',
        sourceKey: detail.sourceKey,
        fieldKeys: detail.fieldKeys,
        filters: detail.filters,
        sorts: detail.sorts,
        rowLimit: detail.rowLimit,
        shared: detail.shared,
      })
      setEditingId(detail.id)
      setPreview(null)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to open report')
    }
  }

  const deleteSaved = async (id: string) => {
    try {
      await customReportsApi.delete(id)
      message.success('Deleted')
      if (editingId === id) {
        setDraft(EMPTY_DRAFT)
        setEditingId(null)
        setPreview(null)
      }
      reloadSaved()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  const newReport = () => {
    setDraft(EMPTY_DRAFT)
    setEditingId(null)
    setPreview(null)
  }

  // ── render ──────────────────────────────────────────────────────────────

  if (!catalog) return <Spin />

  const previewCols = preview?.columns.map((c) => ({
    title: c.label,
    dataIndex: c.key,
    key: c.key,
    render: (_: unknown, _row: unknown, rowIdx: number) => {
      const v = preview.rows[rowIdx][preview.columns.findIndex((x) => x.key === c.key)]
      if (v == null) return <Text type="secondary">—</Text>
      if (typeof v === 'object') return JSON.stringify(v)
      return String(v)
    },
  })) ?? []

  const previewData = preview?.rows.map((row, i) => {
    const o: Record<string, unknown> = { __i: i }
    preview.columns.forEach((c, ci) => { o[c.key] = row[ci] })
    return o
  }) ?? []

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>Custom report builder</Title>
        <Space>
          <Button onClick={newReport}>New</Button>
          <Button type="primary" loading={running} onClick={run}>Preview</Button>
          <Button type="primary" loading={saving} onClick={save}>Save</Button>
        </Space>
      </Space>

      <Row gutter={16}>
        {/* ── Spec panel ─────────────────────────────────────────── */}
        <Col xs={24} md={9}>
          <Card title="Specification" size="small">
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>NAME</Text>
                <Input
                  value={draft.name ?? ''}
                  placeholder="e.g. Active engineers in Finance"
                  onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>DESCRIPTION</Text>
                <Input.TextArea
                  rows={2}
                  value={draft.description ?? ''}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>DATA SOURCE</Text>
                <Select
                  style={{ width: '100%' }}
                  placeholder="Choose a source"
                  value={draft.sourceKey || undefined}
                  onChange={onChangeSource}
                  options={catalog.sources.map((s) => ({ value: s.key, label: s.label }))}
                />
              </div>

              {source && (
                <>
                  <div>
                    <Text type="secondary" style={{ fontSize: 11 }}>FIELDS</Text>
                    <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid #f0f0f0', padding: 8, borderRadius: 4 }}>
                      {source.fields.map((f) => (
                        <div key={f.key} style={{ marginBottom: 4 }}>
                          <Checkbox
                            checked={draft.fieldKeys.includes(f.key)}
                            onChange={(e) => toggleField(f.key, e.target.checked)}
                          >
                            {f.label} <Tag style={{ fontSize: 10 }}>{f.type}</Tag>
                          </Checkbox>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div>
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>FILTERS</Text>
                      <Button size="small" onClick={addFilter}>+ Filter</Button>
                    </Space>
                    {draft.filters.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No filters" style={{ margin: '8px 0' }} />}
                    {draft.filters.map((f, idx) => (
                      <FilterRow
                        key={idx}
                        spec={f}
                        source={source}
                        ops={opsForField(f.fieldKey)}
                        onChange={(p) => updateFilter(idx, p)}
                        onRemove={() => removeFilter(idx)}
                      />
                    ))}
                  </div>

                  <div>
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>SORT</Text>
                      <Button size="small" onClick={addSort}>+ Sort</Button>
                    </Space>
                    {draft.sorts.map((s, idx) => (
                      <SortRow
                        key={idx}
                        spec={s}
                        source={source}
                        onChange={(p) => updateSort(idx, p)}
                        onRemove={() => removeSort(idx)}
                      />
                    ))}
                  </div>

                  <Space>
                    <Text type="secondary" style={{ fontSize: 11 }}>ROW LIMIT</Text>
                    <Input
                      type="number"
                      style={{ width: 100 }}
                      value={draft.rowLimit ?? 1000}
                      onChange={(e) => setDraft({ ...draft, rowLimit: Number(e.target.value) })}
                    />
                    <Switch
                      checked={!!draft.shared}
                      onChange={(v) => setDraft({ ...draft, shared: v })}
                    />
                    <Text type="secondary">Share with org</Text>
                  </Space>
                </>
              )}
            </Space>
          </Card>
        </Col>

        {/* ── Saved + preview panel ──────────────────────────────── */}
        <Col xs={24} md={15}>
          <Card
            size="small"
            title="My library"
            extra={editingId && <Tag color="blue">editing: {draft.name}</Tag>}
          >
            <List
              loading={loadingSaved}
              size="small"
              dataSource={saved}
              renderItem={(r) => (
                <List.Item
                  actions={[
                    <a onClick={() => loadSaved(r.id)} key="open">Open</a>,
                    r.mine ? (
                      <Popconfirm
                        key="del"
                        title={`Delete "${r.name}"?`}
                        onConfirm={() => deleteSaved(r.id)}
                      >
                        <a>Delete</a>
                      </Popconfirm>
                    ) : null,
                  ].filter(Boolean)}
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        {r.name}
                        {r.shared && <Tag color="green">shared</Tag>}
                        {!r.mine && <Tag color="default">by {r.ownerUser}</Tag>}
                      </Space>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {r.sourceLabel} · updated {dayjs(r.updatedAt).format('YYYY-MM-DD HH:mm')}
                        {r.lastRunRows != null && ` · last run: ${r.lastRunRows} rows`}
                      </Text>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>

          <Card size="small" title="Preview" style={{ marginTop: 12 }}>
            {!preview && <Empty description='Click "Preview" to see results' />}
            {preview && (
              <>
                <Paragraph style={{ marginBottom: 8 }}>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {preview.rowCount} rows
                    {preview.truncated && ` (truncated at ${preview.rowLimit})`}
                  </Text>
                </Paragraph>
                <Table
                  size="small"
                  scroll={{ x: 'max-content', y: 480 }}
                  columns={previewCols}
                  dataSource={previewData}
                  rowKey="__i"
                  pagination={{ pageSize: 50, showSizeChanger: false }}
                />
              </>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}

// ── small components ──────────────────────────────────────────────────────

function FilterRow({
  spec, source, ops, onChange, onRemove,
}: {
  spec: FilterSpec
  source: SourceCatalog
  ops: { op: FilterOp; valueCount: number; compatibleTypes: string[] }[]
  onChange: (p: Partial<FilterSpec>) => void
  onRemove: () => void
}) {
  const opInfo = ops.find((o) => o.op === spec.op)
  const valueCount = opInfo?.valueCount ?? 1
  const filterableFields = source.fields.filter((f) => f.filterable)

  return (
    <Space.Compact block style={{ marginTop: 6 }}>
      <Select
        size="small"
        style={{ width: 160 }}
        value={spec.fieldKey}
        onChange={(v) => {
          const newField = source.fields.find((f) => f.key === v)
          // reset op when field type changes
          const stillValid = newField && ops.some((o) => o.op === spec.op && o.compatibleTypes.includes(newField.type))
          onChange({ fieldKey: v, op: stillValid ? spec.op : 'EQ' })
        }}
        options={filterableFields.map((f) => ({ value: f.key, label: f.label }))}
      />
      <Select
        size="small"
        style={{ width: 110 }}
        value={spec.op}
        onChange={(v) => {
          const o = ops.find((x) => x.op === v)
          const need = o?.valueCount ?? 1
          // Truncate or pad the value list to the new op's expected count.
          const padded = (spec.values ?? []).slice(0, need)
          while (padded.length < need) padded.push('')
          onChange({ op: v, values: padded })
        }}
        options={ops.map((o) => ({ value: o.op, label: o.op }))}
      />
      {valueCount >= 1 && (
        <Input
          size="small"
          style={{ width: valueCount === 2 ? 100 : 200 }}
          placeholder={valueCount === 2 ? 'from' : 'value'}
          value={spec.values?.[0] ?? ''}
          onChange={(e) => {
            const v = [...(spec.values ?? [])]
            v[0] = e.target.value
            onChange({ values: v })
          }}
        />
      )}
      {valueCount === 2 && (
        <Input
          size="small"
          style={{ width: 100 }}
          placeholder="to"
          value={spec.values?.[1] ?? ''}
          onChange={(e) => {
            const v = [...(spec.values ?? [])]
            v[1] = e.target.value
            onChange({ values: v })
          }}
        />
      )}
      <Button size="small" danger onClick={onRemove}>×</Button>
    </Space.Compact>
  )
}

function SortRow({
  spec, source, onChange, onRemove,
}: {
  spec: SortSpec
  source: SourceCatalog
  onChange: (p: Partial<SortSpec>) => void
  onRemove: () => void
}) {
  const sortableFields = source.fields.filter((f) => f.sortable)
  return (
    <Space.Compact block style={{ marginTop: 6 }}>
      <Select
        size="small"
        style={{ width: 180 }}
        value={spec.fieldKey}
        onChange={(v) => onChange({ fieldKey: v })}
        options={sortableFields.map((f) => ({ value: f.key, label: f.label }))}
      />
      <Select
        size="small"
        style={{ width: 80 }}
        value={spec.direction}
        onChange={(v) => onChange({ direction: v as SortDirection })}
        options={[{ value: 'ASC', label: 'ASC' }, { value: 'DESC', label: 'DESC' }]}
      />
      <Button size="small" danger onClick={onRemove}>×</Button>
    </Space.Compact>
  )
}
