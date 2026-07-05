import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
  DatePicker,
  Checkbox,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, EyeOutlined } from '@ant-design/icons'
import { api } from '../api/client'
import { AttachmentUploader } from '../components/AttachmentUploader'
import dayjs from 'dayjs'

type ErCaseType = 'GRIEVANCE' | 'COMPLAINT' | 'INVESTIGATION' | 'DISCIPLINARY'
type ErCaseSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
type ErCaseStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'PENDING_INFO'
  | 'RESOLVED'
  | 'CLOSED'
  | 'ESCALATED'

interface ErCase {
  id: string
  caseNumber: string
  caseType: ErCaseType
  category: string
  severity: ErCaseSeverity
  status: ErCaseStatus
  employeeId?: string
  employeeName?: string
  employeeNo?: string
  description: string
  isConfidential: boolean
  isAnonymous: boolean
  ownerUsername?: string
  legalHold: boolean
  outcome?: string
  createdAt: string
  createdBy: string
  resolvedAt?: string
}

interface ErCaseNote {
  id: string
  caseId: string
  body: string
  isInternal: boolean
  createdAt: string
  createdBy: string
}

interface ErInvestigation {
  id: string
  caseId: string
  investigatorUsername: string
  status: 'OPEN' | 'CLOSED'
  findings?: string
  recommendation?: string
  openedAt: string
  closedAt?: string
}

interface ErInvestigationInterview {
  id: string
  investigationId: string
  intervieweeName: string
  intervieweeRole: string
  interviewDate: string
  summary: string
  createdBy: string
}

interface ErEvidence {
  id: string
  investigationId: string
  description: string
  attachmentId: string
  createdAt: string
  createdBy: string
}

const CASE_TYPE_OPTIONS: ErCaseType[] = [
  'GRIEVANCE',
  'COMPLAINT',
  'INVESTIGATION',
  'DISCIPLINARY',
]

const SEVERITY_OPTIONS: ErCaseSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const STATUS_OPTIONS: ErCaseStatus[] = [
  'OPEN',
  'IN_PROGRESS',
  'PENDING_INFO',
  'RESOLVED',
  'CLOSED',
  'ESCALATED',
]

const CASE_TYPE_COLOR: Record<ErCaseType, string> = {
  GRIEVANCE: 'blue',
  COMPLAINT: 'orange',
  INVESTIGATION: 'purple',
  DISCIPLINARY: 'red',
}

const SEVERITY_COLOR: Record<ErCaseSeverity, string> = {
  LOW: 'green',
  MEDIUM: 'gold',
  HIGH: 'orange',
  CRITICAL: 'red',
}

const STATUS_COLOR: Record<ErCaseStatus, string> = {
  OPEN: 'blue',
  IN_PROGRESS: 'processing',
  PENDING_INFO: 'warning',
  RESOLVED: 'success',
  CLOSED: 'default',
  ESCALATED: 'error',
}

