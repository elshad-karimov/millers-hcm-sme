import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ApiOutlined, DownloadOutlined, MailOutlined, PlayCircleOutlined } from '@ant-design/icons'
import {
  reportSchedulingApi,
  type EmailStatus,
  type ReportDefinition,
  type ReportDefinitionRequest,
  type ReportRun,
  type ReportRunStatus,
  type ReportSchedule,
  type ReportScheduleRequest,
  type ReportType,
  type WebhookStatus,
} from '../api/reportScheduling'
import { attachmentsApi } from '../api/attachments'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const REPORT_TYPES: ReportType[] = [
  'HEADCOUNT',
  'ATTRITION',
  'PAYROLL',
  'LEAVE',
  'ATTENDANCE',
  'TRAINING',
  'PERFORMANCE',
  'RECRUITMENT',
]

const STATUS_COLOR: Record<ReportRunStatus, string> = {
  RUNNING: 'gold',
  SUCCESS: 'green',
  FAILED: 'red',
}

const EMAIL_COLOR: Record<EmailStatus, string> = {
  NOT_REQUESTED: 'default',
  SKIPPED: 'orange',
  SENT: 'green',
  FAILED: 'red',
}

const EMAIL_LABEL: Record<EmailStatus, string> = {
  NOT_REQUESTED: '—',
  SKIPPED: 'Skipped',
  SENT: 'Sent',
  FAILED: 'Failed',
}

const WEBHOOK_COLOR: Record<WebhookStatus, string> = {
  NOT_REQUESTED: 'default',
  SKIPPED: 'orange',
  SENT: 'geekblue',
  FAILED: 'red',
}

const WEBHOOK_LABEL: Record<WebhookStatus, string> = {
  NOT_REQUESTED: '—',
  SKIPPED: 'Skipped',
  SENT: 'Sent',
  FAILED: 'Failed',
}

function downloadRunFile(run: ReportRun, onError: (m: string) => void) {
  if (!run.attachmentId) return
  attachmentsApi
    .download({
      id: run.attachmentId,
      attachmentNo: '—',
      bucket: '',
      objectKey: '',
      originalFilename: run.fileName ?? 'report.bin',
      contentType: null,
      sizeBytes: run.sizeBytes ?? null,
      ownerModule: 'REPORTING',
      ownerEntity: 'ReportRun',
      ownerId: run.id,
      uploadedBy: run.triggeredBy ?? '',
      uploadedAt: run.startedAt,
    })
    .catch((err) =>
      onError(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Download failed',
      ),
    )
}

