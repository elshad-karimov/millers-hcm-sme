import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, DatePicker, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography, message
} from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  listResignations, submitResignation, withdrawResignation,
  type ResignationResponse, type ResignationStatus, type PageResponse,
} from '../api/offboarding'

const { Title } = Typography
const { TextArea } = Input

const STATUS_COLOR: Record<ResignationStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
  CANCELLED: 'default',
}

export function ResignationRequestPage() {
  const [form] = Form.useForm()
  const [data, setData] = useState<PageResponse<ResignationResponse> | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<ResignationResponse | null>(null)
  const [withdrawReason, setWithdrawReason] = useState('')
  const [page, setPage] = useState(0)
  const [showForm, setShowForm] = useState(false)
  const [statusFilter, setStatusFilter] = useState<ResignationStatus | undefined>()
  const [tick, setTick] = useState(0)

  const refresh = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    setLoading(true)
    listResignations({ status: statusFilter, page, size: 20 })
      .then(setData)
      .catch(() => message.error('Failed to load resignations'))
      .finally(() => setLoading(false))
  }, [tick, page, statusFilter])

  const handleSubmit = async (values: Record<string, unknown>) => {
    setSubmitting(true)
    try {
      await submitResignation({
        employeeId: values.employeeId as string,
        resignationDate: (values.resignationDate as dayjs.Dayjs).format('YYYY-MM-DD'),
        proposedLastWorkingDate: (values.proposedLastWorkingDate as dayjs.Dayjs).format('YYYY-MM-DD'),
        reasonText: values.reasonText as string | undefined,
        comments: values.comments as string | undefined,
      })
      message.success('Resignation submitted successfully')
      form.resetFields()
      setShowForm(false)
      refresh()
    } catch {
      message.error('Submission failed')
    } finally {
      setSubmitting(false)
    }
  }

  const handleWithdraw = async () => {
    if (!withdrawTarget) return
    setWithdrawing(withdrawTarget.id)
    try {
      await withdrawResignation(withdrawTarget.id, withdrawReason ? { withdrawalReason: withdrawReason } : undefined)
      message.success('Resignation withdrawn')
      setWithdrawTarget(null)
      setWithdrawReason('')
      refresh()
    } catch {
      message.error('Failed to withdraw')
    } finally {
      setWithdrawing(null)
    }
  }

  const columns = [
    {
      title: 'Resignation No',
      dataIndex: 'resignationNo',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      render: (id: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{id.slice(0, 8)}…</span>,
    },
    {
      title: 'Resignation Date',
      dataIndex: 'resignationDate',
    },
    {
      title: 'Proposed LWD',
      dataIndex: 'proposedLastWorkingDate',
    },
    {
      title: 'Approved LWD',
      dataIndex: 'approvedLastWorkingDate',
      render: (v?: string) => v ?? '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (v: ResignationStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: 'Withdrawal Reason',
      dataIndex: 'withdrawalReason',
      render: (v?: string) => v
        ? <Tooltip title={v}><span style={{ color: '#888', fontSize: 12 }}>{v.length > 30 ? v.slice(0, 30) + '…' : v}</span></Tooltip>
        : '—',
    },
    {
      title: 'Actions',
      render: (_: unknown, r: ResignationResponse) =>
        (r.status === 'SUBMITTED' || r.status === 'APPROVED') ? (
          <Button
            size="small"
            danger
            loading={withdrawing === r.id}
            onClick={() => { setWithdrawTarget(r); setWithdrawReason('') }}
          >
            Withdraw
          </Button>
        ) : null,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} align="center">
        <LogoutOutlined style={{ fontSize: 22 }} />
        <Title level={3} style={{ margin: 0 }}>Resignations</Title>
        <Button type="primary" onClick={() => setShowForm(f => !f)}>
          {showForm ? 'Cancel' : 'New Resignation'}
        </Button>
      </Space>

      {showForm && (
        <Card title="Submit Resignation" style={{ marginBottom: 24 }}>
          <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ maxWidth: 600 }}>
            <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}>
              <Input placeholder="UUID of the employee" />
            </Form.Item>
            <Form.Item name="resignationDate" label="Resignation Date" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="proposedLastWorkingDate"
              label="Proposed Last Working Date"
              rules={[{ required: true }]}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="reasonText" label="Reason">
              <TextArea rows={3} />
            </Form.Item>
            <Form.Item name="comments" label="Comments">
              <TextArea rows={2} />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting}>
              Submit
            </Button>
          </Form>
        </Card>
      )}

      <Card
        title="All Resignations"
        extra={
          <Select
            allowClear
            placeholder="Filter by status"
            style={{ width: 180 }}
            value={statusFilter}
            onChange={v => { setStatusFilter(v); setPage(0) }}
            options={(['SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'] as ResignationStatus[])
              .map(s => ({ label: s, value: s }))}
          />
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={data?.content ?? []}
          columns={columns}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: data?.totalElements ?? 0,
            onChange: p => setPage(p - 1),
          }}
        />
      </Card>
      <Modal
        title={`Withdraw ${withdrawTarget?.resignationNo ?? 'Resignation'}`}
        open={withdrawTarget !== null}
        onCancel={() => setWithdrawTarget(null)}
        onOk={handleWithdraw}
        okText="Confirm Withdrawal"
        okButtonProps={{ danger: true, loading: withdrawing !== null }}
      >
        <p style={{ color: '#888' }}>
          This will cancel the offboarding case and notify the approver. This action cannot be undone.
        </p>
        <Input.TextArea
          rows={3}
          placeholder="Reason for withdrawal (optional)"
          value={withdrawReason}
          onChange={e => setWithdrawReason(e.target.value)}
        />
      </Modal>
    </div>
  )
}
