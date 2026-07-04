// HCM_16 M417 — Mentoring: mentors + relationships. Mentees request; mentor/HR approve.
// Tabs: Mentors (browse+request), My mentoring (mentor/mentee view), HR all relationships.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { api } from '../../api/client'

const { Title } = Typography

interface MentorProfile {
  id: string
  employeeId: string
  name: string
  areas?: string | null
  maxMentees: number
  active: boolean
}

interface Relationship {
  id: string
  mentorEmployeeId: string
  mentorName: string
  menteeEmployeeId: string
  menteeName: string
  startDate: string
  endDate?: string | null
  status: string
  meetingNotes?: string | null
}

const STATUS_COLOR = {
  REQUESTED: 'orange',
  ACTIVE: 'green',
  COMPLETED: 'blue',
  CANCELLED: 'default',
}

export function MentoringPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [mentors, setMentors] = useState<MentorProfile[]>([])
  const [relationships, setRelationships] = useState<Relationship[]>([])
  const [registerOpen, setRegisterOpen] = useState(false)
  const [registerForm] = Form.useForm()
  const [registering, setRegistering] = useState(false)
  const [notesOpen, setNotesOpen] = useState(false)
  const [selectedRel, setSelectedRel] = useState<Relationship | null>(null)
  const [notesForm] = Form.useForm()
  const [savingNotes, setSavingNotes] = useState(false)

  const loadMentors = () => {
    api
      .get<MentorProfile[]>('/talent/mentoring/mentors')
      .then((r) => setMentors(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load mentors'))
  }

  const loadRelationships = () => {
    setLoading(true)
    api
      .get<Relationship[]>('/talent/mentoring/relationships')
      .then((r) => setRelationships(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load relationships'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadMentors()
    loadRelationships()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const requestMentor = async (mentorId: string) => {
    try {
      await api.post(`/talent/mentoring/relationships/request/${mentorId}`)
      message.success('Request sent')
      loadRelationships()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Request failed',
      )
    }
  }

  const submitRegister = async () => {
    const v = await registerForm.validateFields()
    setRegistering(true)
    try {
      await api.post('/talent/mentoring/mentors', v)
      message.success('Registered as mentor')
      setRegisterOpen(false)
      registerForm.resetFields()
      loadMentors()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Registration failed',
      )
    } finally {
      setRegistering(false)
    }
  }

  const approve = async (id: string) => {
    try {
      await api.put(`/talent/mentoring/relationships/${id}/approve`)
      message.success('Approved')
      loadRelationships()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Approve failed',
      )
    }
  }

  const complete = async (id: string) => {
    try {
      await api.put(`/talent/mentoring/relationships/${id}/complete`)
      message.success('Completed')
      loadRelationships()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Complete failed',
      )
    }
  }

  const cancel = async (id: string) => {
    try {
      await api.put(`/talent/mentoring/relationships/${id}/cancel`)
      message.success('Cancelled')
      loadRelationships()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const openNotes = (rel: Relationship) => {
    setSelectedRel(rel)
    notesForm.setFieldsValue({ notes: rel.meetingNotes })
    setNotesOpen(true)
  }

  const submitNotes = async () => {
    if (!selectedRel) return
    const v = await notesForm.validateFields()
    setSavingNotes(true)
    try {
      await api.put(`/talent/mentoring/relationships/${selectedRel.id}/notes`, v)
      message.success('Notes saved')
      setNotesOpen(false)
      loadRelationships()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSavingNotes(false)
    }
  }

  const mentorCols: ColumnsType<MentorProfile> = [
    { title: 'Name', dataIndex: 'name' },
    { title: 'Areas', dataIndex: 'areas', render: (v) => v ?? '—' },
    { title: 'Max Mentees', dataIndex: 'maxMentees', width: 120 },
    {
      title: '',
      width: 100,
      render: (_, r) => (
        <Button size="small" onClick={() => requestMentor(r.id)}>
          Request
        </Button>
      ),
    },
  ]

  const relCols: ColumnsType<Relationship> = [
    { title: 'Mentor', dataIndex: 'mentorName' },
    { title: 'Mentee', dataIndex: 'menteeName' },
    {
      title: 'Start',
      dataIndex: 'startDate',
      width: 100,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: 'End',
      dataIndex: 'endDate',
      width: 100,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={STATUS_COLOR[s as keyof typeof STATUS_COLOR]}>{s}</Tag>,
    },
    {
      title: 'Notes',
      dataIndex: 'meetingNotes',
      render: (v: string) => (v ? v.slice(0, 50) + (v.length > 50 ? '…' : '') : '—'),
    },
    {
      title: '',
      width: 200,
      render: (_, r) => (
        <Space size="small">
          {r.status === 'REQUESTED' && <Button size="small" onClick={() => approve(r.id)}>Approve</Button>}
          {r.status === 'ACTIVE' && <Button size="small" onClick={() => complete(r.id)}>Complete</Button>}
          {r.status !== 'CANCELLED' && r.status !== 'COMPLETED' && (
            <Button size="small" danger onClick={() => cancel(r.id)}>Cancel</Button>
          )}
          <Button size="small" type="link" onClick={() => openNotes(r)}>Notes</Button>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3}>Mentoring</Title>

      <Tabs
        items={[
          {
            key: 'mentors',
            label: 'Mentors',
            children: (
              <Card
                extra={
                  <Button type="primary" onClick={() => setRegisterOpen(true)}>
                    Register as Mentor
                  </Button>
                }
              >
                <Table
                  rowKey="id"
                  columns={mentorCols}
                  dataSource={mentors}
                  pagination={false}
                  size="small"
                />
              </Card>
            ),
          },
          {
            key: 'my-mentoring',
            label: 'My Mentoring',
            children: loading ? (
              <Spin />
            ) : (
              <Card>
                <Table
                  rowKey="id"
                  columns={relCols}
                  dataSource={relationships}
                  pagination={false}
                  size="small"
                />
              </Card>
            ),
          },
        ]}
      />

      <Modal
        open={registerOpen}
        title="Register as Mentor"
        onCancel={() => setRegisterOpen(false)}
        onOk={submitRegister}
        confirmLoading={registering}
        okText="Register"
      >
        <Form form={registerForm} layout="vertical">
          <Form.Item name="employeeId" label="Employee ID" rules={[{ required: true }]}>
            <Input placeholder="UUID (your employee ID)" />
          </Form.Item>
          <Form.Item name="areas" label="Areas of expertise">
            <Input placeholder="Leadership, Finance, Technology" />
          </Form.Item>
          <Form.Item name="maxMentees" label="Max mentees" initialValue={3}>
            <InputNumber min={1} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={notesOpen}
        title="Meeting Notes"
        onCancel={() => setNotesOpen(false)}
        onOk={submitNotes}
        confirmLoading={savingNotes}
        okText="Save"
      >
        <Form form={notesForm} layout="vertical">
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={6} placeholder="Meeting notes, progress, goals discussed…" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
