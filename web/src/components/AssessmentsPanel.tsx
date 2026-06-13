import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import {
  recruitmentApi,
  type Assessment,
  type AssessmentRequest,
  type AssessmentResult,
  type AssessmentStatus,
  type AssessmentType,
} from '../api/recruitment'

/**
 * M287 — Recruitment PRD §22: assessments for one application.
 * Assign a test, record score; PASS/FAIL auto-derives from score vs
 * passing score server-side. A blocks-hire FAIL stops the hire.
 */

const TYPE_OPTIONS: { value: AssessmentType; label: string }[] = [
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'LANGUAGE', label: 'Language' },
  { value: 'COGNITIVE', label: 'Cognitive' },
  { value: 'PERSONALITY', label: 'Personality' },
  { value: 'JOB_SIMULATION', label: 'Job simulation' },
  { value: 'PRACTICAL', label: 'Practical' },
  { value: 'CASE_STUDY', label: 'Case study' },
  { value: 'TYPING', label: 'Typing' },
  { value: 'OTHER', label: 'Other' },
]

const STATUS_COLOR: Record<AssessmentStatus, string> = {
  ASSIGNED: 'gold',
  IN_PROGRESS: 'blue',
  COMPLETED: 'cyan',
  EXPIRED: 'red',
  CANCELLED: 'default',
}

const RESULT_COLOR: Record<AssessmentResult, string> = { PASS: 'green', FAIL: 'red' }

interface AddValues {
  assessmentType: AssessmentType
  name: string
  provider?: string
  maxScore?: number
  passingScore?: number
  blocksHire?: boolean
}

export function AssessmentsPanel({
  applicationId,
  canEdit,
}: {
  applicationId: string
  canEdit: boolean
}) {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<Assessment[]>([])
  const [form] = Form.useForm<AddValues>()

  const load = useCallback(() => {
    recruitmentApi
      .assessmentsForApplication(applicationId)
      .then(setRows)
      .catch(() => setRows([]))
  }, [applicationId])

  useEffect(load, [load])

  const add = async () => {
    const v = await form.validateFields()
    try {
      await recruitmentApi.createAssessment(applicationId, v as AssessmentRequest)
      form.resetFields()
      message.success('Assessment assigned')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed',
      )
    }
  }

  const record = async (a: Assessment) => {
    const raw = window.prompt(
      `Enter score for "${a.name}"${a.maxScore != null ? ` (out of ${a.maxScore})` : ''}:`,
    )
    if (raw == null) return
    const score = Number(raw)
    if (Number.isNaN(score)) {
      message.error('Score must be a number')
      return
    }
    try {
      // Server auto-derives PASS/FAIL from score vs passing score.
      await recruitmentApi.updateAssessment(a.id, { status: 'COMPLETED', score })
      message.success('Score recorded')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed',
      )
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Table<Assessment>
        rowKey="id"
        size="small"
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: 'No assessments yet' }}
        columns={[
          { title: '#', dataIndex: 'assessmentNo', width: 100 },
          { title: 'Name', dataIndex: 'name', ellipsis: true },
          {
            title: 'Type',
            dataIndex: 'assessmentType',
            width: 120,
            render: (t: AssessmentType) => t.replace(/_/g, ' '),
          },
          {
            title: 'Score',
            width: 110,
            render: (_: unknown, a: Assessment) =>
              a.score != null
                ? `${a.score}${a.maxScore != null ? ` / ${a.maxScore}` : ''}`
                : a.passingScore != null
                  ? `pass ≥ ${a.passingScore}`
                  : '—',
          },
          {
            title: 'Status',
            dataIndex: 'status',
            width: 110,
            render: (s: AssessmentStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
          },
          {
            title: 'Result',
            dataIndex: 'result',
            width: 90,
            render: (r: AssessmentResult | null) =>
              r ? <Tag color={RESULT_COLOR[r]}>{r}</Tag> : '—',
          },
          {
            title: 'Blocks hire',
            dataIndex: 'blocksHire',
            width: 90,
            render: (b: boolean) => (b ? <Tag color="volcano">Yes</Tag> : 'No'),
          },
          ...(canEdit
            ? [
                {
                  title: 'Actions',
                  width: 140,
                  render: (_: unknown, a: Assessment) =>
                    ['COMPLETED', 'EXPIRED', 'CANCELLED'].includes(a.status) ? (
                      <span style={{ color: '#bbb' }}>—</span>
                    ) : (
                      <Button size="small" onClick={() => record(a)}>
                        Record score
                      </Button>
                    ),
                },
              ]
            : []),
        ]}
      />
      {canEdit && (
        <Form form={form} layout="inline" onFinish={add}>
          <Form.Item name="assessmentType" rules={[{ required: true, message: 'Type' }]}>
            <Select placeholder="Type" options={TYPE_OPTIONS} style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="name" rules={[{ required: true, message: 'Name' }]}>
            <Input placeholder="Assessment name" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="maxScore">
            <InputNumber placeholder="Max" min={0} style={{ width: 80 }} />
          </Form.Item>
          <Form.Item name="passingScore">
            <InputNumber placeholder="Pass ≥" min={0} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item>
            <Button htmlType="submit">Assign</Button>
          </Form.Item>
        </Form>
      )}
    </Space>
  )
}
