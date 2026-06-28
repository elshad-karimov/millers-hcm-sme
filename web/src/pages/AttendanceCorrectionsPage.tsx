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
import { attendanceApi, type AttendanceCorrection } from '../api/attendance'

const { Title, Text } = Typography
const { TextArea } = Input

export function AttendanceCorrectionsPage() {
  const [corrections, setCorrections] = useState<AttendanceCorrection[]>([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [decisionModalOpen, setDecisionModalOpen] = useState(false)
  const [selectedCorrection, setSelectedCorrection] = useState<AttendanceCorrection | null>(null)
  const [decisionType, setDecisionType] = useState<'approve' | 'reject'>('approve')
  const [comment, setComment] = useState('')
  const [processing, setProcessing] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi.corrections()
      .then(setCorrections)
      .catch(() => message.error('Failed to load corrections'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function openDecisionModal(correction: AttendanceCorrection, type: 'approve' | 'reject') {
    setSelectedCorrection(correction)
    setDecisionType(type)
    setComment('')
    setDecisionModalOpen(true)
  }

  function closeDecisionModal() {
    setDecisionModalOpen(false)
    setSelectedCorrection(null)
    setComment('')
  }

  function handleDecision() {
    if (!selectedCorrection) return
    if (decisionType === 'reject' && !comment.trim()) {
      message.warning('Rejection comment is required')
      return
    }

    setProcessing(true)
    const call = decisionType === 'approve'
      ? attendanceApi.approveCorrection(selectedCorrection.id, comment || undefined)
      : attendanceApi.rejectCorrection(selectedCorrection.id, comment)

    call
      .then(() => {
        message.success(`Correction ${decisionType === 'approve' ? 'approved' : 'rejected'}`)
        closeDecisionModal()
        load()
      })
      .catch(() => message.error('Failed to process correction'))
      .finally(() => setProcessing(false))
  }

  const filteredCorrections = statusFilter === 'all'
    ? corrections
    : corrections.filter(c => c.workflowStatus === statusFilter)

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
      title: 'Type',
      dataIndex: 'correctionType',
      width: 140,
      render: (v: string) => <Tag>{v}</Tag>,
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
      title: 'Decision',
      dataIndex: 'decision',
      width: 100,
      render: (v: string) => v ? <Tag>{v}</Tag> : null,
    },
    {
      title: '',
      width: 120,
      render: (_: unknown, r: AttendanceCorrection) =>
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
        <Title level={3} style={{ margin: 0 }}>Attendance Corrections</Title>
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
        Review and approve/reject attendance correction requests from employees.
      </Text>

      <Table
        rowKey="id"
        dataSource={filteredCorrections}
        columns={columns}
        loading={loading}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={decisionType === 'approve' ? 'Approve Correction' : 'Reject Correction'}
        open={decisionModalOpen}
        onCancel={closeDecisionModal}
        onOk={handleDecision}
        confirmLoading={processing}
        okText={decisionType === 'approve' ? 'Approve' : 'Reject'}
        okButtonProps={{ danger: decisionType === 'reject' }}
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>Employee:</Text> {selectedCorrection?.employeeId.substring(0, 8)}...
          <br />
          <Text strong>Date:</Text> {selectedCorrection?.workDate}
          <br />
          <Text strong>Reason:</Text> {selectedCorrection?.reason}
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
