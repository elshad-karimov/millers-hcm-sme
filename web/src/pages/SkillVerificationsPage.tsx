import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Input,
  Tabs,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  skillVerificationApi,
  type SkillVerificationRequest,
  type VerificationStatus,
} from '../api/skillVerification'

const { Title } = Typography
const { TextArea } = Input

const STATUS_COLOR: Record<VerificationStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
}

export function SkillVerificationsPage() {
  const { message, modal } = AntdApp.useApp()

  const [pending, setPending] = useState<SkillVerificationRequest[]>([])
  const [myRequests, setMyRequests] = useState<SkillVerificationRequest[]>([])
  const [loading, setLoading] = useState(false)

  const loadPending = () => {
    setLoading(true)
    skillVerificationApi
      .pending()
      .then((res) => setPending(res.data))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load pending requests'),
      )
      .finally(() => setLoading(false))
  }

  const loadMyRequests = () => {
    setLoading(true)
    skillVerificationApi
      .myRequests()
      .then((res) => setMyRequests(res.data))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load my requests'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadPending()
    loadMyRequests()
  }, [])

  const handleApprove = (id: string) => {
    let notes = ''
    modal.confirm({
      title: 'Approve Verification Request',
      content: (
        <TextArea
          placeholder="Verification notes (optional)"
          rows={3}
          onChange={(e) => (notes = e.target.value)}
        />
      ),
      onOk: () =>
        skillVerificationApi
          .approve(id, notes)
          .then(() => {
            message.success('Request approved')
            loadPending()
            loadMyRequests()
          })
          .catch((err) =>
            message.error(err?.response?.data?.message ?? 'Failed to approve'),
          ),
    })
  }

  const handleReject = (id: string) => {
    let notes = ''
    modal.confirm({
      title: 'Reject Verification Request',
      content: (
        <TextArea
          placeholder="Rejection reason (optional)"
          rows={3}
          onChange={(e) => (notes = e.target.value)}
        />
      ),
      onOk: () =>
        skillVerificationApi
          .reject(id, notes)
          .then(() => {
            message.success('Request rejected')
            loadPending()
            loadMyRequests()
          })
          .catch((err) =>
            message.error(err?.response?.data?.message ?? 'Failed to reject'),
          ),
    })
  }

  const columns: ColumnsType<SkillVerificationRequest> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      width: 140,
      render: (id) => id.substring(0, 8),
    },
    {
      title: 'Competency',
      dataIndex: 'competencyId',
      width: 140,
      render: (id) => id.substring(0, 8),
    },
    {
      title: 'Requested Level',
      dataIndex: 'requestedLevel',
      width: 120,
      render: (level: number) => <Tag color="blue">{level}</Tag>,
    },
    {
      title: 'Evidence',
      dataIndex: 'evidenceNotes',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (status: VerificationStatus) => (
        <Tag color={STATUS_COLOR[status]}>{status}</Tag>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 160,
      render: (dt: string) => new Date(dt).toLocaleDateString(),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, rec) =>
        rec.status === 'PENDING' ? (
          <Space>
            <Button type="primary" size="small" onClick={() => handleApprove(rec.id)}>
              Approve
            </Button>
            <Button danger size="small" onClick={() => handleReject(rec.id)}>
              Reject
            </Button>
          </Space>
        ) : rec.verificationNotes ? (
          <span style={{ fontSize: '12px', color: '#888' }}>
            {rec.verificationNotes.substring(0, 30)}
          </span>
        ) : null,
    },
  ]

  const myColumns: ColumnsType<SkillVerificationRequest> = [
    {
      title: 'Competency',
      dataIndex: 'competencyId',
      width: 140,
      render: (id) => id.substring(0, 8),
    },
    {
      title: 'Requested Level',
      dataIndex: 'requestedLevel',
      width: 120,
      render: (level: number) => <Tag color="blue">{level}</Tag>,
    },
    {
      title: 'Evidence',
      dataIndex: 'evidenceNotes',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (status: VerificationStatus) => (
        <Tag color={STATUS_COLOR[status]}>{status}
        </Tag>
      ),
    },
    {
      title: 'Verified By',
      dataIndex: 'verifiedByEmployeeId',
      width: 140,
      render: (id) => (id ? id.substring(0, 8) : '-'),
    },
    {
      title: 'Verified At',
      dataIndex: 'verifiedAt',
      width: 160,
      render: (dt?: string) => (dt ? new Date(dt).toLocaleDateString() : '-'),
    },
  ]

  return (
    <div>
      <Title level={2}>Skill Verification Requests</Title>

      <Tabs
        items={[
          {
            key: 'pending',
            label: `Pending Requests (${pending.length})`,
            children: (
              <Card>
                <Table
                  dataSource={pending}
                  columns={columns}
                  rowKey="id"
                  loading={loading}
                  pagination={{ pageSize: 20 }}
                />
              </Card>
            ),
          },
          {
            key: 'my',
            label: `My Requests (${myRequests.length})`,
            children: (
              <Card>
                <Table
                  dataSource={myRequests}
                  columns={myColumns}
                  rowKey="id"
                  loading={loading}
                  pagination={{ pageSize: 20 }}
                />
              </Card>
            ),
          },
        ]}
      />
    </div>
  )
}
