// HCM_12 M400 — continuous feedback + check-ins (PRD §22). Praise/improvement
// notes with EMPLOYEE_VISIBLE / MANAGER_PRIVATE visibility; 1:1/monthly/quarterly
// check-ins with action items, follow-up and employee acknowledgement.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { employeesApi, type Employee } from '../../api/employees'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

interface FeedbackNote {
  id: string
  employeeId: string
  authorEmployeeId?: string | null
  kind: 'PRAISE' | 'IMPROVEMENT' | 'NOTE' | 'REQUEST'
  visibility: 'EMPLOYEE_VISIBLE' | 'MANAGER_PRIVATE'
  message: string
  tags?: string | null
  createdBy?: string | null
  createdAt: string
}

interface CheckIn {
  id: string
  employeeId: string
  managerId?: string | null
  meetingDate: string
  kind: 'ONE_TO_ONE' | 'MONTHLY' | 'QUARTERLY'
  discussionNotes?: string | null
  actionItems?: string | null
  followUpDate?: string | null
  acknowledgedAt?: string | null
  createdBy?: string | null
}

const KIND_COLOR: Record<FeedbackNote['kind'], string> = {
  PRAISE: 'green',
  IMPROVEMENT: 'orange',
  NOTE: 'default',
  REQUEST: 'blue',
}