// ============================================================================
//  Definitions
// ============================================================================
function DefinitionsTab({ onChanged }: { onChanged: () => void }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [rows, setRows] = useState<ReportDefinition[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ReportDefinition | null>(null)
  const [form] = Form.useForm<ReportDefinitionRequest & { paramsJson?: string }>()

  const load = () => {
    setLoading(true)
    reportSchedulingApi
      .definitions()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load definitions'),
      )
      .finally(() => setLoading(false))
  }
  useEffect(load, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ reportType: 'HEADCOUNT', defaultFormat: 'XLSX', active: true, paramsJson: '{}' })
    setOpen(true)
  }
  const openEdit = (d: ReportDefinition) => {
    setEditing(d)
    form.setFieldsValue({
      name: d.name,
      reportType: d.reportType,
      defaultFormat: d.defaultFormat,
      description: d.description ?? undefined,
      active: d.active,
      paramsJson: JSON.stringify(d.parameters ?? {}, null, 2),
    })
    setOpen(true)
  }

  const onFinish = async (v: ReportDefinitionRequest & { paramsJson?: string }) => {
    let parameters: Record<string, unknown> = {}
    try {
      parameters = v.paramsJson ? JSON.parse(v.paramsJson) : {}
    } catch {
      message.error('Parameters JSON is invalid')
      return
    }
    const payload: ReportDefinitionRequest = {
      name: v.name,
      reportType: v.reportType,
      defaultFormat: v.defaultFormat,
      description: v.description,
      active: v.active,
      parameters,
    }
    try {
      if (editing) {
        await reportSchedulingApi.updateDefinition(editing.id, payload)
        message.success('Definition updated')
      } else {
        await reportSchedulingApi.createDefinition(payload)
        message.success('Definition created')
      }
      setOpen(false)
      load()
      onChanged()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onRun = async (d: ReportDefinition) => {
    try {
      const run = await reportSchedulingApi.runDefinition(d.id)
      message.success(`Run ${run.runNo} ${run.status}`)
      onChanged()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Run failed',
      )
    }
  }

  const columns: ColumnsType<ReportDefinition> = [
    { title: 'Def #', dataIndex: 'definitionNo', width: 110 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Type', dataIndex: 'reportType', width: 130, render: (t: string) => <Tag>{t}</Tag> },
    { title: 'Format', dataIndex: 'defaultFormat', width: 90 },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    {
      title: '',
      width: 220,
      render: (_, d) => (
        <Space>
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => onRun(d)}>
            Run now
          </Button>
          {canEdit && (
            <Button size="small" onClick={() => openEdit(d)}>
              Edit
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="Report definitions"
      size="small"
      extra={canEdit && <Button type="primary" onClick={openCreate}>New definition</Button>}
    >
      <Table rowKey="id" size="small" loading={loading} columns={columns} dataSource={rows} pagination={false} />

      <Modal
        open={open}
        title={editing ? `Edit ${editing.definitionNo}` : 'New definition'}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        okText="Save"
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Row gutter={16}>
            <Col span={14}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="reportType" label="Type" rules={[{ required: true }]}>
                <Select options={REPORT_TYPES.map((t) => ({ value: t, label: t }))} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="defaultFormat" label="Format" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'XLSX', label: 'Excel' },
                    { value: 'PDF', label: 'PDF' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="paramsJson"
            label="Parameters (JSON)"
            tooltip='e.g. {"year": 2026} or {"from":"2026-05-01","to":"2026-05-31"}'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Input type="checkbox" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

// ============================================================================
//  Schedules
// ============================================================================
function SchedulesTab({ onChanged }: { onChanged: () => void }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<ReportSchedule[]>([])
  const [defs, setDefs] = useState<ReportDefinition[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<ReportScheduleRequest>()

  const load = () => {
    setLoading(true)
    Promise.all([reportSchedulingApi.schedules(), reportSchedulingApi.definitions()])
      .then(([s, d]) => {
        setRows(s)
        setDefs(d)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load schedules'),
      )
      .finally(() => setLoading(false))
  }
  useEffect(load, [])

  const defMap = useMemo(() => new Map(defs.map((d) => [d.id, d])), [defs])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ active: true, cron: '0 0 6 * * *' })
    setOpen(true)
  }

  const onFinish = async (v: ReportScheduleRequest) => {
    try {
      const s = await reportSchedulingApi.createSchedule(v)
      message.success(`Schedule ${s.scheduleNo} created · next run ${s.nextRunAt ?? '—'}`)
      setOpen(false)
      load()
      onChanged()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onRunNow = async (s: ReportSchedule) => {
    try {
      const updated = await reportSchedulingApi.runScheduleNow(s.id)
      message.success(`${updated.scheduleNo} fired · lastStatus=${updated.lastStatus}`)
      load()
      onChanged()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Run failed',
      )
    }
  }

  const columns: ColumnsType<ReportSchedule> = [
    { title: 'Sched #', dataIndex: 'scheduleNo', width: 110 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Definition',
      dataIndex: 'definitionId',
      render: (id: string) => {
        const d = defMap.get(id)
        return d ? `${d.definitionNo} — ${d.name}` : id
      },
    },
    { title: 'Cron', dataIndex: 'cron', width: 180 },
    {
      title: 'Next run',
      dataIndex: 'nextRunAt',
      width: 180,
      render: (s: string | null) => (s ? s.slice(0, 19).replace('T', ' ') : '—'),
    },
    {
      title: 'Last run',
      dataIndex: 'lastRunAt',
      width: 180,
      render: (s: string | null) => (s ? s.slice(0, 19).replace('T', ' ') : '—'),
    },
    {
      title: 'Last status',
      dataIndex: 'lastStatus',
      width: 110,
      render: (s: string | null) =>
        s ? <Tag color={s === 'SUCCESS' ? 'green' : 'red'}>{s}</Tag> : '—',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    {
      title: '',
      width: 130,
      render: (_, s) =>
        canEdit && (
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => onRunNow(s)}>
            Run now
          </Button>
        ),
    },
  ]

  return (
    <Card
      title="Schedules"
      size="small"
      extra={canEdit && <Button type="primary" onClick={openCreate}>New schedule</Button>}
    >
      <Table rowKey="id" size="small" loading={loading} columns={columns} dataSource={rows} pagination={false} />

      <Modal
        open={open}
        title="New schedule"
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        okText="Create"
        width={620}
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="definitionId" label="Definition" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={defs.map((d) => ({
                value: d.id,
                label: `${d.definitionNo} — ${d.name} (${d.reportType}/${d.defaultFormat})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="cron"
            label="Cron (6 fields: sec min hour dom month dow)"
            tooltip="Spring cron — e.g. '0 0 6 * * *' = daily 06:00, '0 0 0 1 * *' = first of month, '0 */5 * * * *' = every 5 minutes"
            rules={[{ required: true }]}
          >
            <Input placeholder="0 0 6 * * *" />
          </Form.Item>
          <Form.Item name="recipients" label="Email recipients (comma-separated)">
            <Input placeholder="hr@millers.az, audit@millers.az" />
          </Form.Item>
          <Form.Item name="webhookType" label="Webhook channel" initialValue="NONE">
            <Select
              options={[
                { value: 'NONE',  label: 'No webhook' },
                { value: 'SLACK', label: 'Slack incoming webhook' },
                { value: 'TEAMS', label: 'MS Teams connector' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="webhookUrl"
            label="Webhook URL"
            tooltip="Slack incoming-webhook URL (https://hooks.slack.com/…) or Teams connector URL"
          >
            <Input placeholder="https://hooks.slack.com/services/T0/B0/xxxx" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

// ============================================================================
//  Runs
// ============================================================================
function RunsTab({ refreshKey }: { refreshKey: number }) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canResend = hasRole(...RoleSets.HR_ADMIN_WRITE)
  const [rows, setRows] = useState<ReportRun[]>([])
  const [loading, setLoading] = useState(false)
  const [resendingId, setResendingId] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    reportSchedulingApi
      .runs({ size: 100 })
      .then((p) => setRows(p.content))
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load runs'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [refreshKey])

  const onResend = (id: string) => {
    setResendingId(id)
    reportSchedulingApi
      .resendRunEmail(id)
      .then((r) => {
        if (r.emailStatus === 'SENT') {
          message.success(`Email resent to ${r.emailRecipients}`)
        } else if (r.emailStatus === 'FAILED') {
          message.error(`Email failed again: ${r.emailError}`)
        }
        load()
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Resend failed'))
      .finally(() => setResendingId(null))
  }

  const onResendWebhook = (id: string) => {
    setResendingId(id)
    reportSchedulingApi
      .resendRunWebhook(id)
      .then((r) => {
        if (r.webhookStatus === 'SENT') {
          message.success(`Webhook resent to ${r.webhookTarget}`)
        } else if (r.webhookStatus === 'FAILED') {
          message.error(`Webhook failed again: ${r.webhookError}`)
        }
        load()
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Webhook resend failed'))
      .finally(() => setResendingId(null))
  }

  const columns: ColumnsType<ReportRun> = [
    { title: 'Run #', dataIndex: 'runNo', width: 110 },
    { title: 'Report', dataIndex: 'reportType', width: 130, render: (t: string) => <Tag>{t}</Tag> },
    { title: 'Format', dataIndex: 'format', width: 80 },
    {
      title: 'Trigger',
      dataIndex: 'triggerSource',
      width: 110,
      render: (s: string) => <Tag>{s}</Tag>,
    },
    { title: 'File name', dataIndex: 'fileName', ellipsis: true },
    {
      title: 'Size',
      dataIndex: 'sizeBytes',
      width: 100,
      render: (b: number | null) => (b == null ? '—' : `${(b / 1024).toFixed(1)} KB`),
    },
    {
      title: 'Started',
      dataIndex: 'startedAt',
      width: 180,
      render: (s: string) => s.slice(0, 19).replace('T', ' '),
    },
    {
      title: 'Duration',
      width: 100,
      render: (_, r) => {
        if (!r.finishedAt) return '—'
        const ms = new Date(r.finishedAt).getTime() - new Date(r.startedAt).getTime()
        return `${(ms / 1000).toFixed(2)} s`
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: ReportRunStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Email',
      width: 200,
      render: (_, r) => {
        const tooltip =
          r.emailStatus === 'SENT'
            ? `Sent to ${r.emailRecipients ?? '?'} at ${r.emailSentAt?.slice(0, 19).replace('T', ' ')}`
            : r.emailStatus === 'FAILED'
            ? r.emailError ?? 'delivery failed'
            : r.emailStatus === 'SKIPPED'
            ? r.emailError ?? 'no recipients'
            : 'Email not requested for this run'
        return (
          <Tooltip title={tooltip}>
            <Tag color={EMAIL_COLOR[r.emailStatus]} icon={<MailOutlined />}>
              {EMAIL_LABEL[r.emailStatus]}
            </Tag>
          </Tooltip>
        )
      },
    },
    {
      title: 'Webhook',
      width: 140,
      render: (_, r) => {
        const tooltip =
          r.webhookStatus === 'SENT'
            ? `Posted to ${r.webhookTarget ?? '?'} at ${r.webhookSentAt?.slice(0, 19).replace('T', ' ')}`
            : r.webhookStatus === 'FAILED'
            ? r.webhookError ?? 'webhook failed'
            : r.webhookStatus === 'SKIPPED'
            ? r.webhookError ?? 'no webhook configured'
            : 'Webhook not requested for this run'
        return (
          <Tooltip title={tooltip}>
            <Tag color={WEBHOOK_COLOR[r.webhookStatus]} icon={<ApiOutlined />}>
              {WEBHOOK_LABEL[r.webhookStatus]}
            </Tag>
          </Tooltip>
        )
      },
    },
    {
      title: '',
      width: 280,
      render: (_, r) => (
        <Space size={4}>
          {r.status === 'SUCCESS' && r.attachmentId ? (
            <Tooltip title="Download generated file">
              <Button
                size="small"
                icon={<DownloadOutlined />}
                onClick={() => downloadRunFile(r, (msg) => message.error(msg))}
              >
                Download
              </Button>
            </Tooltip>
          ) : null}
          {canResend &&
          r.status === 'SUCCESS' &&
          r.attachmentId &&
          r.scheduleId &&
          (r.emailStatus === 'FAILED' || r.emailStatus === 'SENT') ? (
            <Tooltip
              title={
                r.emailStatus === 'FAILED'
                  ? 'Retry sending this report by email'
                  : 'Re-send the same file to the recipients'
              }
            >
              <Button
                size="small"
                icon={<MailOutlined />}
                loading={resendingId === r.id}
                onClick={() => onResend(r.id)}
              >
                Resend email
              </Button>
            </Tooltip>
          ) : null}
          {canResend &&
          r.status === 'SUCCESS' &&
          r.scheduleId &&
          (r.webhookStatus === 'FAILED' || r.webhookStatus === 'SENT') ? (
            <Tooltip
              title={
                r.webhookStatus === 'FAILED'
                  ? 'Retry posting to the Slack/Teams webhook'
                  : 'Re-post the same summary to the webhook'
              }
            >
              <Button
                size="small"
                icon={<ApiOutlined />}
                loading={resendingId === r.id}
                onClick={() => onResendWebhook(r.id)}
              >
                Resend webhook
              </Button>
            </Tooltip>
          ) : null}
          {r.status === 'FAILED' && r.errorMessage ? (
            <Typography.Text type="danger" style={{ fontSize: 12 }}>
              {r.errorMessage.length > 60 ? r.errorMessage.slice(0, 60) + '…' : r.errorMessage}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
  ]

  return (
    <Card title="Run history" size="small">
      <Table rowKey="id" size="small" loading={loading} columns={columns} dataSource={rows} pagination={false} />
    </Card>
  )
}

// ============================================================================
//  Page shell
// ============================================================================
export function ReportSchedulesPage() {
  const [refreshKey, setRefreshKey] = useState(0)
  const onChanged = () => setRefreshKey((k) => k + 1)
  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Scheduled reports
        </Typography.Title>
      }
    >
      <Tabs
        items={[
          {
            key: 'definitions',
            label: 'Definitions',
            children: <DefinitionsTab onChanged={onChanged} />,
          },
          {
            key: 'schedules',
            label: 'Schedules',
            children: <SchedulesTab onChanged={onChanged} />,
          },
          { key: 'runs', label: 'Run history', children: <RunsTab refreshKey={refreshKey} /> },
        ]}
      />
    </Card>
  )
}