export function ErCasesPage() {
  const { message } = AntdApp.useApp()
  const [cases, setCases] = useState<ErCase[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [selectedCase, setSelectedCase] = useState<ErCase | null>(null)
  const [filters, setFilters] = useState<{
    type?: ErCaseType
    severity?: ErCaseSeverity
    status?: ErCaseStatus
  }>({})

  const [form] = Form.useForm()

  const fetchCases = async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (filters.type) params.append('type', filters.type)
      if (filters.severity) params.append('severity', filters.severity)
      if (filters.status) params.append('status', filters.status)

      const { data } = await api.get(`/api/er/cases?${params}`)
      setCases(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load cases')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCases()
  }, [filters])

  const handleCreate = async (values: any) => {
    try {
      await api.post('/api/er/cases', {
        caseType: values.caseType,
        category: values.category,
        severity: values.severity,
        employeeId: values.employeeId || null,
        description: values.description,
        isConfidential: values.isConfidential ?? false,
        isAnonymous: values.isAnonymous ?? false,
      })
      message.success('Case created')
      setCreateOpen(false)
      form.resetFields()
      fetchCases()
    } catch (err: any) {
      message.error(err.message || 'Failed to create case')
    }
  }

  const openDetail = async (caseId: string) => {
    try {
      const { data } = await api.get(`/api/er/cases/${caseId}`)
      setSelectedCase(data)
      setDetailOpen(true)
    } catch (err: any) {
      message.error(err.message || 'Failed to load case details')
    }
  }

  const columns: ColumnsType<ErCase> = [
    {
      title: 'Case Number',
      dataIndex: 'caseNumber',
      key: 'caseNumber',
      width: 140,
    },
    {
      title: 'Type',
      dataIndex: 'caseType',
      key: 'caseType',
      width: 140,
      render: (type: ErCaseType) => <Tag color={CASE_TYPE_COLOR[type]}>{type}</Tag>,
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 140,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      width: 120,
      render: (sev: ErCaseSeverity) => <Tag color={SEVERITY_COLOR[sev]}>{sev}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: ErCaseStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: 'Employee',
      key: 'employee',
      width: 200,
      render: (_, rec) =>
        rec.isAnonymous
          ? 'Anonymous'
          : rec.employeeName
          ? `${rec.employeeName} (${rec.employeeNo})`
          : '—',
    },
    {
      title: 'Owner',
      dataIndex: 'ownerUsername',
      key: 'ownerUsername',
      width: 140,
      render: (val) => val || '—',
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (val) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, rec) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(rec.id)}>
          View
        </Button>
      ),
    },
  ]

  return (
    <Card
      title="ER Cases"
      extra={
        <Space>
          <Select
            placeholder="Type"
            allowClear
            style={{ width: 140 }}
            value={filters.type}
            onChange={(type) => setFilters({ ...filters, type })}
          >
            {CASE_TYPE_OPTIONS.map((t) => (
              <Select.Option key={t} value={t}>
                {t}
              </Select.Option>
            ))}
          </Select>
          <Select
            placeholder="Severity"
            allowClear
            style={{ width: 120 }}
            value={filters.severity}
            onChange={(severity) => setFilters({ ...filters, severity })}
          >
            {SEVERITY_OPTIONS.map((s) => (
              <Select.Option key={s} value={s}>
                {s}
              </Select.Option>
            ))}
          </Select>
          <Select
            placeholder="Status"
            allowClear
            style={{ width: 140 }}
            value={filters.status}
            onChange={(status) => setFilters({ ...filters, status })}
          >
            {STATUS_OPTIONS.map((st) => (
              <Select.Option key={st} value={st}>
                {st}
              </Select.Option>
            ))}
          </Select>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New Case
          </Button>
        </Space>
      }
    >
      <Table
        dataSource={cases}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1400 }}
      />

      {/* Create Modal */}
      <Modal
        title="New ER Case"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="caseType"
            label="Case Type"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select placeholder="Select type">
              {CASE_TYPE_OPTIONS.map((t) => (
                <Select.Option key={t} value={t}>
                  {t}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Harassment, Conduct, Performance" />
          </Form.Item>
          <Form.Item
            name="severity"
            label="Severity"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select placeholder="Select severity">
              {SEVERITY_OPTIONS.map((s) => (
                <Select.Option key={s} value={s}>
                  {s}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="employeeId" label="Employee (optional)">
            <Input placeholder="Employee UUID" />
          </Form.Item>
          <Form.Item name="isAnonymous" valuePropName="checked">
            <Checkbox>Anonymous case (hide employee details)</Checkbox>
          </Form.Item>
          <Form.Item name="isConfidential" valuePropName="checked">
            <Checkbox>Confidential</Checkbox>
          </Form.Item>
          <Form.Item
            name="description"
            label="Description"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={4} placeholder="Case details" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Drawer */}
      {selectedCase && (
        <ErCaseDetailDrawer
          caseData={selectedCase}
          open={detailOpen}
          onClose={() => {
            setDetailOpen(false)
            setSelectedCase(null)
            fetchCases()
          }}
        />
      )}
    </Card>
  )
}

interface ErCaseDetailDrawerProps {
  caseData: ErCase
  open: boolean
  onClose: () => void
}

function ErCaseDetailDrawer({ caseData, open, onClose }: ErCaseDetailDrawerProps) {
  const { message } = AntdApp.useApp()
  const [activeTab, setActiveTab] = useState('details')
  const [notes, setNotes] = useState<ErCaseNote[]>([])
  const [investigations, setInvestigations] = useState<ErInvestigation[]>([])

  useEffect(() => {
    if (open && caseData?.id) {
      fetchNotes()
      fetchInvestigations()
    }
  }, [open, caseData?.id])

  const fetchNotes = async () => {
    try {
      const { data } = await api.get(`/api/er/cases/${caseData.id}/notes`)
      setNotes(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load notes')
    }
  }

  const fetchInvestigations = async () => {
    try {
      const { data } = await api.get(`/api/er/cases/${caseData.id}/investigations`)
      setInvestigations(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load investigations')
    }
  }

  return (
    <Drawer title={`ER Case: ${caseData.caseNumber}`} open={open} onClose={onClose} width={800}>
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        <Tabs.TabPane tab="Details" key="details">
          <DetailsTab caseData={caseData} onUpdate={onClose} />
        </Tabs.TabPane>
        <Tabs.TabPane tab="Notes" key="notes">
          <NotesTab caseId={caseData.id} notes={notes} onRefresh={fetchNotes} />
        </Tabs.TabPane>
        <Tabs.TabPane tab="Investigation" key="investigation">
          <InvestigationTab
            caseId={caseData.id}
            investigations={investigations}
            onRefresh={fetchInvestigations}
          />
        </Tabs.TabPane>
      </Tabs>
    </Drawer>
  )
}

function DetailsTab({ caseData, onUpdate }: { caseData: ErCase; onUpdate: () => void }) {
  const { message } = AntdApp.useApp()
  const [statusForm] = Form.useForm()
  const [detailsForm] = Form.useForm()

  const handleStatusUpdate = async (values: any) => {
    try {
      await api.put(`/api/er/cases/${caseData.id}/status`, {
        status: values.status,
        outcome: values.outcome,
      })
      message.success('Status updated')
      onUpdate()
    } catch (err: any) {
      message.error(err.message || 'Failed to update status')
    }
  }

  const handleDetailsUpdate = async (values: any) => {
    try {
      await api.put(`/api/er/cases/${caseData.id}`, {
        category: values.category,
        severity: values.severity,
        description: values.description,
        ownerUsername: values.ownerUsername,
        legalHold: values.legalHold,
      })
      message.success('Details updated')
      onUpdate()
    } catch (err: any) {
      message.error(err.message || 'Failed to update details')
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card title="Status" size="small">
        <Form form={statusForm} layout="vertical" onFinish={handleStatusUpdate}>
          <Form.Item name="status" label="Status" initialValue={caseData.status}>
            <Select>
              {STATUS_OPTIONS.map((st) => (
                <Select.Option key={st} value={st}>
                  {st}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="outcome" label="Outcome" initialValue={caseData.outcome}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            Update Status
          </Button>
        </Form>
      </Card>

      <Card title="Case Details" size="small">
        <Form
          form={detailsForm}
          layout="vertical"
          onFinish={handleDetailsUpdate}
          initialValues={{
            category: caseData.category,
            severity: caseData.severity,
            description: caseData.description,
            ownerUsername: caseData.ownerUsername,
            legalHold: caseData.legalHold,
          }}
        >
          <Form.Item name="category" label="Category">
            <Input />
          </Form.Item>
          <Form.Item name="severity" label="Severity">
            <Select>
              {SEVERITY_OPTIONS.map((s) => (
                <Select.Option key={s} value={s}>
                  {s}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="ownerUsername" label="Owner (Username)">
            <Input placeholder="Assign to user" />
          </Form.Item>
          <Form.Item name="legalHold" label="Legal Hold" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            Update Details
          </Button>
        </Form>
      </Card>

      <Typography.Text type="secondary">
        Created: {dayjs(caseData.createdAt).format('YYYY-MM-DD HH:mm')} by {caseData.createdBy}
      </Typography.Text>
    </Space>
  )
}

function NotesTab({
  caseId,
  notes,
  onRefresh,
}: {
  caseId: string
  notes: ErCaseNote[]
  onRefresh: () => void
}) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm()

  const handleAddNote = async (values: any) => {
    try {
      await api.post(`/api/er/cases/${caseId}/notes`, {
        body: values.body,
        isInternal: values.isInternal ?? false,
      })
      message.success('Note added')
      form.resetFields()
      onRefresh()
    } catch (err: any) {
      message.error(err.message || 'Failed to add note')
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title="Add Note" size="small">
        <Form form={form} layout="vertical" onFinish={handleAddNote}>
          <Form.Item
            name="body"
            label="Note"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="isInternal" valuePropName="checked">
            <Checkbox>Internal only (not visible to employee)</Checkbox>
          </Form.Item>
          <Button type="primary" htmlType="submit">
            Add Note
          </Button>
        </Form>
      </Card>

      <div>
        {notes.map((note) => (
          <Card key={note.id} size="small" style={{ marginBottom: 8 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text>{note.body}</Typography.Text>
              {note.isInternal && <Tag color="orange">Internal</Tag>}
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {dayjs(note.createdAt).format('YYYY-MM-DD HH:mm')} — {note.createdBy}
              </Typography.Text>
            </Space>
          </Card>
        ))}
      </div>
    </Space>
  )
}

function InvestigationTab({
  caseId,
  investigations,
  onRefresh,
}: {
  caseId: string
  investigations: ErInvestigation[]
  onRefresh: () => void
}) {
  const { message } = AntdApp.useApp()
  const [openForm] = Form.useForm()
  const [closeForm] = Form.useForm()
  const [interviewForm] = Form.useForm()
  const [evidenceForm] = Form.useForm()
  const [openModal, setOpenModal] = useState(false)
  const [closeModal, setCloseModal] = useState<string | null>(null)
  const [interviewModal, setInterviewModal] = useState<string | null>(null)
  const [evidenceModal, setEvidenceModal] = useState<string | null>(null)
  const [interviews, setInterviews] = useState<ErInvestigationInterview[]>([])
  const [evidence, setEvidence] = useState<ErEvidence[]>([])

  const handleOpenInvestigation = async (values: any) => {
    try {
      await api.post(`/api/er/cases/${caseId}/investigations`, {
        investigatorUsername: values.investigatorUsername,
      })
      message.success('Investigation opened')
      openForm.resetFields()
      setOpenModal(false)
      onRefresh()
    } catch (err: any) {
      message.error(err.message || 'Failed to open investigation')
    }
  }

  const handleCloseInvestigation = async (investigationId: string, values: any) => {
    try {
      await api.put(`/api/er/investigations/${investigationId}/close`, {
        findings: values.findings,
        recommendation: values.recommendation,
      })
      message.success('Investigation closed')
      closeForm.resetFields()
      setCloseModal(null)
      onRefresh()
    } catch (err: any) {
      message.error(err.message || 'Failed to close investigation')
    }
  }

  const fetchInterviews = async (investigationId: string) => {
    try {
      const { data } = await api.get(`/api/er/investigations/${investigationId}/interviews`)
      setInterviews(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load interviews')
    }
  }

  const handleAddInterview = async (investigationId: string, values: any) => {
    try {
      await api.post(`/api/er/investigations/${investigationId}/interviews`, {
        intervieweeName: values.intervieweeName,
        intervieweeRole: values.intervieweeRole,
        interviewDate: values.interviewDate?.format('YYYY-MM-DD'),
        summary: values.summary,
      })
      message.success('Interview added')
      interviewForm.resetFields()
      fetchInterviews(investigationId)
    } catch (err: any) {
      message.error(err.message || 'Failed to add interview')
    }
  }

  const fetchEvidence = async (investigationId: string) => {
    try {
      const { data } = await api.get(`/api/er/investigations/${investigationId}/evidence`)
      setEvidence(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load evidence')
    }
  }

  const handleAddEvidence = async (investigationId: string, values: any) => {
    try {
      await api.post(`/api/er/investigations/${investigationId}/evidence`, {
        description: values.description,
        attachmentId: values.attachmentId,
      })
      message.success('Evidence added')
      evidenceForm.resetFields()
      fetchEvidence(investigationId)
    } catch (err: any) {
      message.error(err.message || 'Failed to add evidence')
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Button type="primary" onClick={() => setOpenModal(true)}>
        Open Investigation
      </Button>

      {investigations.map((inv) => (
        <Card
          key={inv.id}
          title={`Investigation: ${inv.investigatorUsername}`}
          size="small"
          extra={
            inv.status === 'OPEN' ? (
              <Button size="small" onClick={() => setCloseModal(inv.id)}>
                Close
              </Button>
            ) : (
              <Tag color="default">CLOSED</Tag>
            )
          }
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text>Opened: {dayjs(inv.openedAt).format('YYYY-MM-DD')}</Typography.Text>
            {inv.closedAt && (
              <Typography.Text>
                Closed: {dayjs(inv.closedAt).format('YYYY-MM-DD')}
              </Typography.Text>
            )}
            {inv.findings && (
              <div>
                <Typography.Text strong>Findings:</Typography.Text>
                <Typography.Paragraph>{inv.findings}</Typography.Paragraph>
              </div>
            )}
            {inv.recommendation && (
              <div>
                <Typography.Text strong>Recommendation:</Typography.Text>
                <Typography.Paragraph>{inv.recommendation}</Typography.Paragraph>
              </div>
            )}

            <Button
              size="small"
              onClick={() => {
                setInterviewModal(inv.id)
                fetchInterviews(inv.id)
              }}
            >
              Interviews
            </Button>
            <Button
              size="small"
              onClick={() => {
                setEvidenceModal(inv.id)
                fetchEvidence(inv.id)
              }}
            >
              Evidence
            </Button>
          </Space>
        </Card>
      ))}

      {/* Open Investigation Modal */}
      <Modal
        title="Open Investigation"
        open={openModal}
        onCancel={() => setOpenModal(false)}
        onOk={() => openForm.submit()}
      >
        <Form form={openForm} layout="vertical" onFinish={handleOpenInvestigation}>
          <Form.Item
            name="investigatorUsername"
            label="Investigator (Username)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* Close Investigation Modal */}
      <Modal
        title="Close Investigation"
        open={!!closeModal}
        onCancel={() => setCloseModal(null)}
        onOk={() => closeForm.submit()}
      >
        <Form
          form={closeForm}
          layout="vertical"
          onFinish={(values) => {
            if (closeModal) handleCloseInvestigation(closeModal, values)
          }}
        >
          <Form.Item
            name="findings"
            label="Findings"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name="recommendation"
            label="Recommendation"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Interviews Modal */}
      <Modal
        title="Interviews"
        open={!!interviewModal}
        onCancel={() => setInterviewModal(null)}
        footer={null}
        width={700}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Form
            form={interviewForm}
            layout="vertical"
            onFinish={(values) => {
              if (interviewModal) handleAddInterview(interviewModal, values)
            }}
          >
            <Form.Item
              name="intervieweeName"
              label="Interviewee Name"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="intervieweeRole"
              label="Role"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="interviewDate"
              label="Date"
              rules={[{ required: true, message: 'Required' }]}
            >
              <DatePicker />
            </Form.Item>
            <Form.Item
              name="summary"
              label="Summary"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input.TextArea rows={3} />
            </Form.Item>
            <Button type="primary" htmlType="submit">
              Add Interview
            </Button>
          </Form>

          <div>
            {interviews.map((int) => (
              <Card key={int.id} size="small" style={{ marginBottom: 8 }}>
                <Typography.Text strong>
                  {int.intervieweeName} ({int.intervieweeRole})
                </Typography.Text>
                <br />
                <Typography.Text type="secondary">
                  {dayjs(int.interviewDate).format('YYYY-MM-DD')}
                </Typography.Text>
                <Typography.Paragraph style={{ marginTop: 8 }}>
                  {int.summary}
                </Typography.Paragraph>
              </Card>
            ))}
          </div>
        </Space>
      </Modal>

      {/* Evidence Modal */}
      <Modal
        title="Evidence"
        open={!!evidenceModal}
        onCancel={() => setEvidenceModal(null)}
        footer={null}
        width={700}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Form
            form={evidenceForm}
            layout="vertical"
            onFinish={(values) => {
              if (evidenceModal) handleAddEvidence(evidenceModal, values)
            }}
          >
            <Form.Item
              name="description"
              label="Description"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="attachmentId"
              label="Attachment ID"
              rules={[{ required: true, message: 'Required' }]}
            >
              <Input placeholder="UUID of uploaded attachment" />
            </Form.Item>
            <Button type="primary" htmlType="submit">
              Add Evidence
            </Button>
          </Form>

          <Typography.Text type="secondary">
            Use AttachmentUploader to upload files first, then paste the attachment ID here.
          </Typography.Text>

          {evidenceModal && (
            <AttachmentUploader
              ownerModule="employee_relations"
              ownerEntity="erevidence"
              ownerId={evidenceModal}
            />
          )}

          <div>
            {evidence.map((ev) => (
              <Card key={ev.id} size="small" style={{ marginBottom: 8 }}>
                <Typography.Text>{ev.description}</Typography.Text>
                <br />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {dayjs(ev.createdAt).format('YYYY-MM-DD HH:mm')} — {ev.createdBy}
                </Typography.Text>
              </Card>
            ))}
          </div>
        </Space>
      </Modal>
    </Space>
  )
}
