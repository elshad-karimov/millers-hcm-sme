// M84 — Bulk reorg manifest upload + preview + apply.
//
// Paste a JSON manifest (or load a .json file) targeting a DRAFT
// structure version. Dry-run validates without writing; Apply runs
// inside one transaction so partial failure rolls back.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  App as AntdApp,
} from 'antd'
import type { UploadProps } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { UploadOutlined } from '@ant-design/icons'
import {
  orgApi,
  type BulkReorgKind,
  type BulkReorgManifest,
  type BulkReorgResult,
  type BulkReorgRowResult,
  type StructureVersion,
} from '../api/org'

const KIND_COLOR: Record<BulkReorgKind, string> = {
  ADD: 'green',
  UPDATE: 'blue',
  MOVE: 'gold',
  REMOVE: 'red',
}

const SAMPLE_MANIFEST: BulkReorgManifest = {
  dryRun: true,
  operations: [
    {
      kind: 'ADD',
      code: 'ENG-PLATFORM',
      name: 'Platform Engineering',
      unitType: 'TEAM',
      parentCode: 'ENG',
      costCentreCode: 'CC-100',
    },
    { kind: 'MOVE', code: 'TEAM-BE', newParentCode: 'ENG-PLATFORM' },
    { kind: 'UPDATE', code: 'ENG', name: 'Engineering Division' },
    { kind: 'REMOVE', code: 'LEGACY-TEAM' },
  ],
}

export function BulkReorgPage() {
  const { message } = AntdApp.useApp()
  const [versions, setVersions] = useState<StructureVersion[]>([])
  const [versionId, setVersionId] = useState<string | undefined>()
  const [json, setJson] = useState<string>(JSON.stringify(SAMPLE_MANIFEST, null, 2))
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BulkReorgResult | null>(null)

  useEffect(() => {
    orgApi
      .versions()
      .then((vs) => {
        setVersions(vs)
        const draft = vs.find((v) => v.status === 'DRAFT')
        if (draft) setVersionId(draft.id)
      })
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load versions'),
      )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const versionOptions = useMemo(
    () =>
      versions
        .filter((v) => v.status === 'DRAFT')
        .map((v) => ({
          value: v.id,
          label: `v${v.versionNumber} (DRAFT, effective ${v.effectiveDate})`,
        })),
    [versions],
  )

  const parseManifest = (): BulkReorgManifest | null => {
    try {
      const parsed = JSON.parse(json) as BulkReorgManifest
      if (!parsed.operations || !Array.isArray(parsed.operations)) {
        message.error('operations array is required')
        return null
      }
      return parsed
    } catch (e) {
      message.error(
        'Manifest is not valid JSON: ' + (e instanceof Error ? e.message : 'unknown'),
      )
      return null
    }
  }

  const submit = async (dryRun: boolean) => {
    if (!versionId) {
      message.error('Pick a DRAFT version first')
      return
    }
    const manifest = parseManifest()
    if (!manifest) return
    const payload: BulkReorgManifest = { ...manifest, dryRun }
    setSubmitting(true)
    setResult(null)
    try {
      const r = await orgApi.bulkReorg(versionId, payload)
      setResult(r)
      message.success(
        dryRun
          ? `Dry-run OK — ${r.operationsTotal} ops would apply`
          : `Applied ${r.operationsApplied}/${r.operationsTotal} ops`,
      )
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Bulk reorg failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const uploadProps: UploadProps = {
    accept: '.json,application/json',
    showUploadList: false,
    beforeUpload: (file) => {
      const reader = new FileReader()
      reader.onload = (ev) => {
        try {
          const text = String(ev.target?.result ?? '')
          // Pretty-print the loaded payload so the textarea stays readable.
          const parsed = JSON.parse(text)
          setJson(JSON.stringify(parsed, null, 2))
          message.success(`Loaded ${file.name}`)
        } catch {
          message.error('File is not valid JSON')
        }
      }
      reader.readAsText(file)
      return false // prevent AntD from uploading
    },
  }

  const columns: ColumnsType<BulkReorgRowResult> = [
    { title: '#', dataIndex: 'index', width: 60 },
    {
      title: 'Kind',
      dataIndex: 'kind',
      width: 100,
      render: (v: BulkReorgKind) => <Tag color={KIND_COLOR[v]}>{v}</Tag>,
    },
    { title: 'Code', dataIndex: 'code', width: 200 },
    {
      title: 'Applied',
      dataIndex: 'applied',
      width: 100,
      render: (v: boolean) =>
        v ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>,
    },
    { title: 'Message', dataIndex: 'message' },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Bulk reorganisation
      </Typography.Title>

      <Alert
        type="info"
        showIcon
        message="Atomic apply"
        description="Target a DRAFT version. The whole manifest validates first, then applies in one transaction — partial failure rolls everything back. Use Dry-run to pre-flight without writing."
      />

      <Card title="Manifest" size="small"
        extra={
          <Space>
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>Load JSON file</Button>
            </Upload>
            <Button onClick={() => setJson(JSON.stringify(SAMPLE_MANIFEST, null, 2))}>
              Reset to sample
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            placeholder="Pick a DRAFT version"
            style={{ width: 480 }}
            value={versionId}
            onChange={setVersionId}
            options={versionOptions}
            allowClear
          />
          <Input.TextArea
            rows={18}
            value={json}
            onChange={(e) => setJson(e.target.value)}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
          <Space>
            <Button onClick={() => submit(true)} loading={submitting}>
              Dry-run
            </Button>
            <Button type="primary" danger onClick={() => submit(false)} loading={submitting}>
              Apply
            </Button>
          </Space>
        </Space>
      </Card>

      {result && (
        <Card
          title={
            <Space>
              <span>Result</span>
              <Tag color={result.dryRun ? 'blue' : 'green'}>
                {result.dryRun ? 'DRY-RUN' : 'APPLIED'}
              </Tag>
              <Typography.Text type="secondary">
                {result.operationsApplied}/{result.operationsTotal} ops
              </Typography.Text>
            </Space>
          }
          size="small"
        >
          <Table
            rowKey="index"
            columns={columns}
            dataSource={result.rows}
            pagination={false}
            size="small"
            locale={{ emptyText: <Empty description="No rows" /> }}
          />
        </Card>
      )}
    </Space>
  )
}
