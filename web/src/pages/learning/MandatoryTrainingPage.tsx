// HCM_14 M406 — mandatory / compliance training rules (PRD 14 §9/§16).
// A rule scopes a course to an audience with a recurrence; the daily sweep
// auto-enrols and renews. The compliance table shows in-scope vs compliant.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { learningApi, type Course } from '../../api/learning'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

interface Rule {
  id: string
  courseId: string
  name: string
  departmentName?: string | null
  positionId?: string | null
  workLocationId?: string | null
  recurrenceMonths?: number | null
  dueDays: number
  reminderDaysBefore: number
  active: boolean
}

interface Compliance {
  ruleId: string
  ruleName: string
  courseId: string
  inScope: number
  compliant: number
  pending: number
  overdue: number
}

export function MandatoryTrainingPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [rules, setRules] = useState<Rule[]>([])
  const [compliance, setCompliance] = useState<Compliance[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [sweeping, setSweeping] = useState(false)

  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<Rule | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([
      api.get<Rule[]>('/learning/mandatory-training/rules'),
      api.get<Compliance[]>('/learning/mandatory-training/compliance'),
      learningApi.courses({ size: 500 }),
    ])
      .then(([r, c, cs]) => {
        setRules(r.data)
        setCompliance(c.data)
        setCourses(cs.content)
      })
      .catch(() => message.error('Failed to load mandatory training'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])
  const compMap = useMemo(() => new Map(compliance.map((c) => [c.ruleId, c])), [compliance])

  const save = async () => {
    const v = await form.validateFields()
    try {
      if (editing) await api.put(`/learning/mandatory-training/rules/${editing.id}`, v)
      else await api.post('/learning/mandatory-training/rules', v)
      message.success('Rule saved')
      setEditOpen(false)
      load()
    } catch (e: unknown) {
      const x = e as { response?: { data?: { message?: string } } }
      message.error(x.response?.data?.message ?? 'Save failed')
    }
  }

  const sweep = async () => {
    setSweeping(true)
    try {
      const r = await api.post<{ rulesProcessed: number; newlyEnrolled: number; renewed: number }>(
        '/learning/mandatory-training/sweep',
      )
      message.success(
        `Sweep done — ${r.data.newlyEnrolled} enrolled, ${r.data.renewed} renewed over ${r.data.rulesProcessed} rules`,
      )
      load()
    } catch {
      message.error('Sweep failed')
    } finally {
      setSweeping(false)
    }
  }

  const columns: ColumnsType<Rule> = [
    { title: 'Rule', dataIndex: 'name' },
    {
      title: 'Course',
      key: 'course',
      render: (_, r) => {
        const c = courseMap.get(r.courseId)
        return c ? `${c.code} — ${c.title}` : r.courseId
      },
    },
    {
      title: 'Audience',
      key: 'aud',
      width: 180,
      render: (_, r) =>
        r.departmentName || r.positionId || r.workLocationId ? (
          <Space size={4} wrap>
            {r.departmentName && <Tag>{r.departmentName}</Tag>}
            {r.positionId && <Tag>position</Tag>}
            {r.workLocationId && <Tag>location</Tag>}
          </Space>
        ) : (
          <Tag color="blue">everyone</Tag>
        ),
    },
    {
      title: 'Recurrence',
      dataIndex: 'recurrenceMonths',
      width: 110,
      render: (v) => (v ? `${v} mo` : 'one-time'),
    },
    { title: 'Due days', dataIndex: 'dueDays', width: 90, align: 'center' },
    {
      title: 'Compliance',
      key: 'comp',
      width: 240,
      render: (_, r) => {
        const c = compMap.get(r.id)
        if (!c) return '—'
        return (
          <Space size={4}>
            <Tag color="green">{c.compliant} ok</Tag>
            <Tag color="gold">{c.pending} pending</Tag>
            <Tag color={c.overdue > 0 ? 'red' : 'default'}>{c.overdue} overdue</Tag>
            <Text type="secondary">/ {c.inScope}</Text>
          </Space>
        )
      },
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
    ...(canWrite
      ? [
          {
            title: '',
            key: 'a',
            width: 70,
            render: (_: unknown, r: Rule) => (
              <Button
                size="small"
                onClick={() => {
                  setEditing(r)
                  form.setFieldsValue(r)
                  setEditOpen(true)
                }}
              >
                Edit
              </Button>
            ),
          } as ColumnsType<Rule>[number],
        ]
      : []),
  ]

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Mandatory training
          </Title>
          <Text type="secondary">
            Compliance rules with recurrence — the daily sweep (05:30) auto-enrols new joiners
            and renews expired completions.
          </Text>
        </Col>
        <Col>
          <Space>
            {canWrite && (
              <>
                <Button loading={sweeping} onClick={sweep}>
                  Run sweep now
                </Button>
                <Button
                  type="primary"
                  onClick={() => {
                    setEditing(null)
                    form.resetFields()
                    form.setFieldsValue({ dueDays: 30, reminderDaysBefore: 7, active: true })
                    setEditOpen(true)
                  }}
                >
                  New rule
                </Button>
              </>
            )}
          </Space>
        </Col>
      </Row>

      <Card size="small">
        <Table rowKey="id" size="small" columns={columns} dataSource={rules} pagination={false} />
      </Card>

      <Modal
        title={editing ? 'Edit rule' : 'New mandatory-training rule'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={save}
        okText="Save"
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="name" label="Rule name" rules={[{ required: true }]}>
                <Input placeholder="Annual safety training" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="courseId" label="Course" rules={[{ required: true }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={courses.map((c) => ({ value: c.id, label: `${c.code} — ${c.title}` }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="departmentName"
            label="Department filter (empty = everyone)"
            extra="Position / work-location filters are available via the API; department covers the common case."
          >
            <Input placeholder="e.g. Engineering" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="recurrenceMonths" label="Recurrence (months, empty = one-time)">
                <InputNumber min={1} style={{ width: '100%' }} placeholder="12" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="dueDays" label="Due in (days)">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="reminderDaysBefore" label="Remind (days before)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )
}