export function CheckInsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canManage = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [notes, setNotes] = useState<FeedbackNote[]>([])
  const [checkIns, setCheckIns] = useState<CheckIn[]>([])
  const [loading, setLoading] = useState(false)

  const [noteOpen, setNoteOpen] = useState(false)
  const [noteForm] = Form.useForm()
  const [ciOpen, setCiOpen] = useState(false)
  const [ciForm] = Form.useForm()

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((e) => {
      setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
    })
  }, [])

  const load = () => {
    if (!employeeId) return
    setLoading(true)
    Promise.all([
      api.get<FeedbackNote[]>('/performance/continuous/feedback', { params: { employeeId } }),
      api.get<CheckIn[]>('/performance/continuous/check-ins', { params: { employeeId } }),
    ])
      .then(([f, c]) => {
        setNotes(f.data)
        setCheckIns(c.data)
      })
      .catch(() => message.error('Failed to load'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [employeeId]) // eslint-disable-line react-hooks/exhaustive-deps

  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  const saveNote = async () => {
    if (!employeeId) return
    const v = await noteForm.validateFields()
    try {
      await api.post('/performance/continuous/feedback', { ...v, employeeId })
      message.success('Feedback recorded')
      setNoteOpen(false)
      noteForm.resetFields()
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    }
  }

  const saveCheckIn = async () => {
    if (!employeeId) return
    const v = await ciForm.validateFields()
    try {
      await api.post('/performance/continuous/check-ins', {
        ...v,
        employeeId,
        meetingDate: v.meetingDate.format('YYYY-MM-DD'),
        followUpDate: v.followUpDate ? v.followUpDate.format('YYYY-MM-DD') : null,
      })
      message.success('Check-in recorded')
      setCiOpen(false)
      ciForm.resetFields()
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    }
  }

  const ciColumns: ColumnsType<CheckIn> = [
    { title: 'Date', dataIndex: 'meetingDate', width: 110 },
    {
      title: 'Kind',
      dataIndex: 'kind',
      width: 120,
      render: (v: string) => <Tag color="geekblue">{v.replace(/_/g, ' ')}</Tag>,
    },
    { title: 'Discussion', dataIndex: 'discussionNotes', ellipsis: true, render: (v) => v ?? '—' },
    { title: 'Action items', dataIndex: 'actionItems', ellipsis: true, render: (v) => v ?? '—' },
    { title: 'Follow-up', dataIndex: 'followUpDate', width: 110, render: (v) => v ?? '—' },
    {
      title: 'Ack',
      key: 'ack',
      width: 140,
      render: (_, c) =>
        c.acknowledgedAt ? (
          <Tag color="green">acknowledged</Tag>
        ) : (
          <Button
            size="small"
            onClick={() =>
              api
                .post(`/performance/continuous/check-ins/${c.id}/acknowledge`)
                .then(() => {
                  message.success('Acknowledged')
                  load()
                })
                .catch(() => message.error('Failed'))
            }
          >
            Acknowledge
          </Button>
        ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Check-ins &amp; continuous feedback
          </Title>
          <Text type="secondary">
            Ongoing feedback (§22.1 — private manager notes stay manager/HR-only) and 1:1 /
            monthly / quarterly check-ins with acknowledgement (§22.2).
          </Text>
        </Col>
      </Row>

      <Card size="small">
        <Space style={{ marginBottom: 12 }} wrap>
          <Select
            showSearch
            optionFilterProp="label"
            style={{ minWidth: 280 }}
            placeholder="Pick an employee"
            value={employeeId}
            onChange={setEmployeeId}
            options={employees.map((e) => ({
              value: e.id,
              label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
            }))}
          />
          <Button
            type="primary"
            disabled={!employeeId}
            onClick={() => {
              noteForm.resetFields()
              noteForm.setFieldsValue({ kind: 'PRAISE', visibility: 'EMPLOYEE_VISIBLE' })
              setNoteOpen(true)
            }}
          >
            Give feedback
          </Button>
          {canManage && (
            <Button
              disabled={!employeeId}
              onClick={() => {
                ciForm.resetFields()
                ciForm.setFieldsValue({ kind: 'ONE_TO_ONE' })
                setCiOpen(true)
              }}
            >
              Record check-in
            </Button>
          )}
        </Space>

        {!employeeId ? (
          <Text type="secondary">Pick an employee to see their feedback and check-ins.</Text>
        ) : loading ? (
          <Spin />
        ) : (
          <Tabs
            defaultActiveKey="checkins"
            items={[
              {
                key: 'checkins',
                label: `Check-ins (${checkIns.length})`,
                children: (
                  <Table
                    rowKey="id"
                    size="small"
                    columns={ciColumns}
                    dataSource={checkIns}
                    pagination={{ pageSize: 10, hideOnSinglePage: true }}
                  />
                ),
              },
              {
                key: 'feedback',
                label: `Feedback (${notes.length})`,
                children:
                  notes.length === 0 ? (
                    <Text type="secondary">No feedback yet.</Text>
                  ) : (
                    <Timeline
                      items={notes.map((n) => ({
                        color: n.kind === 'PRAISE' ? 'green' : n.kind === 'IMPROVEMENT' ? 'orange' : 'blue',
                        children: (
                          <Space direction="vertical" size={0}>
                            <Space size={4}>
                              <Tag color={KIND_COLOR[n.kind]}>{n.kind}</Tag>
                              {n.visibility === 'MANAGER_PRIVATE' && (
                                <Tag color="red">manager-private</Tag>
                              )}
                              {n.tags &&
                                n.tags.split(',').map((t) => <Tag key={t}>{t.trim()}</Tag>)}
                            </Space>
                            <Text>{n.message}</Text>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {n.authorEmployeeId
                                ? (() => {
                                    const a = empMap.get(n.authorEmployeeId!)
                                    return a ? `${a.firstName} ${a.lastName}` : n.createdBy
                                  })()
                                : n.createdBy}{' '}
                              · {new Date(n.createdAt).toLocaleString()}
                            </Text>
                          </Space>
                        ),
                      }))}
                    />
                  ),
              },
            ]}
          />
        )}
      </Card>

      {/* Feedback modal */}
      <Modal
        title="Give feedback"
        open={noteOpen}
        onCancel={() => setNoteOpen(false)}
        onOk={saveNote}
        okText="Record"
        destroyOnClose
      >
        <Form form={noteForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="kind" label="Kind" rules={[{ required: true }]}>
                <Select
                  options={['PRAISE', 'IMPROVEMENT', 'NOTE', 'REQUEST'].map((k) => ({
                    value: k,
                    label: k,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="visibility" label="Visibility" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'EMPLOYEE_VISIBLE', label: 'Employee-visible' },
                    { value: 'MANAGER_PRIVATE', label: 'Private manager note' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="message" label="Message" rules={[{ required: true, max: 2000 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="tags" label="Tags (comma-separated)">
            <Input placeholder="teamwork, delivery" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Check-in modal */}
      <Modal
        title="Record check-in"
        open={ciOpen}
        onCancel={() => setCiOpen(false)}
        onOk={saveCheckIn}
        okText="Record"
        destroyOnClose
      >
        <Form form={ciForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="meetingDate" label="Meeting date" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="kind" label="Kind" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'ONE_TO_ONE', label: 'One-to-one' },
                    { value: 'MONTHLY', label: 'Monthly' },
                    { value: 'QUARTERLY', label: 'Quarterly' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="discussionNotes" label="Discussion notes">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="actionItems" label="Action items">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="followUpDate" label="Follow-up date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
