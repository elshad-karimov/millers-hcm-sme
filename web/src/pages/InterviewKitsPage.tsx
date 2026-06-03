// M85 — Interview-kit admin: list, create / edit kit, manage its weighted
// questions. Recruiters / HR use this to define scoring templates that
// interviewers later fill in against candidates.

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  interviewKitsApi,
  type InterviewKit,
  type InterviewKitRequest,
  type InterviewQuestion,
  type InterviewQuestionRequest,
} from '../api/interviews'

export function InterviewKitsPage() {
  const { message } = AntdApp.useApp()
  const [kits, setKits] = useState<InterviewKit[]>([])
  const [loading, setLoading] = useState(true)

  // Kit drawer state
  const [kitDrawerOpen, setKitDrawerOpen] = useState(false)
  const [editingKit, setEditingKit] = useState<InterviewKit | null>(null)
  const [kitForm] = Form.useForm<InterviewKitRequest>()

  // Question modal state — opens for a kit
  const [questionsFor, setQuestionsFor] = useState<InterviewKit | null>(null)
  const [questions, setQuestions] = useState<InterviewQuestion[]>([])
  const [questionsLoading, setQuestionsLoading] = useState(false)
  const [questionEditing, setQuestionEditing] = useState<InterviewQuestion | null>(null)
  const [questionForm] = Form.useForm<InterviewQuestionRequest>()
  const [questionModalOpen, setQuestionModalOpen] = useState(false)

  const load = () => {
    setLoading(true)
    interviewKitsApi
      .list(undefined, false)
      .then(setKits)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load kits'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  // ── Kit CRUD ─────────────────────────────────────────────────────────────

  const openCreateKit = () => {
    setEditingKit(null)
    kitForm.resetFields()
    kitForm.setFieldsValue({ active: true } as InterviewKitRequest)
    setKitDrawerOpen(true)
  }

  const openEditKit = (k: InterviewKit) => {
    setEditingKit(k)
    kitForm.setFieldsValue({
      code: k.code,
      name: k.name,
      description: k.description ?? undefined,
      jobFamilyId: k.jobFamilyId ?? undefined,
      active: k.active,
    })
    setKitDrawerOpen(true)
  }

  const submitKit = async (values: InterviewKitRequest) => {
    try {
      if (editingKit) {
        await interviewKitsApi.update(editingKit.id, values)
        message.success('Kit updated')
      } else {
        await interviewKitsApi.create(values)
        message.success('Kit created')
      }
      setKitDrawerOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const deactivateKit = async (k: InterviewKit) => {
    try {
      await interviewKitsApi.deactivate(k.id)
      message.success('Kit deactivated')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Deactivate failed',
      )
    }
  }

  // ── Question modal ───────────────────────────────────────────────────────

  const openQuestions = (k: InterviewKit) => {
    setQuestionsFor(k)
    setQuestionsLoading(true)
    interviewKitsApi
      .listQuestions(k.id, false)
      .then(setQuestions)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load questions'),
      )
      .finally(() => setQuestionsLoading(false))
  }

  const reloadQuestions = (kitId: string) => {
    interviewKitsApi
      .listQuestions(kitId, false)
      .then(setQuestions)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to reload questions'),
      )
  }

  const openAddQuestion = () => {
    setQuestionEditing(null)
    questionForm.resetFields()
    questionForm.setFieldsValue({
      weight: 1,
      sortOrder: questions.length,
      required: true,
      active: true,
    } as InterviewQuestionRequest)
    setQuestionModalOpen(true)
  }

  const openEditQuestion = (q: InterviewQuestion) => {
    setQuestionEditing(q)
    questionForm.setFieldsValue({
      questionText: q.questionText,
      weight: q.weight,
      sortOrder: q.sortOrder,
      required: q.required,
      active: q.active,
    })
    setQuestionModalOpen(true)
  }

  const submitQuestion = async (values: InterviewQuestionRequest) => {
    if (!questionsFor) return
    try {
      if (questionEditing) {
        await interviewKitsApi.updateQuestion(questionsFor.id, questionEditing.id, values)
        message.success('Question updated')
      } else {
        await interviewKitsApi.addQuestion(questionsFor.id, values)
        message.success('Question added')
      }
      setQuestionModalOpen(false)
      reloadQuestions(questionsFor.id)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const deleteQuestion = async (q: InterviewQuestion) => {
    if (!questionsFor) return
    try {
      await interviewKitsApi.deleteQuestion(questionsFor.id, q.id)
      message.success('Question deleted')
      reloadQuestions(questionsFor.id)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Delete failed',
      )
    }
  }

  // ── Columns ──────────────────────────────────────────────────────────────

  const kitCols: ColumnsType<InterviewKit> = [
    { title: 'Code', dataIndex: 'code', width: 200 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Description', dataIndex: 'description', render: (v) => v ?? '—' },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) =>
        v ? <Tag color="green">YES</Tag> : <Tag>INACTIVE</Tag>,
    },
    {
      title: '',
      width: 260,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openQuestions(r)}>Questions</Button>
          <Button size="small" type="link" onClick={() => openEditKit(r)}>Edit</Button>
          {r.active && (
            <Popconfirm title="Deactivate this kit?" onConfirm={() => deactivateKit(r)}>
              <Button size="small" type="link" danger>Deactivate</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const questionCols: ColumnsType<InterviewQuestion> = [
    { title: '#', dataIndex: 'sortOrder', width: 60 },
    { title: 'Question', dataIndex: 'questionText' },
    { title: 'Weight', dataIndex: 'weight', width: 80 },
    {
      title: 'Required',
      dataIndex: 'required',
      width: 100,
      render: (v: boolean) => (v ? <Tag color="red">YES</Tag> : '—'),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) =>
        v ? <Tag color="green">YES</Tag> : <Tag>INACTIVE</Tag>,
    },
    {
      title: '',
      width: 160,
      render: (_, r) => (
        <Space>
          <Button size="small" type="link" onClick={() => openEditQuestion(r)}>Edit</Button>
          <Popconfirm title="Delete this question?" onConfirm={() => deleteQuestion(r)}>
            <Button size="small" type="link" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Interview kits
          </Typography.Title>
        }
        extra={<Button type="primary" onClick={openCreateKit}>New kit</Button>}
      >
        <Table
          rowKey="id"
          columns={kitCols}
          dataSource={kits}
          loading={loading}
          pagination={false}
        />
      </Card>

      {/* ── Kit drawer ─────────────────────────────────────── */}
      <Drawer
        open={kitDrawerOpen}
        title={editingKit ? `Edit ${editingKit.code}` : 'New interview kit'}
        width={560}
        onClose={() => setKitDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={kitForm} layout="vertical" onFinish={submitKit}>
          <Form.Item
            label="Code"
            name="code"
            rules={[
              { required: true, message: 'Required' },
              { pattern: /^[A-Z0-9_-]+$/, message: 'Uppercase alphanumeric, _ or -' },
            ]}
          >
            <Input disabled={!!editingKit} placeholder="ENG_BACKEND_LOOP" />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="Job family ID (optional)" name="jobFamilyId"
            extra="Soft FK — links the kit to a job family so the picker can filter">
            <Input />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              {editingKit ? 'Save changes' : 'Create'}
            </Button>
            <Button onClick={() => setKitDrawerOpen(false)}>Cancel</Button>
          </Space>
        </Form>
      </Drawer>

      {/* ── Questions modal ─────────────────────────────────── */}
      <Modal
        open={!!questionsFor}
        title={questionsFor ? `Questions — ${questionsFor.name}` : ''}
        width={900}
        footer={<Button onClick={() => setQuestionsFor(null)}>Close</Button>}
        onCancel={() => setQuestionsFor(null)}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button type="primary" onClick={openAddQuestion}>Add question</Button>
          <Table
            rowKey="id"
            columns={questionCols}
            dataSource={questions}
            loading={questionsLoading}
            pagination={false}
            size="small"
            locale={{ emptyText: <Empty description="No questions yet" /> }}
          />
        </Space>
      </Modal>

      {/* ── Question add/edit modal ─────────────────────────── */}
      <Modal
        open={questionModalOpen}
        title={questionEditing ? 'Edit question' : 'New question'}
        onCancel={() => setQuestionModalOpen(false)}
        onOk={() => questionForm.submit()}
        destroyOnClose
      >
        <Form form={questionForm} layout="vertical" onFinish={submitQuestion}>
          <Form.Item label="Question" name="questionText" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Space>
            <Form.Item label="Weight" name="weight" rules={[{ required: true }]}>
              <InputNumber min={1} max={10} />
            </Form.Item>
            <Form.Item label="Sort order" name="sortOrder">
              <InputNumber min={0} />
            </Form.Item>
          </Space>
          <Form.Item label="Required" name="required" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
