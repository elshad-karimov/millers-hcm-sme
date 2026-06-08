import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { ShareAltOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
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

export function InboxPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [inbox, setInbox] = useState<WorkflowInstance[]>([])
  const [initiated, setInitiated] = useState<WorkflowInstance[]>([])
  const [loading, setLoading] = useState(true)

  // Delegate modal state
  const [delegateTarget, setDelegateTarget] = useState<WorkflowInstance | null>(null)
  const [delegating, setDelegating] = useState(false)
  const [delegateForm] = Form.useForm<DelegateForm>()

  function reload() {
    setLoading(true)
    Promise.all([workflowApi.inbox(), workflowApi.initiated()])
      .then(([a, b]) => {
        setInbox(a)
        setInitiated(b)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load workflow inbox'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => { reload() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleDelegate() {
    if (!delegateTarget) return
    try {
      const vals = await delegateForm.validateFields()
      setDelegating(true)
      await workflowApi.act(delegateTarget.id, 'DELEGATE', vals.comment, vals.delegateTo)
      message.success(`Step delegated to ${vals.delegateTo}`)
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

  const inboxColumns: ColumnsType<WorkflowInstance> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Workflow',
      dataIndex: 'definitionCode',
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    { title: 'Initiator', dataIndex: 'initiatedBy' },
    {
      title: 'Waiting on',
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
      title: 'Status',
      dataIndex: 'status',
      render: (s: WorkflowStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Started',
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
        if (alreadyDelegated) return null
        return (
          <Button
            size="small"
            icon={<ShareAltOutlined />}
            onClick={(e) => {
              e.stopPropagation()
              setDelegateTarget(r)
            }}
          >
            Delegate
          </Button>
        )
      },
    },
  ]

  const initiatedColumns: ColumnsType<WorkflowInstance> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Workflow',
      dataIndex: 'definitionCode',
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    {
      title: 'Waiting on',
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
      title: 'Status',
      dataIndex: 'status',
      render: (s: WorkflowStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Started',
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
      <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Approvals</Typography.Title>}>
        <Tabs
          items={[
            {
              key: 'inbox',
              label: (
                <Space>
                  Pending for me
                  {inbox.length > 0 && <Tag color="gold">{inbox.length}</Tag>}
                </Space>
              ),
              children:
                inbox.length === 0 ? (
                  <Empty description="Nothing waiting on you" />
                ) : (
                  <Table
                    rowKey="id"
                    columns={inboxColumns}
                    dataSource={inbox}
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
            {
              key: 'initiated',
              label: 'Initiated by me',
              children:
                initiated.length === 0 ? (
                  <Empty description="You haven't started any workflows yet" />
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
        title={`Delegate: ${delegateTarget?.title ?? ''}`}
        open={!!delegateTarget}
        onCancel={() => { setDelegateTarget(null); delegateForm.resetFields() }}
        onOk={handleDelegate}
        confirmLoading={delegating}
        okText="Delegate"
        destroyOnClose
      >
        <Form form={delegateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="delegateTo"
            label="Delegate to (username)"
            rules={[{ required: true, message: 'Enter the username to delegate to' }]}
          >
            <Input placeholder="e.g. john.smith" autoFocus />
          </Form.Item>
          <Form.Item name="comment" label="Note (optional)">
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="Reason for delegation" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
