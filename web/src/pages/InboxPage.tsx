import { useEffect, useState } from 'react'
import { Card, Empty, Space, Spin, Table, Tabs, Tag, Typography, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { workflowApi, type WorkflowInstance, type WorkflowStatus } from '../api/workflow'

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

export function InboxPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [inbox, setInbox] = useState<WorkflowInstance[]>([])
  const [initiated, setInitiated] = useState<WorkflowInstance[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([workflowApi.inbox(), workflowApi.initiated()])
      .then(([a, b]) => {
        setInbox(a)
        setInitiated(b)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load workflow inbox'),
      )
      .finally(() => setLoading(false))
  }, [message])

  const columns: ColumnsType<WorkflowInstance> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Workflow',
      dataIndex: 'definitionCode',
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    { title: 'Initiator', dataIndex: 'initiatedBy' },
    {
      title: 'Waiting on',
      render: (_, r) =>
        r.status === 'PENDING' && r.currentStepRole ? (
          <Tag>{r.currentStepRole.replace('ROLE_', '')}</Tag>
        ) : (
          '—'
        ),
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
                  columns={columns}
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
                  columns={columns}
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
  )
}
