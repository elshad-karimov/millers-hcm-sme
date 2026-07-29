// M122 — HR admin page for pre-boarding invites.
//
// HR picks a newly-hired employee, issues a magic-link URL, copies it
// once to send via their preferred channel (email/Slack/etc.), and
// later reviews the candidate's submission before clicking Complete to
// graduate the data into the Employee record.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  preboardingApi,
  type InviteDetail,
  type InviteSummary,
  type IssueRequest,
  type PreboardingStatus,
} from '../api/preboarding'
import { EmployeePicker } from '../components/EmployeePicker'

const { Title, Text, Paragraph } = Typography

const STATUS_COLOR: Record<PreboardingStatus, string> = {
  DRAFT: 'default',
  SENT: 'blue',
  OPENED: 'cyan',
  SUBMITTED: 'gold',
  COMPLETED: 'green',
  EXPIRED: 'orange',
  REVOKED: 'red',
}

export function PreboardingPage() {
  const { message } = AntdApp.useApp()
  const [invites, setInvites] = useState<InviteSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [issueOpen, setIssueOpen] = useState(false)
  const [revealedToken, setRevealedToken] = useState<{ summary: InviteSummary; token: string } | null>(null)
  const [detail, setDetail] = useState<InviteDetail | null>(null)
  const [form] = Form.useForm()

  const reload = () => {
    setLoading(true)
    preboardingApi.list()
      .then(setInvites)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load invites'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onIssue = async () => {
    try {
      const values = await form.validateFields()
      const body: IssueRequest = {
        employeeId: values.employeeId,
        expiresInDays: values.expiresInDays,
        note: values.note,
      }
      const result = await preboardingApi.issue(body)
      setIssueOpen(false)
      form.resetFields()
      setRevealedToken({ summary: result.summary, token: result.plaintextToken })
      reload()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Issue failed')
    }
  }

  const onComplete = async (id: string) => {
    try {
      await preboardingApi.complete(id)
      message.success('Submission applied to employee record')
      setDetail(null)
      reload()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Complete failed')
    }
  }

  const onRevoke = async (id: string) => {
    try {
      await preboardingApi.revoke(id, 'Revoked by admin')
      message.success('Invite revoked')
      reload()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Revoke failed')
    }
  }

  const onView = async (id: string) => {
    try {
      const data = await preboardingApi.get(id)
      setDetail(data)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load detail')
    }
  }

  const copy = (s: string) => {
    navigator.clipboard.writeText(s)
    message.success('Copied to clipboard')
  }

  const portalUrl = (token: string) =>
    `${window.location.origin}/preboarding/${token}`

  const columns = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      render: (name: string, r: InviteSummary) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: PreboardingStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Expires',
      dataIndex: 'expiresAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
      width: 110,
    },
    {
      title: 'Sent',
      dataIndex: 'sentAt',
      render: (v: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'),
      width: 150,
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      render: (v: string | null) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : <Text type="secondary">—</Text>),
      width: 150,
    },
    {
      title: 'Created by',
      dataIndex: 'createdBy',
      width: 130,
    },
    {
      title: 'Actions',
      render: (_: unknown, r: InviteSummary) => (
        <Space>
          <a onClick={() => onView(r.id)}>View</a>
          {r.status === 'SUBMITTED' && (
            <Popconfirm
              title="Apply submission to the employee record?"
              onConfirm={() => onComplete(r.id)}
            >
              <a style={{ color: '#52c41a' }}>Complete</a>
            </Popconfirm>
          )}
          {(r.status === 'SENT' || r.status === 'OPENED' || r.status === 'SUBMITTED') && (
            <Popconfirm title="Revoke this invite?" onConfirm={() => onRevoke(r.id)}>
              <a style={{ color: 'red' }}>Revoke</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>Pre-boarding invites</Title>
        <Button type="primary" onClick={() => setIssueOpen(true)}>Issue invite</Button>
      </Space>

      <Card size="small">
        <Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 12 }}>
          Issue a magic link to a newly-hired employee to capture personal info,
          emergency contacts and dependents before day 1. Review the submission
          here and click <strong>Complete</strong> to graduate it into the
          Employee record.
        </Paragraph>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={invites}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* ── Issue modal ─────────────────────────────────── */}
      <Modal
        open={issueOpen}
        onCancel={() => { setIssueOpen(false); form.resetFields() }}
        onOk={onIssue}
        okText="Issue"
        title="Issue pre-boarding invite"
      >
        <Form form={form} layout="vertical" initialValues={{ expiresInDays: 14 }}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Pick an employee' }]}
          >
            <EmployeePicker placeholder="Search by name" />
          </Form.Item>
          <Form.Item name="expiresInDays" label="Expires in (days)">
            <InputNumber min={1} max={90} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="note" label="Note (internal)">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Reveal-once modal ────────────────────────────── */}
      <Modal
        open={!!revealedToken}
        onCancel={() => setRevealedToken(null)}
        onOk={() => setRevealedToken(null)}
        okText="I've copied it"
        cancelButtonProps={{ style: { display: 'none' } }}
        title="Magic-link URL"
        width={660}
        maskClosable={false}
      >
        {revealedToken && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Alert
              type="warning"
              showIcon
              message="This URL is shown once."
              description={`Send it to ${revealedToken.summary.employeeName} via your usual channel — it cannot be retrieved later.`}
            />
            <div style={{
              background: '#fafafa', border: '1px solid #d9d9d9', borderRadius: 4,
              padding: 12, fontFamily: 'monospace', fontSize: 13, wordBreak: 'break-all',
            }}>
              {portalUrl(revealedToken.token)}
            </div>
            <Button type="primary" onClick={() => copy(portalUrl(revealedToken.token))}>
              Copy URL
            </Button>
          </Space>
        )}
      </Modal>

      {/* ── Detail drawer ────────────────────────────────── */}
      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        width={680}
        title={detail
          ? `${detail.summary.employeeName} — ${detail.summary.status}`
          : ''
        }
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space>
              <Tag color={STATUS_COLOR[detail.summary.status]}>{detail.summary.status}</Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Expires {dayjs(detail.summary.expiresAt).format('YYYY-MM-DD')}
              </Text>
            </Space>
            {detail.summary.note && (
              <Alert type="info" message={detail.summary.note} />
            )}
            <Text type="secondary" style={{ fontSize: 11 }}>SUBMISSION</Text>
            {detail.payload ? (
              <pre style={{
                background: '#fafafa', padding: 10, borderRadius: 4,
                fontSize: 11, maxHeight: 460, overflow: 'auto', whiteSpace: 'pre-wrap',
              }}>
                {JSON.stringify(detail.payload, null, 2)}
              </pre>
            ) : (
              <Text type="secondary">Candidate hasn't submitted yet.</Text>
            )}
            {detail.summary.status === 'SUBMITTED' && (
              <Popconfirm
                title="Apply this submission to the employee record?"
                onConfirm={() => onComplete(detail.summary.id)}
              >
                <Button type="primary">Complete and apply</Button>
              </Popconfirm>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  )
}
