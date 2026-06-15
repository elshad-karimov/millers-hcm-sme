import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import dayjs from 'dayjs'
import {
  recruitmentApi,
  type CandidateEducation,
  type CandidateEducationRequest,
  type CandidateExperience,
  type CandidateExperienceRequest,
  type CandidateProfile,
  type CandidateSkillRow,
  type CandidateSkillRequest,
  type SkillProficiency,
} from '../api/recruitment'

/**
 * M291 — Recruitment PRD §11: structured candidate profile editor
 * (education, work experience, rated skills). Dropped into the candidate
 * edit page; each section is independent per-row CRUD.
 */

const PROFICIENCY: { value: SkillProficiency; label: string; color: string }[] = [
  { value: 'BEGINNER', label: 'Beginner', color: 'default' },
  { value: 'INTERMEDIATE', label: 'Intermediate', color: 'blue' },
  { value: 'ADVANCED', label: 'Advanced', color: 'geekblue' },
  { value: 'EXPERT', label: 'Expert', color: 'green' },
]

function errMsg(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback
}

export function CandidateProfilePanels({ candidateId }: { candidateId: string }) {
  const { message } = AntdApp.useApp()
  const [profile, setProfile] = useState<CandidateProfile>({
    education: [],
    experience: [],
    skills: [],
  })

  const load = useCallback(() => {
    recruitmentApi
      .candidateProfile(candidateId)
      .then(setProfile)
      .catch(() => setProfile({ education: [], experience: [], skills: [] }))
  }, [candidateId])

  useEffect(load, [load])

  // ── Education ────────────────────────────────────────────────────────
  const [eduOpen, setEduOpen] = useState(false)
  const [eduEditing, setEduEditing] = useState<CandidateEducation | null>(null)
  const [eduForm] = Form.useForm<CandidateEducationRequest>()
  const openEdu = (e?: CandidateEducation) => {
    setEduEditing(e ?? null)
    eduForm.resetFields()
    eduForm.setFieldsValue(e ?? { ordinal: profile.education.length + 1 })
    setEduOpen(true)
  }
  const saveEdu = async () => {
    const v = await eduForm.validateFields()
    try {
      if (eduEditing) await recruitmentApi.updateCandidateEducation(eduEditing.id, v)
      else await recruitmentApi.addCandidateEducation(candidateId, v)
      message.success('Saved')
      setEduOpen(false)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Save failed'))
    }
  }
  const delEdu = async (id: string) => {
    try {
      await recruitmentApi.deleteCandidateEducation(id)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Delete failed'))
    }
  }

  // ── Experience ───────────────────────────────────────────────────────
  const [expOpen, setExpOpen] = useState(false)
  const [expEditing, setExpEditing] = useState<CandidateExperience | null>(null)
  const [expForm] = Form.useForm()
  const watchCurrent = Form.useWatch('current', expForm)
  const openExp = (x?: CandidateExperience) => {
    setExpEditing(x ?? null)
    expForm.resetFields()
    expForm.setFieldsValue(
      x
        ? {
            ...x,
            startDate: x.startDate ? dayjs(x.startDate) : undefined,
            endDate: x.endDate ? dayjs(x.endDate) : undefined,
          }
        : { ordinal: profile.experience.length + 1, current: false },
    )
    setExpOpen(true)
  }
  const saveExp = async () => {
    const v = await expForm.validateFields()
    const payload: CandidateExperienceRequest = {
      ordinal: v.ordinal,
      company: v.company,
      title: v.title,
      location: v.location ?? null,
      startDate: v.startDate ? v.startDate.format('YYYY-MM-DD') : null,
      endDate: v.current || !v.endDate ? null : v.endDate.format('YYYY-MM-DD'),
      current: !!v.current,
      description: v.description ?? null,
    }
    try {
      if (expEditing) await recruitmentApi.updateCandidateExperience(expEditing.id, payload)
      else await recruitmentApi.addCandidateExperience(candidateId, payload)
      message.success('Saved')
      setExpOpen(false)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Save failed'))
    }
  }
  const delExp = async (id: string) => {
    try {
      await recruitmentApi.deleteCandidateExperience(id)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Delete failed'))
    }
  }

  // ── Skills ───────────────────────────────────────────────────────────
  const [skillOpen, setSkillOpen] = useState(false)
  const [skillEditing, setSkillEditing] = useState<CandidateSkillRow | null>(null)
  const [skillForm] = Form.useForm<CandidateSkillRequest>()
  const openSkill = (s?: CandidateSkillRow) => {
    setSkillEditing(s ?? null)
    skillForm.resetFields()
    skillForm.setFieldsValue(s ?? { ordinal: profile.skills.length + 1 })
    setSkillOpen(true)
  }
  const saveSkill = async () => {
    const v = await skillForm.validateFields()
    try {
      if (skillEditing) await recruitmentApi.updateCandidateSkill(skillEditing.id, v)
      else await recruitmentApi.addCandidateSkill(candidateId, v)
      message.success('Saved')
      setSkillOpen(false)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Save failed'))
    }
  }
  const delSkill = async (id: string) => {
    try {
      await recruitmentApi.deleteCandidateSkill(id)
      load()
    } catch (err) {
      message.error(errMsg(err, 'Delete failed'))
    }
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%', marginTop: 24 }}>
      <Card
        size="small"
        title="Education"
        extra={
          <Button size="small" onClick={() => openEdu()}>
            + Add
          </Button>
        }
      >
        <Table<CandidateEducation>
          rowKey="id"
          size="small"
          dataSource={profile.education}
          pagination={false}
          locale={{ emptyText: 'No education entries' }}
          columns={[
            { title: 'Institution', dataIndex: 'institution', ellipsis: true },
            { title: 'Degree', dataIndex: 'degree', render: (v?: string) => v ?? '—' },
            { title: 'Field', dataIndex: 'fieldOfStudy', render: (v?: string) => v ?? '—' },
            {
              title: 'Years',
              width: 110,
              render: (_: unknown, e) =>
                e.startYear || e.endYear ? `${e.startYear ?? '?'}–${e.endYear ?? '?'}` : '—',
            },
            { title: 'Grade', dataIndex: 'grade', width: 90, render: (v?: string) => v ?? '—' },
            {
              title: '',
              width: 120,
              render: (_: unknown, e) => (
                <Space size="small">
                  <Button size="small" onClick={() => openEdu(e)}>
                    Edit
                  </Button>
                  <Popconfirm title="Remove?" onConfirm={() => delEdu(e.id)}>
                    <Button size="small" danger>
                      Delete
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Card
        size="small"
        title="Work experience"
        extra={
          <Button size="small" onClick={() => openExp()}>
            + Add
          </Button>
        }
      >
        <Table<CandidateExperience>
          rowKey="id"
          size="small"
          dataSource={profile.experience}
          pagination={false}
          locale={{ emptyText: 'No experience entries' }}
          columns={[
            { title: 'Company', dataIndex: 'company', ellipsis: true },
            { title: 'Title', dataIndex: 'title', ellipsis: true },
            {
              title: 'Period',
              width: 180,
              render: (_: unknown, x) =>
                `${x.startDate ?? '?'} – ${x.current ? 'present' : (x.endDate ?? '?')}`,
            },
            {
              title: '',
              dataIndex: 'current',
              width: 80,
              render: (c: boolean) => (c ? <Tag color="green">Current</Tag> : null),
            },
            {
              title: '',
              width: 120,
              render: (_: unknown, x) => (
                <Space size="small">
                  <Button size="small" onClick={() => openExp(x)}>
                    Edit
                  </Button>
                  <Popconfirm title="Remove?" onConfirm={() => delExp(x.id)}>
                    <Button size="small" danger>
                      Delete
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Card
        size="small"
        title="Skills"
        extra={
          <Button size="small" onClick={() => openSkill()}>
            + Add
          </Button>
        }
      >
        <Table<CandidateSkillRow>
          rowKey="id"
          size="small"
          dataSource={profile.skills}
          pagination={false}
          locale={{ emptyText: 'No rated skills' }}
          columns={[
            { title: 'Skill', dataIndex: 'name' },
            {
              title: 'Proficiency',
              dataIndex: 'proficiency',
              width: 140,
              render: (p?: SkillProficiency | null) => {
                const opt = PROFICIENCY.find((o) => o.value === p)
                return opt ? <Tag color={opt.color}>{opt.label}</Tag> : '—'
              },
            },
            {
              title: 'Years',
              dataIndex: 'yearsExperience',
              width: 90,
              render: (v?: number | null) => (v != null ? v : '—'),
            },
            {
              title: '',
              width: 120,
              render: (_: unknown, s) => (
                <Space size="small">
                  <Button size="small" onClick={() => openSkill(s)}>
                    Edit
                  </Button>
                  <Popconfirm title="Remove?" onConfirm={() => delSkill(s.id)}>
                    <Button size="small" danger>
                      Delete
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      {/* Education modal */}
      <Modal
        title={eduEditing ? 'Edit education' : 'Add education'}
        open={eduOpen}
        onOk={saveEdu}
        onCancel={() => setEduOpen(false)}
        okText="Save"
        destroyOnClose
      >
        <Form form={eduForm} layout="vertical">
          <Form.Item name="ordinal" label="Order" initialValue={1} rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="institution" label="Institution" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Space>
            <Form.Item name="degree" label="Degree">
              <Input placeholder="BSc, MSc, …" />
            </Form.Item>
            <Form.Item name="fieldOfStudy" label="Field of study">
              <Input />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="startYear" label="Start year">
              <InputNumber min={1950} max={2100} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="endYear" label="End year">
              <InputNumber min={1950} max={2100} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="grade" label="Grade">
              <Input style={{ width: 120 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* Experience modal */}
      <Modal
        title={expEditing ? 'Edit experience' : 'Add experience'}
        open={expOpen}
        onOk={saveExp}
        onCancel={() => setExpOpen(false)}
        okText="Save"
        destroyOnClose
      >
        <Form form={expForm} layout="vertical">
          <Form.Item name="ordinal" label="Order" initialValue={1} rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: 90 }} />
          </Form.Item>
          <Space>
            <Form.Item name="company" label="Company" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="title" label="Title" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="location" label="Location">
            <Input />
          </Form.Item>
          <Space>
            <Form.Item name="startDate" label="Start date">
              <DatePicker />
            </Form.Item>
            <Form.Item name="endDate" label="End date">
              <DatePicker disabled={watchCurrent} />
            </Form.Item>
            <Form.Item name="current" label="Current role">
              <Select
                style={{ width: 100 }}
                options={[
                  { value: false, label: 'No' },
                  { value: true, label: 'Yes' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Skill modal */}
      <Modal
        title={skillEditing ? 'Edit skill' : 'Add skill'}
        open={skillOpen}
        onOk={saveSkill}
        onCancel={() => setSkillOpen(false)}
        okText="Save"
        destroyOnClose
      >
        <Form form={skillForm} layout="vertical">
          <Form.Item name="ordinal" label="Order" initialValue={1} rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="name" label="Skill" rules={[{ required: true }]}>
            <Input placeholder="e.g. PostgreSQL" />
          </Form.Item>
          <Space>
            <Form.Item name="proficiency" label="Proficiency">
              <Select allowClear style={{ width: 160 }} options={PROFICIENCY} />
            </Form.Item>
            <Form.Item name="yearsExperience" label="Years">
              <InputNumber min={0} max={60} step={0.5} style={{ width: 110 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  )
}
