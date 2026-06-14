import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd'
import {
  recruitmentApi,
  type ScreeningQuestion,
  type ScreeningQuestionRequest,
  type ScreeningQuestionType,
} from '../api/recruitment'

/**
 * M289 — Recruitment PRD §15: knockout / pre-screening questions for one
 * job posting. HR defines an ordered set of questions with a pass/fail
 * rule; a candidate who fails a `knockout` question is auto-rejected at
 * apply time.
 */

const TYPE_OPTIONS: { value: ScreeningQuestionType; label: string }[] = [
  { value: 'BOOLEAN', label: 'Yes / No' },
  { value: 'SINGLE_CHOICE', label: 'Single choice' },
  { value: 'NUMERIC', label: 'Number' },
]

/** Human summary of the pass rule for the table. */
function ruleText(q: ScreeningQuestion): string {
  switch (q.questionType) {
    case 'BOOLEAN':
      return q.expectedBoolean == null
        ? 'any answer'
        : `must be ${q.expectedBoolean ? 'Yes' : 'No'}`
    case 'SINGLE_CHOICE':
      return q.acceptableOptions && q.acceptableOptions.trim()
        ? `one of: ${q.acceptableOptions.split(/\r?\n/).filter(Boolean).join(', ')}`
        : 'any choice'
    case 'NUMERIC': {
      if (q.minValue != null && q.maxValue != null) return `${q.minValue}–${q.maxValue}`
      if (q.minValue != null) return `≥ ${q.minValue}`
      if (q.maxValue != null) return `≤ ${q.maxValue}`
      return 'any number'
    }
    default:
      return '—'
  }
}

interface FormValues {
  ordinal: number
  questionText: string
  questionType: ScreeningQuestionType
  options?: string
  knockout: boolean
  required: boolean
  expectedBoolean?: 'TRUE' | 'FALSE' | 'ANY'
  acceptableOptions?: string
  minValue?: number | null
  maxValue?: number | null
  active: boolean
}

