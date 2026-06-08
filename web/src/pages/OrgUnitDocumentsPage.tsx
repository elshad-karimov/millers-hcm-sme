import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { orgNativeReportsApi, type OrgUnitFlatRow } from '../api/orgNativeReports'
import {
  orgUnitDocumentsApi,
  type OrgUnitDocument,
  type OrgUnitDocumentRequest,
} from '../api/orgUnitDocuments'

const { Title } = Typography

const DOC_TYPES = ['LICENSE', 'PERMIT', 'CERTIFICATE', 'AGREEMENT', 'INSURANCE', 'OTHER']

function expiryColor(expiryDate?: string | null): string | undefined {
  if (!expiryDate) return undefined
  const days = dayjs(expiryDate).diff(dayjs(), 'day')
  if (days < 0) return 'red'
  if (days <= 30) return 'orange'
  if (days <= 90) return 'gold'
  return 'green'
}

export function OrgUnitDocumentsPage() {
  const { message } = AntdApp.useApp()
  const [units, setUnits] = useState<OrgUnitFlatRow[]>([])
  const [selectedUnit, setSelectedUnit] = useState<string | null>(null)
  const [docs, setDocs] = useState<OrgUnitDocument[]>([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<OrgUnitDocument | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    orgNativeReportsApi
      .flat()
      .then((r) => setUnits(r.rows))
      .catch(() => message.error('Failed to load org units'))
  }, [message])

  const loadDocs = (unitId: string) => {
    setLoadingDocs(true)
    orgUnitDocumentsApi
      .list(unitId)
      .then(setDocs)
      .catch(() => message.error('Failed to load documents'))
      .finally(() => setLoadingDocs(false))
  }

  const onSelectUnit = (unitId: string) => {
    setSelectedUnit(unitId)
    loadDocs(unitId)
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (doc: OrgUnitDocument) => {
    setEditing(doc)
    form.setFieldsValue({
      title: doc.title,
      docType: doc.docType ?? undefined,
      documentRef: doc.documentRef ?? undefined,
      issuedDate: doc.issuedDate ? dayjs(doc.issuedDate) : undefined,
      expiryDate: doc.expiryDate ? dayjs(doc.expiryDate) : undefined,
      notes: doc.notes ?? undefined,
    })
    setModalOpen(true)
  }

  const onSave = async (values: {
    title: string
    docType?: string
    documentRef?: string
    issuedDate?: dayjs.Dayjs
    expiryDate?: dayjs.Dayjs
    notes?: string
  }) => {
    if (!selectedUnit) return
    setSaving(true)
    const payload: OrgUnitDocumentRequest = {
      title: values.title,
      docType: values.docType,
      documentRef: values.documentRef,
      issuedDate: values.issuedDate?.format('YYYY-MM-DD'),
      expiryDate: values.expiryDate?.format('YYYY-MM-DD'),
      notes: values.notes,
    }
    try {
      if (editing) {
        await orgUnitDocumentsApi.update(selectedUnit, editing.id, payload)
        message.success('Document updated')
      } else {
        await orgUnitDocumentsApi.create(selectedUnit, payload)
        message.success('Document created')
      }
      setModalOpen(false)
      loadDocs(selectedUnit)
    } catch {
      message.error('Save failed')
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (doc: OrgUnitDocument) => {
    if (!selectedUnit) return
    try {
      await orgUnitDocumentsApi.delete(selectedUnit, doc.id)
      message.success('Document deleted')
      loadDocs(selectedUnit)
    } catch {
      message.error('Delete failed')
    }
  }

  const columns: ColumnsType<OrgUnitDocument> = [
    { title: 'Title', dataIndex: 'title', render: (v: string) => <strong>{v}</strong> },
    {
      title: 'Type',
      dataIndex: 'docType',
      width: 120,
      render: (v?: string | null) => (v ? <Tag>{v}</Tag> : '—'),
    },
    { title: 'Reference', dataIndex: 'documentRef', width: 180, render: (v) => v ?? '—' },
    { title: 'Issued', dataIndex: 'issuedDate', width: 110, render: (v) => v ?? '—' },
    {
      title: 'Expiry',
      dataIndex: 'expiryDate',
      width: 120,
      render: (v?: string | null) =>
        v ? <Tag color={expiryColor(v)}>{v}</Tag> : '—',
    },
    {
      title: '',
      width: 90,
      render: (_: unknown, row: OrgUnitDocument) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)} />
          <Popconfirm title="Delete this document?" onConfirm={() => onDelete(row)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const unitOptions = units.map((u) => ({
    value: u.unitId,
    label: `${u.code} — ${u.name}`,
  }))

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 24 }}>Org unit documents</Title>

      <Space style={{ marginBottom: 16 }} size="middle">
        <Select
          showSearch
          placeholder="Select org unit…"
          style={{ width: 320 }}
          options={unitOptions}
          optionFilterProp="label"
          onChange={onSelectUnit}
        />
        {selectedUnit && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Add document
          </Button>
        )}
      </Space>

      {selectedUnit && (
        <Table
          rowKey="id"
          loading={loadingDocs}
          dataSource={docs}
          columns={columns}
          size="small"
          pagination={{ pageSize: 25 }}
        />
      )}

      <Modal
        title={editing ? 'Edit document' : 'New document'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onSave}>
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="docType" label="Type">
            <Select allowClear options={DOC_TYPES.map((t) => ({ value: t, label: t }))} />
          </Form.Item>
          <Form.Item name="documentRef" label="Reference / URL" rules={[{ max: 400 }]}>
            <Input placeholder="Document number, file path, or external URL" />
          </Form.Item>
          <Form.Item name="issuedDate" label="Issued date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expiryDate" label="Expiry date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
