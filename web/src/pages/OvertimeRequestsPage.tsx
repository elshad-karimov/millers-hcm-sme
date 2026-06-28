import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  message,
  Modal,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { attendanceApi, type OvertimeRequest } from '../api/attendance'

const { Title, Text } = Typography
const { TextArea } = Input

export function OvertimeRequestsPage() {
  const [requests, setRequests] = useState<OvertimeRequest[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [decisionModalOpen, setDecisionModalOpen] = useState(false)
  const [selectedRequest, setSelectedRequest] = useState<OvertimeRequest | null>(null)
  const [decisionType, setDecisionType] = useState<'approve' | 'reject'>('approve')
  const [comment, setComment] = useState('')
  const [processing, setProcessing] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi.overtimeRequests()
      .then(setRequests)
      .catch(() => message.error('Failed to load overtime requests'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function openDecisionModal(request: OvertimeRequest, type: 'approve' | 'reject') {
    setSelectedRequest(request)
    setDecisionType(type)
    setComment('')
    setDecisionModalOpen(true)
  }

  function closeDecisionModal() {
    setDecisionModalOpen(false)
    setSelectedRequest(null)
    setComment('')
  }

  function handleDecision() {
    if (!selectedRequest) return
    if (decisionType === 'reject' && !comment.trim()) {
      message.warning('Rejection comment is required')
      return
    }

    setProcessing(true)
    const call = decisionType === 'approve'
      ? attendanceApi.approveOvertimeRequest(selectedRequest.id, comment || undefined)
      : attendanceApi.rejectOvertimeRequest(selectedRequest.id, comment)

    call
      .then(() => {
        message.success(`Overtime request ${decisionType === 'approve' ? 'approved' : 'rejected'}`)
        closeDecisionModal()
        load()
      })
      .catch(() => message.error('Failed to process request'))
      .finally(() => setProcessing(false))
  }

  const filteredRequests = statusFilter === 'all'
    ? requests
    : requests.filter(r => r.workflowStatus === statusFilter)

  const columns = [
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      width: 140,
      render: (v: string) => <Text code>{v.substring(0, 8)}...</Text>,
    },
    {
      title: 'Date',
      dataIndex: 'workDate',
      width: 110,
    },
    {
      title: 'Requested Minutes',
      dataIndex: 'requestedMinutes',
      width: 140,
      render: (v: number) => `${v} min`,
    },
    {
      title: 'Type',
      dataIndex: 'type',
      width: 120,
      render: (v: string) => <Tag>{v || 'STANDARD'}</Tag>,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'workflowStatus',
      width: 120,
      render: (v: string) => {
        const color = v === 'PENDING' ? 'orange' : v === 'APPROVED' ? 'green' : 'red'
        return <Tag color={color}>{v}</Tag>
      },
    },
    {
      title: '',
      width: 120,
      render: (_: unknown, r: OvertimeRequest) =>
        r.workflowStatus === 'PENDING' ? (
          <Space>
            <Button
              size="small"
              type="primary"
              icon={<CheckOutlined />}
              onClick={() => openDecisionModal(r, 'approve')}
            >
              Approve
            </Button>
            <Button
              size="small"
              danger
              icon={<CloseOutlined />}
              onClick={() => openDecisionModal(r, 'reject')}
            >
              Reject
            </Button>
          </Space>
        ) : null,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>Overtime Requests</Title>
        <Select
          value={statusFilter}
          onChange={setStatusFilter}
          style={{ width: 200 }}
          options={[
            { label: 'All', value: 'all' },
            { label: 'Pending', value: 'PENDING' },
            { label: 'Approved', value: 'APPROVED' },
            { label: 'Rejected', value: 'REJECTED' },
          ]}
        />
      </div>

      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Review and approve/reject overtime requests from employees.
      </Text>

      <Table
        rowKey="id"
        dataSource={filteredRequests}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={decisionType === 'approve' ? 'Approve Overtime Request' : 'Reject Overtime Request'}
        open={decisionModalOpen}
        onCancel={closeDecisionModal}
        onOk={handleDecision}
        confirmLoading={processing}
        okText={decisionType === 'approve' ? 'Approve' : 'Reject'}
        okButtonProps={{ danger: decisionType === 'reject' }}
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>Employee:</Text> {selectedRequest?.employeeId.substring(0, 8)}...
          <br />
          <Text strong>Date:</Text> {selectedRequest?.workDate}
          <br />
          <Text strong>Minutes:</Text> {selectedRequest?.requestedMinutes}
          <br />
          <Text strong>Reason:</Text> {selectedRequest?.reason}
        </div>
        <TextArea
          rows={3}
          placeholder={decisionType === 'reject' ? 'Rejection reason (required)' : 'Optional comment'}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>
    </div>
  )
}