export function ScreeningQuestionsPanel({
  postingId,
  canEdit,
}: {
  postingId: string
  canEdit: boolean
}) {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<ScreeningQuestion[]>([])
  const [editing, setEditing] = useState<ScreeningQuestion | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const watchType = Form.useWatch('questionType', form)

  const load = useCallback(() => {
    recruitmentApi
      .screeningQuestions(postingId)
      .then(setRows)
      .catch(() => setRows([]))
  }, [postingId])

  useEffect(load, [load])

  const openNew = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      ordinal: rows.length + 1,
      questionType: 'BOOLEAN',
      knockout: true,
      required: true,
      expectedBoolean: 'TRUE',
      active: true,
    })
    setOpen(true)
  }

  const openEdit = (q: ScreeningQuestion) => {
    setEditing(q)
    form.setFieldsValue({
      ordinal: q.ordinal,
      questionText: q.questionText,
      questionType: q.questionType,
      options: q.options ?? undefined,
      knockout: q.knockout,
      required: q.required,
      expectedBoolean: q.expectedBoolean == null ? 'ANY' : q.expectedBoolean ? 'TRUE' : 'FALSE',
      acceptableOptions: q.acceptableOptions ?? undefined,
      minValue: q.minValue ?? null,
      maxValue: q.maxValue ?? null,
      active: q.active,
    })
    setOpen(true)
  }

  const save = async () => {
    const v = await form.validateFields()
    const payload: ScreeningQuestionRequest = {
      ordinal: v.ordinal,
      questionText: v.questionText,
      questionType: v.questionType,
      options: v.questionType === 'SINGLE_CHOICE' ? v.options : null,
      knockout: v.knockout,
      required: v.required,
      expectedBoolean:
        v.questionType === 'BOOLEAN'
          ? v.expectedBoolean === 'ANY'
            ? null
            : v.expectedBoolean === 'TRUE'
          : null,
      acceptableOptions: v.questionType === 'SINGLE_CHOICE' ? v.acceptableOptions : null,
      minValue: v.questionType === 'NUMERIC' ? (v.minValue ?? null) : null,
      maxValue: v.questionType === 'NUMERIC' ? (v.maxValue ?? null) : null,
      active: v.active,
    }
    try {
      if (editing) {
        await recruitmentApi.updateScreeningQuestion(editing.id, payload)
        message.success('Question updated')
      } else {
        await recruitmentApi.createScreeningQuestion(postingId, payload)
        message.success('Question added')
      }
      setOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    }
  }

  const remove = async (q: ScreeningQuestion) => {
    try {
      await recruitmentApi.deleteScreeningQuestion(q.id)
      message.success('Question removed')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Table<ScreeningQuestion>
        rowKey="id"
        size="small"
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: 'No screening questions — candidates apply without a knockout filter' }}
        columns={[
          { title: '#', dataIndex: 'ordinal', width: 50 },
          { title: 'Question', dataIndex: 'questionText', ellipsis: true },
          {
            title: 'Type',
            dataIndex: 'questionType',
            width: 110,
            render: (t: ScreeningQuestionType) =>
              TYPE_OPTIONS.find((o) => o.value === t)?.label ?? t,
          },
          { title: 'Pass rule', width: 170, render: (_: unknown, q) => ruleText(q) },
          {
            title: 'Knockout',
            dataIndex: 'knockout',
            width: 90,
            render: (b: boolean) =>
              b ? <Tag color="volcano">Knockout</Tag> : <Tag>Screening</Tag>,
          },
          {
            title: 'Active',
            dataIndex: 'active',
            width: 70,
            render: (b: boolean) => (b ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
          },
          ...(canEdit
            ? [
                {
                  title: 'Actions',
                  width: 130,
                  render: (_: unknown, q: ScreeningQuestion) => (
                    <Space size="small">
                      <Button size="small" onClick={() => openEdit(q)}>
                        Edit
                      </Button>
                      <Popconfirm title="Remove this question?" onConfirm={() => remove(q)}>
                        <Button size="small" danger>
                          Delete
                        </Button>
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />
      {canEdit && (
        <Button type="dashed" onClick={openNew}>
          + Add question
        </Button>
      )}

      <Modal
        title={editing ? 'Edit screening question' : 'New screening question'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="Save"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="ordinal" label="Order" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 90 }} />
            </Form.Item>
            <Form.Item name="questionType" label="Type" rules={[{ required: true }]}>
              <Select options={TYPE_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="questionText"
            label="Question"
            rules={[{ required: true, message: 'Question text is required' }]}
          >
            <Input.TextArea rows={2} placeholder="e.g. Are you legally authorised to work in Azerbaijan?" />
          </Form.Item>

          {watchType === 'BOOLEAN' && (
            <Form.Item name="expectedBoolean" label="Passing answer">
              <Select
                options={[
                  { value: 'TRUE', label: 'Yes' },
                  { value: 'FALSE', label: 'No' },
                  { value: 'ANY', label: 'Any (informational)' },
                ]}
              />
            </Form.Item>
          )}
          {watchType === 'SINGLE_CHOICE' && (
            <>
              <Form.Item
                name="options"
                label="Choices (one per line)"
                rules={[{ required: true, message: 'Add at least one choice' }]}
              >
                <Input.TextArea rows={3} placeholder={'Yes\nNo\nNot sure'} />
              </Form.Item>
              <Form.Item
                name="acceptableOptions"
                label="Passing choices (one per line — blank = any)"
              >
                <Input.TextArea rows={2} placeholder={'Yes'} />
              </Form.Item>
            </>
          )}
          {watchType === 'NUMERIC' && (
            <Space size="middle" style={{ display: 'flex' }}>
              <Form.Item name="minValue" label="Minimum (inclusive)">
                <InputNumber style={{ width: 140 }} />
              </Form.Item>
              <Form.Item name="maxValue" label="Maximum (inclusive)">
                <InputNumber style={{ width: 140 }} />
              </Form.Item>
            </Space>
          )}

          <Space size="large">
            <Form.Item name="knockout" label="Knockout" valuePropName="checked" tooltip="Failing auto-rejects the applicant">
              <Switch />
            </Form.Item>
            <Form.Item name="required" label="Required" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  )
}
