// HCM_12 M396 — result acknowledgement (§25) + appeals (§26) on the review detail
// page. Acknowledge once (optionally disputing); a disputed result can become a
// formal appeal: SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED / RETURNED → CLOSED.
// An approved adjustment goes through the M394 override path (original preserved).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  appealsApi,
  type AppealStatus,
  type PerformanceAppeal,
  type PerformanceReview,
} from '../../api/performance'

const { Text } = Typography

const APPEAL_COLOR: Record<AppealStatus, string> = {
  SUBMITTED: 'blue',
  UNDER_REVIEW: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  RETURNED: 'orange',
  CLOSED: 'default',
}

export function AckAppealsCard({
  review,
  canHr,
  onChanged,
}: {
  review: PerformanceReview
  canHr: boolean
  onChanged: () => void
}) {
  const { message } = AntdApp.useApp()
  const [appeals, setAppeals] = useState<PerformanceAppeal[]>([])
  const [loading, setLoading] = useState(true)

  const [ackOpen, setAckOpen] = useState(false)
  const [ackForm] = Form.useForm<{ comments?: string; disputed: boolean }>()
  const [savingAck, setSavingAck] = useState(false)

  const [appealOpen, setAppealOpen] = useState(false)
  const [appealForm] = Form.useForm<{ reason: string }>()
  const [savingAppeal, setSavingAppeal] = useState(false)

  const [deciding, setDeciding] = useState<PerformanceAppeal | null>(null)
  const [decideForm] = Form.useForm<{
    decision: 'APPROVED' | 'REJECTED' | 'RETURNED'
    adjustedRating?: number
    notes?: string
  }>()
  const [savingDecision, setSavingDecision] = useState(false)

  const load = () => {
    setLoading(true)
    appealsApi
      .list({ reviewId: review.id })
      .then(setAppeals)
      .catch(() => message.error('Failed to load appeals'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [review.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn()
      message.success(ok)
      load()
      onChanged()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Action failed')
    }
  }

  const saveAck = async () => {
    const v = await ackForm.validateFields()
    setSavingAck(true)
    try {
      await appealsApi.acknowledge(review.id, v.comments, v.disputed ?? false)
      message.success(v.disputed ? 'Acknowledged with dispute' : 'Result acknowledged')
      setAckOpen(false)
      onChanged()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Acknowledgement failed')
    } finally {
      setSavingAck(false)
    }
  }

  const saveAppeal = async () => {
    const v = await appealForm.validateFields()
    setSavingAppeal(true)
    try {
      await appealsApi.submit(review.id, v.reason)
      message.success('Appeal submitted')
      setAppealOpen(false)
      appealForm.resetFields()
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Appeal failed')
    } finally {
      setSavingAppeal(false)
    }
  }

  const saveDecision = async () => {
    if (!deciding) return
    const v = await decideForm.validateFields()
    setSavingDecision(true)
    try {
      await appealsApi.decide(deciding.id, v.decision, v.adjustedRating, v.notes)
      message.success(`Appeal ${v.decision.toLowerCase()}`)
      setDeciding(null)
      load()
      onChanged()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Decision failed')
    } finally {
      setSavingDecision(false)
    }
  }

  const hasLiveAppeal = appeals.some((a) =>
    ['SUBMITTED', 'UNDER_REVIEW', 'RETURNED'].includes(a.status),
  )

  const columns: ColumnsType<PerformanceAppeal> = [
    { title: 'Reason', dataIndex: 'reason', ellipsis: true },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: AppealStatus) => <Tag color={APPEAL_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Original',
      dataIndex: 'originalRating',
      width: 90,
      align: 'center',
      render: (v) => (v != null ? Number(v).toFixed(2) : '—'),
    },
    {
      title: 'Adjusted',
      dataIndex: 'adjustedRating',
      width: 90,
      align: 'center',
      render: (v) => (v != null ? <Text strong>{Number(v).toFixed(2)}</Text> : '—'),
    },
    {
      title: 'Decision',
      key: 'decision',
      width: 200,
      render: (_, a) =>
        a.decidedAt ? (
          <Space direction="vertical" size={0}>
            <Text style={{ fontSize: 12 }}>{a.decisionNotes ?? '—'}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {a.decidedBy} · {new Date(a.decidedAt).toLocaleDateString()}
            </Text>
          </Space>
        ) : (
          '—'
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 220,
      render: (_, a) => (
        <Space size={4} wrap>
          {canHr && a.status === 'SUBMITTED' && (
            <Button
              size="small"
              onClick={() => act(() => appealsApi.takeUnderReview(a.id), 'Taken under review')}
            >
              Take under review
            </Button>
          )}
          {canHr && a.status === 'UNDER_REVIEW' && (
            <Button
              size="small"
              type="primary"
              onClick={() => {
                decideForm.resetFields()
                decideForm.setFieldsValue({ decision: 'APPROVED' })
                setDeciding(a)
              }}
            >
              Decide
            </Button>
          )}
          {a.status === 'RETURNED' && (
            <Button
              size="small"
              onClick={() => act(() => appealsApi.resubmit(a.id), 'Appeal resubmitted')}
            >
              Resubmit
            </Button>
          )}
          {canHr && (a.status === 'APPROVED' || a.status === 'REJECTED') && (
            <Button size="small" onClick={() => act(() => appealsApi.close(a.id), 'Appeal closed')}>
              Close
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      size="small"
      title="Acknowledgement & appeals (§25–§26)"
      style={{ marginTop: 16 }}
      extra={
        <Space size={8}>
          {!review.acknowledgedAt && review.finalRating != null && (
            <Button
              size="small"
              type="primary"
              onClick={() => {
                ackForm.resetFields()
                setAckOpen(true)
              }}
            >
              Acknowledge result
            </Button>
          )}
          {review.finalRating != null && !hasLiveAppeal && (
            <Button size="small" danger onClick={() => setAppealOpen(true)}>
              Submit appeal
            </Button>
          )}
        </Space>
      }
    >
      <Space direction="vertical" size={4} style={{ width: '100%', marginBottom: 8 }}>
        {review.acknowledgedAt ? (
          <Text>
            Acknowledged {new Date(review.acknowledgedAt).toLocaleString()}{' '}
            {review.acknowledgementDisputed ? (
              <Tag color="red">disputed</Tag>
            ) : (
              <Tag color="green">accepted</Tag>
            )}
            {review.acknowledgedComments && (
              <Text type="secondary"> — {review.acknowledgedComments}</Text>
            )}
          </Text>
        ) : (
          <Text type="secondary">
            {review.finalRating != null
              ? 'The employee has not acknowledged the result yet.'
              : 'Acknowledgement becomes available once a final rating exists.'}
          </Text>
        )}
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={appeals}
        pagination={false}
        locale={{ emptyText: 'No appeals for this review.' }}
      />

      {/* Acknowledge modal */}
      <Modal
        title="Acknowledge review result"
        open={ackOpen}
        onCancel={() => setAckOpen(false)}
        onOk={saveAck}
        confirmLoading={savingAck}
        okText="Acknowledge"
        destroyOnClose
      >
        <Form form={ackForm} layout="vertical">
          <Form.Item name="comments" label="Comments (optional)">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
          <Form.Item
            name="disputed"
            label="I dispute this result"
            valuePropName="checked"
            extra="Disputing records your disagreement; you can then submit a formal appeal."
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* Appeal modal */}
      <Modal
        title="Submit appeal"
        open={appealOpen}
        onCancel={() => setAppealOpen(false)}
        onOk={saveAppeal}
        confirmLoading={savingAppeal}
        okText="Submit appeal"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Form form={appealForm} layout="vertical">
          <Form.Item
            name="reason"
            label="Why should this result be reviewed?"
            rules={[{ required: true, min: 10, max: 2000 }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Decide modal (HR) */}
      <Modal
        title="Decide appeal (HR)"
        open={!!deciding}
        onCancel={() => setDeciding(null)}
        onOk={saveDecision}
        confirmLoading={savingDecision}
        okText="Record decision"
        destroyOnClose
      >
        <Form form={decideForm} layout="vertical">
          <Form.Item name="decision" label="Decision" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'APPROVED', label: 'Approve (optionally adjust the rating)' },
                { value: 'REJECTED', label: 'Reject (rating stands)' },
                { value: 'RETURNED', label: 'Return to employee for more information' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.decision !== c.decision}>
            {({ getFieldValue }) =>
              getFieldValue('decision') === 'APPROVED' ? (
                <Form.Item
                  name="adjustedRating"
                  label="Adjusted rating (0–5, optional — goes through the override path, original preserved)"
                >
                  <InputNumber min={0} max={5} step={0.1} style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="notes" label="Decision notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
