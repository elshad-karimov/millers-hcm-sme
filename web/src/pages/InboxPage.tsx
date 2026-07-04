import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PaperClipOutlined, ShareAltOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { workflowApi, type WorkflowInstance, type WorkflowStatus } from '../api/workflow'
import { useAuth } from '../auth/AuthContext'

const STATUS_COLOR: Record<WorkflowStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  AUTO_APPROVED: 'green',
  REJECTED: 'red',
  RETURNED: 'orange',
  CANCELLED: 'default',
}

function instanceLink(i: WorkflowInstance): string | null {
  if (i.subjectModule === 'ORGANIZATION' && i.subjectEntity === 'StructureVersion') {
    return `/organization?versionId=${i.subjectId}`
  }
  return null
}

interface DelegateForm {
  delegateTo: string
  comment?: string
}

interface AttachDocForm {
  documentRef: string
  comment?: string
}

export function InboxPage() {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const { user } = useAuth()
  // M233 — inbox/approvals labels come from the inbox namespace.
  const { t } = useTranslation('inbox')
  const [inbox, setInbox] = useState<WorkflowInstance[]>([])
  const [initiated, setInitiated] = useState<WorkflowInstance[]>([])
  const [loading, setLoading] = useState(true)

  // M435 — filters
  const [typeFilter, setTypeFilter] = useState<string | undefined>()
  const [slaFilter, setSlaFilter] = useState<string | undefined>()

  // M435 — bulk actions
  const [selectedRows, setSelectedRows] = useState<string[]>([])

  // Delegate modal state
  const [delegateTarget, setDelegateTarget] = useState<WorkflowInstance | null>(null)
  const [delegating, setDelegating] = useState(false)
  const [delegateForm] = Form.useForm<DelegateForm>()

  // Attach document modal state
  const [attachTarget, setAttachTarget] = useState<WorkflowInstance | null>(null)
  const [attaching, setAttaching] = useState(false)
  const [attachForm] = Form.useForm<AttachDocForm>()

  function reload() {
    setLoading(true)
    setSelectedRows([])
    Promise.all([workflowApi.inbox(typeFilter, undefined, slaFilter), workflowApi.initiated()])
      .then(([a, b]) => {
        setInbox(a)
        setInitiated(b)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? t('loadFailed')),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => { reload() }, [typeFilter, slaFilter]) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleDelegate() {
    if (!delegateTarget) return
    try {
      const vals = await delegateForm.validateFields()
      setDelegating(true)
      await workflowApi.act(delegateTarget.id, 'DELEGATE', vals.comment, vals.delegateTo)
      message.success(t('delegateModal.successDelegated', { username: vals.delegateTo }))
      setDelegateTarget(null)
      delegateForm.resetFields()
      reload()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      if (axiosErr?.response?.data?.message) {
        message.error(axiosErr.response.data.message)
      }
    } finally {
      setDelegating(false)
    }
  }

  async function handleAttach() {
    if (!attachTarget) return
    try {
      const vals = await attachForm.validateFields()
      setAttaching(true)
      await workflowApi.act(attachTarget.id, 'ATTACH_DOCUMENT', vals.comment, undefined, vals.documentRef)
      message.success(t('attachModal.successAttached'))
      setAttachTarget(null)
      attachForm.resetFields()
      reload()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      if (axiosErr?.response?.data?.message) {
        message.error(axiosErr.response.data.message)
      }
    } finally {
      setAttaching(false)
    }
  }

  async function handleBulkAction(action: 'APPROVE' | 'REJECT') {
    if (selectedRows.length === 0) {
      message.warning('Please select at least one item')
      return
    }

    modal.confirm({
      title: `Bulk ${action.toLowerCase()}`,
      content: (
        <div>
          <p>You are about to {action.toLowerCase()} {selectedRows.length} item(s).</p>
          <Input.TextArea
            id="bulk-comment"
            placeholder="Optional comment"
            rows={3}
            maxLength={500}
          />
        </div>
      ),
      onOk: async () => {
        const comment = (document.getElementById('bulk-comment') as HTMLTextAreaElement)?.value
        try {
          const results = await workflowApi.bulkAct(selectedRows, action, comment || undefined)
          const succeeded = results.filter((r) => r.success).length
          const failed = results.filter((r) => !r.success)

          if (failed.length === 0) {
            message.success(`Successfully ${action.toLowerCase()}ed ${succeeded} item(s)`)
          } else {
            modal.info({
              title: 'Bulk action results',
              content: (
                <div>
                  <p>Succeeded: {succeeded}</p>
                  <p>Failed: {failed.length}</p>
                  {failed.length > 0 && (
                    <ul>
                      {failed.map((f, i) => (
                        <li key={i}>{f.message ?? 'Unknown error'}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ),
            })
          }
          reload()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { message?: string } } }
          message.error(axiosErr?.response?.data?.message ?? 'Bulk action failed')
        }
      },
    })
  }

  // M435 — distinct workflow types from the current inbox for the filter dropdown
  const availableTypes = Array.from(new Set(inbox.map((i) => i.definitionCode))).sort()

  const inboxColumns: ColumnsType<WorkflowInstance> = [
    { title: t('columns.title'), dataIndex: 'title' },
    {
      title: t('columns.workflow'),
      dataIndex: 'definitionCode',
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    { title: t('columns.initiator'), dataIndex: 'initiatedBy' },
    {
      title: t('columns.waitingOn'),
      render: (_, r) => {
        if (r.status !== 'PENDING') return '—'
        // M435 — show SLA overdue tag if applicable
        const slaOverdue = r.initiatedAt && new Date(r.initiatedAt) < new Date(Date.now() - 48 * 60 * 60 * 1000)
        return (
          <Space size={4}>
            {r.currentStepRole && <Tag>{r.currentStepRole.replace('ROLE_', '')}</Tag>}
            {r.delegatedTo && (
              <Tag color="purple" icon={<ShareAltOutlined />}>
                {r.delegatedTo}
              </Tag>
            )}
            {slaOverdue && <Tag color="red">SLA overdue</Tag>}
          </Space>
        )
      },
    },
    {
      title: t('columns.status'),
      dataIndex: 'status',
      render: (s: WorkflowStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: t('columns.started'),
      dataIndex: 'initiatedAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '',
      key: 'actions',
      render: (_, r) => {
        if (r.status !== 'PENDING') return null
        // Only show Delegate if it's not already delegated to someone else
        const alreadyDelegated = r.delegatedTo && r.delegatedTo !== user?.username
        return (
          <Space size={4}>
            {!alreadyDelegated && (
              <Button
                size="small"
                icon={<ShareAltOutlined />}
                onClick={(e) => {
                  e.stopPropagation()
                  setDelegateTarget(r)
                }}
              >
                {t('actions.delegate')}
              </Button>
            )}
            <Button
              size="small"
              icon={<PaperClipOutlined />}
              onClick={(e) => {
                e.stopPropagation()
                setAttachTarget(r)
              }}
            >
              {t('actions.attach')}
            </Button>
          </Space>
        )
      },
    },
  ]

  const initiatedColumns: ColumnsType<WorkflowInstance> = [
    { title: t('columns.title'), dataIndex: 'title' },
    {
      title: t('columns.workflow'),
      dataIndex: 'definitionCode',
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    {
      title: t('columns.waitingOn'),
      render: (_, r) => {
        if (r.status !== 'PENDING') return '—'
        return (
          <Space size={4}>
            {r.currentStepRole && <Tag>{r.currentStepRole.replace('ROLE_', '')}</Tag>}
            {r.delegatedTo && (
              <Tag color="purple" icon={<ShareAltOutlined />}>
                {r.delegatedTo}
              </Tag>
            )}
          </Space>
        )
      },
    },
    {
      title: t('columns.status'),
      dataIndex: 'status',
      render: (s: WorkflowStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: t('columns.started'),
      dataIndex: 'initiatedAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 64 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <>
      <Card title={<Typography.Title level={4} style={{ margin: 0 }}>{t('title')}</Typography.Title>}>
        <Tabs
          items={[
            {
              key: 'inbox',
              label: (
                <Space>
                  {t('tabs.pendingForMe')}
                  {inbox.length > 0 && <Tag color="gold">{inbox.length}</Tag>}
                </Space>
              ),
              children: (
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  {/* M435 — Filters and bulk actions */}
                  <Space wrap>
                    <Select
                      placeholder="Filter by type"
                      style={{ width: 200 }}
                      allowClear
                      value={typeFilter}
                      onChange={setTypeFilter}
                      options={availableTypes.map((t) => ({ label: t, value: t }))}
                    />
                    <Select
                      placeholder="SLA status"
                      style={{ width: 150 }}
                      allowClear
                      value={slaFilter}
                      onChange={setSlaFilter}
                      options={[
                        { label: 'Overdue', value: 'OVERDUE' },
                        { label: 'OK', value: 'OK' },
                      ]}
                    />
                    {selectedRows.length > 0 && (
                      <>
                        <Button type="primary" onClick={() => handleBulkAction('APPROVE')}>
                          Approve selected ({selectedRows.length})
                        </Button>
                        <Button danger onClick={() => handleBulkAction('REJECT')}>
                          Reject selected ({selectedRows.length})
                        </Button>
                      </>
                    )}
                  </Space>
                  {inbox.length === 0 ? (
                    <Empty description={t('empty.nothingWaiting')} />
                  ) : (
                    <Table
                      rowKey="id"
                      columns={inboxColumns}
                      dataSource={inbox}
                      rowSelection={{
                        selectedRowKeys: selectedRows,
                        onChange: (keys) => setSelectedRows(keys as string[]),
                        getCheckboxProps: (record) => ({
                          disabled: record.status !== 'PENDING',
                        }),
                      }}
                      onRow={(record) => ({
                        onClick: () => {
                          const link = instanceLink(record)
                          if (link) navigate(link)
                        },
                        style: { cursor: instanceLink(record) ? 'pointer' : 'default' },
                      })}
                    />
                  )}
                </Space>
              ),
            },
            {
              key: 'initiated',
              label: t('tabs.initiatedByMe'),
              children:
                initiated.length === 0 ? (
                  <Empty description={t('empty.noneInitiated')} />
                ) : (
                  <Table
                    rowKey="id"
                    columns={initiatedColumns}
                    dataSource={initiated}
                    onRow={(record) => ({
                      onClick: () => {
                        const link = instanceLink(record)
                        if (link) navigate(link)
                      },
                      style: { cursor: instanceLink(record) ? 'pointer' : 'default' },
                    })}
                  />
                ),
            },
          ]}
        />
      </Card>

      {/* Delegate modal */}
      <Modal
        title={t('delegateModal.title', { title: delegateTarget?.title ?? '' })}
        open={!!delegateTarget}
        onCancel={() => { setDelegateTarget(null); delegateForm.resetFields() }}
        onOk={handleDelegate}
        confirmLoading={delegating}
        okText={t('delegateModal.ok')}
        destroyOnClose
      >
        <Form form={delegateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="delegateTo"
            label={t('delegateModal.to')}
            rules={[{ required: true, message: t('delegateModal.toRequired') }]}
          >
            <Input placeholder={t('delegateModal.toPlaceholder')} autoFocus />
          </Form.Item>
          <Form.Item name="comment" label={t('delegateModal.note')}>
            <Input.TextArea rows={3} maxLength={500} showCount placeholder={t('delegateModal.notePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Attach Document modal */}
      <Modal
        title={t('attachModal.title', { title: attachTarget?.title ?? '' })}
        open={!!attachTarget}
        onCancel={() => { setAttachTarget(null); attachForm.resetFields() }}
        onOk={handleAttach}
        confirmLoading={attaching}
        okText={t('attachModal.ok')}
        destroyOnClose
      >
        <Form form={attachForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="documentRef"
            label={t('attachModal.ref')}
            rules={[{ required: true, message: t('attachModal.refRequired') }]}
          >
            <Input placeholder={t('attachModal.refPlaceholder')} autoFocus />
          </Form.Item>
          <Form.Item name="comment" label={t('attachModal.description')}>
            <Input.TextArea rows={3} maxLength={500} showCount placeholder={t('attachModal.descriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
