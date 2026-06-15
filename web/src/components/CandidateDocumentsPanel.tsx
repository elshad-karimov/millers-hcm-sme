import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import {
  recruitmentApi,
  type CandidateChecklist,
  type CandidateDocumentItem,
  type DocumentStatus,
  type DocumentType,
} from '../api/recruitment'
import { AttachmentUploader } from './AttachmentUploader'

/**
 * M292 — Recruitment PRD §12: candidate document checklist. Tracks each
 * required document through PENDING → RECEIVED → VERIFIED (REJECTED /
 * WAIVED); files are uploaded per document via the M16 AttachmentUploader.
 */

const STATUS_META: Record<DocumentStatus, { label: string; color: string }> = {
  PENDING: { label: 'Pending', color: 'default' },
  RECEIVED: { label: 'Received', color: 'blue' },
  VERIFIED: { label: 'Verified', color: 'green' },
  REJECTED: { label: 'Rejected', color: 'red' },
  WAIVED: { label: 'Waived', color: 'gold' },
}
const STATUS_OPTIONS = (Object.keys(STATUS_META) as DocumentStatus[]).map((s) => ({
  value: s,
  label: STATUS_META[s].label,
}))

function errMsg(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback
}

export function CandidateDocumentsPanel({ candidateId }: { candidateId: string }) {
  const { message } = AntdApp.useApp()
  const [checklist, setChecklist] = useState<CandidateChecklist>({
    items: [],
    total: 0,
    requiredCount: 0,
    verifiedCount: 0,
    outstandingRequired: 0,
  })
  const [types, setTypes] = useState<DocumentType[]>([])
  const [addType, setAddType] = useState<string | undefined>(undefined)
  const [filesFor, setFilesFor] = useState<CandidateDocumentItem | null>(null)

  const load = useCallback(() => {
    recruitmentApi
      .candidateDocuments(candidateId)
      .then(setChecklist)
      .catch(() => undefined)
  }, [candidateId])

  useEffect(() => {
    load()
    recruitmentApi.documentTypes(true).then(setTypes).catch(() => setTypes([]))
  }, [load])

  const present = new Set(checklist.items.map((i) => i.documentTypeId))
  const addable = types.filter((t) => !present.has(t.id))

  const add = async () => {
    if (!addType) return
    try {
      await recruitmentApi.addCandidateDocument(candidateId, { documentTypeId: addType })
      setAddType(undefined)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Failed to add'))
    }
  }

  const seed = async () => {
    try {
      const c = await recruitmentApi.seedCandidateDocuments(candidateId)
      setChecklist(c)
      message.success('Required documents added')
    } catch (err) {
      message.error(errMsg(err, 'Failed'))
    }
  }

  const setStatus = async (item: CandidateDocumentItem, status: DocumentStatus) => {
    try {
      await recruitmentApi.updateCandidateDocument(item.id, { status })
      load()
    } catch (err) {
      message.error(errMsg(err, 'Failed'))
    }
  }

  const toggleRequired = async (item: CandidateDocumentItem) => {
    try {
      await recruitmentApi.updateCandidateDocument(item.id, {
        status: item.status,
        required: !item.required,
      })
      load()
    } catch (err) {
      message.error(errMsg(err, 'Failed'))
    }
  }

  const remove = async (item: CandidateDocumentItem) => {
    try {
      await recruitmentApi.deleteCandidateDocument(item.id)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Failed'))
    }
  }

  return (
    <Card
      size="small"
      style={{ marginTop: 16 }}
      title={
        <Space>
          <span>Documents</span>
          <Tag color={checklist.outstandingRequired > 0 ? 'orange' : 'green'}>
            {checklist.verifiedCount}/{checklist.requiredCount} required verified
          </Tag>
          {checklist.outstandingRequired > 0 && (
            <Tag color="orange">{checklist.outstandingRequired} outstanding</Tag>
          )}
        </Space>
      }
      extra={
        <Space>
          <Select
            placeholder="Add document…"
            value={addType}
            style={{ width: 200 }}
            options={addable.map((t) => ({ value: t.id, label: t.name }))}
            onChange={setAddType}
            size="small"
          />
          <Button size="small" onClick={add} disabled={!addType}>
            Add
          </Button>
          <Tooltip title="Add all default-required document types">
            <Button size="small" onClick={seed}>
              Seed required
            </Button>
          </Tooltip>
        </Space>
      }
    >
      <Table<CandidateDocumentItem>
        rowKey="id"
        size="small"
        dataSource={checklist.items}
        pagination={false}
        locale={{ emptyText: 'No documents tracked yet' }}
        columns={[
          { title: 'Document', dataIndex: 'typeName', ellipsis: true },
          {
            title: 'Required',
            dataIndex: 'required',
            width: 90,
            render: (r: boolean, item) => (
              <Tag
                color={r ? 'volcano' : 'default'}
                style={{ cursor: 'pointer' }}
                onClick={() => toggleRequired(item)}
              >
                {r ? 'Required' : 'Optional'}
              </Tag>
            ),
          },
          {
            title: 'Status',
            dataIndex: 'status',
            width: 140,
            render: (s: DocumentStatus, item) => (
              <Select
                size="small"
                value={s}
                style={{ width: 120 }}
                options={STATUS_OPTIONS}
                onChange={(v) => setStatus(item, v)}
              />
            ),
          },
          {
            title: 'Verified',
            width: 150,
            render: (_: unknown, item) =>
              item.verifiedAt ? (
                <Tooltip title={item.verifiedBy ?? ''}>
                  {new Date(item.verifiedAt).toLocaleDateString()}
                </Tooltip>
              ) : (
                '—'
              ),
          },
          { title: 'Notes', dataIndex: 'notes', ellipsis: true, render: (n?: string) => n ?? '—' },
          {
            title: '',
            width: 130,
            render: (_: unknown, item) => (
              <Space size="small">
                <Button size="small" onClick={() => setFilesFor(item)}>
                  Files
                </Button>
                <Popconfirm title="Remove from checklist?" onConfirm={() => remove(item)}>
                  <Button size="small" danger>
                    Delete
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Drawer
        title={filesFor ? `Files — ${filesFor.typeName}` : 'Files'}
        open={!!filesFor}
        onClose={() => setFilesFor(null)}
        width={560}
        destroyOnClose
      >
        {filesFor && (
          <AttachmentUploader
            ownerModule="RECRUITMENT"
            ownerEntity="candidatedocument"
            ownerId={filesFor.id}
          />
        )}
      </Drawer>
    </Card>
  )
}
