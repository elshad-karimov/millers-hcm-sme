import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import {
  recruitmentApi,
  type JobPosting,
  type JobPostingRequest,
  type PostingChannel,
  type PostingStatus,
} from '../api/recruitment'

/**
 * M278 — Recruitment PRD §8: per-vacancy job postings panel.
 * Dropped into VacancyDetailPage; renders nothing while loading and
 * a compact table + "New posting" once loaded.
 */

const CHANNEL_OPTIONS: { value: PostingChannel; label: string }[] = [
  { value: 'EXTERNAL', label: 'External (career portal)' },
  { value: 'INTERNAL', label: 'Internal (employees)' },
  { value: 'JOB_BOARD', label: 'Job board' },
  { value: 'AGENCY', label: 'Agency' },
  { value: 'SOCIAL', label: 'Social media' },
]

const LANGUAGE_OPTIONS = [
  { value: 'az', label: 'Azərbaycan' },
  { value: 'en', label: 'English' },
  { value: 'ru', label: 'Русский' },
  { value: 'tr', label: 'Türkçe' },
]

const STATUS_COLOR: Record<PostingStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  PAUSED: 'orange',
  EXPIRED: 'red',
  CLOSED: 'default',
}

interface FormValues {
  channel: PostingChannel
  language: string
  title?: string
  description?: string
  requirements?: string
  benefitsDescription?: string
  salaryVisible?: boolean
  applicationDeadline?: dayjs.Dayjs
}

export function JobPostingsPanel({ vacancyId, canEdit }: { vacancyId: string; canEdit: boolean }) {
  const { message } = AntdApp.useApp()
  const [postings, setPostings] = useState<JobPosting[]>([])
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<JobPosting | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const load = useCallback(() => {
    recruitmentApi
      .postingsForVacancy(vacancyId)
      .then(setPostings)
      .catch(() => setPostings([]))
  }, [vacancyId])

  useEffect(load, [load])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ channel: 'EXTERNAL', language: 'az', salaryVisible: false })
    setFormOpen(true)
  }

  const openEdit = (p: JobPosting) => {
    setEditing(p)
    form.setFieldsValue({
      channel: p.channel,
      language: p.language,
      title: p.title,
      description: p.description ?? undefined,
      requirements: p.requirements ?? undefined,
      benefitsDescription: p.benefitsDescription ?? undefined,
      salaryVisible: p.salaryVisible,
      applicationDeadline: p.applicationDeadline ? dayjs(p.applicationDeadline) : undefined,
    })
    setFormOpen(true)
  }

  const save = async () => {
    const v = await form.validateFields()
    const payload: JobPostingRequest = {
      channel: v.channel,
      language: v.language,
      title: v.title,
      description: v.description,
      requirements: v.requirements,
      benefitsDescription: v.benefitsDescription,
      salaryVisible: v.salaryVisible,
      applicationDeadline: v.applicationDeadline?.format('YYYY-MM-DD'),
    }
    setSaving(true)
    try {
      if (editing) {
        await recruitmentApi.updatePosting(editing.id, payload)
        message.success('Posting updated')
      } else {
        await recruitmentApi.createPosting(vacancyId, payload)
        message.success('Posting created (DRAFT)')
      }
      setFormOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const act = async (fn: () => Promise<JobPosting>, ok: string) => {
    try {
      await fn()
      message.success(ok)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Action failed',
      )
    }
  }

  return (
    <Card
      size="small"
      title="Job postings"
      extra={
        canEdit && (
          <Button size="small" type="primary" onClick={openCreate}>
            New posting
          </Button>
        )
      }
    >
      <Table<JobPosting>
        rowKey="id"
        size="small"
        dataSource={postings}
        pagination={false}
        locale={{ emptyText: 'No postings yet — the vacancy is not advertised anywhere' }}
        columns={[
          { title: '#', dataIndex: 'postingNo', width: 110 },
          {
            title: 'Channel',
            dataIndex: 'channel',
            width: 120,
            render: (c: PostingChannel) => <Tag>{c.replace(/_/g, ' ')}</Tag>,
          },
          {
            title: 'Lang',
            dataIndex: 'language',
            width: 60,
            render: (l: string) => l.toUpperCase(),
          },
          { title: 'Title', dataIndex: 'title', ellipsis: true },
          {
            title: 'Deadline',
            dataIndex: 'applicationDeadline',
            width: 110,
            render: (d?: string | null) => d ?? '—',
          },
          {
            title: 'Status',
            dataIndex: 'status',
            width: 110,
            render: (s: PostingStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
          },
          ...(canEdit
            ? [
                {
                  title: 'Actions',
                  width: 230,
                  render: (_: unknown, p: JobPosting) => (
                    <Space size={4}>
                      {(p.status === 'DRAFT' || p.status === 'PAUSED') && (
                        <>
                          <Button size="small" onClick={() => openEdit(p)}>
                            Edit
                          </Button>
                          <Button
                            size="small"
                            type="primary"
                            onClick={() =>
                              act(() => recruitmentApi.publishPosting(p.id), 'Posting published')
                            }
                          >
                            Publish
                          </Button>
                        </>
                      )}
                      {p.status === 'PUBLISHED' && (
                        <Button
                          size="small"
                          onClick={() =>
                            act(() => recruitmentApi.pausePosting(p.id), 'Posting paused')
                          }
                        >
                          Pause
                        </Button>
                      )}
                      {p.status !== 'CLOSED' && (
                        <Popconfirm
                          title="Close this posting?"
                          onConfirm={() =>
                            act(() => recruitmentApi.closePosting(p.id), 'Posting closed')
                          }
                        >
                          <Button size="small" danger>
                            Close
                          </Button>
                        </Popconfirm>
                      )}
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <Modal
        open={formOpen}
        title={editing ? `Edit posting ${editing.postingNo}` : 'New posting'}
        onCancel={() => setFormOpen(false)}
        onOk={save}
        okText={editing ? 'Save' : 'Create'}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Space.Compact block>
            <Form.Item
              name="channel"
              label="Channel"
              rules={[{ required: true }]}
              style={{ flex: 1, marginRight: 8 }}
            >
              <Select options={CHANNEL_OPTIONS} />
            </Form.Item>
            <Form.Item name="language" label="Language" style={{ width: 140 }}>
              <Select options={LANGUAGE_OPTIONS} />
            </Form.Item>
          </Space.Compact>
          <Form.Item
            name="title"
            label="Title"
            tooltip="Leave blank to use the vacancy title"
            rules={[{ max: 200 }]}
          >
            <Input placeholder="(defaults from vacancy)" />
          </Form.Item>
          <Form.Item
            name="description"
            label="Description"
            tooltip="Leave blank to use the vacancy description"
          >
            <Input.TextArea rows={3} placeholder="(defaults from vacancy)" />
          </Form.Item>
          <Form.Item name="requirements" label="Requirements">
            <Input.TextArea rows={2} placeholder="(defaults from vacancy)" />
          </Form.Item>
          <Form.Item name="benefitsDescription" label="Benefits">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size="large">
            <Form.Item name="salaryVisible" label="Show salary range" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="applicationDeadline" label="Application deadline">
              <DatePicker />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
