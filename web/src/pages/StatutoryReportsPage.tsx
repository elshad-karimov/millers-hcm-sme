import { useEffect, useState } from 'react'
import {
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
  Switch,
  Table,
  Tag,
  App as AntdApp,
  Collapse,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  complianceApi,
  type StatutoryReportTemplate,
  type StatutoryReportSubmission,
  type SubmissionStatus,
} from '../api/compliance'

const STATUS_COLOR: Record<SubmissionStatus, string> = {
  DRAFT: 'default',
  GENERATED: 'blue',
  SUBMITTED: 'orange',
  ACCEPTED: 'green',
  REJECTED: 'red',
}

export function StatutoryReportsPage() {
  const { message } = AntdApp.useApp()
  const [templates, setTemplates] = useState<StatutoryReportTemplate[]>([])
  const [submissions, setSubmissions] = useState<StatutoryReportSubmission[]>([])
  const [loading, setLoading] = useState(true)
  const [templateModalOpen, setTemplateModalOpen] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<StatutoryReportTemplate | null>(null)
  const [templateForm] = Form.useForm<StatutoryReportTemplate>()
  const [submissionModalOpen, setSubmissionModalOpen] = useState(false)
  const [submissionForm] = Form.useForm()
  const [statusModalOpen, setStatusModalOpen] = useState(false)
  const [editingSubmission, setEditingSubmission] = useState<StatutoryReportSubmission | null>(null)
  const [statusForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const [t, s] = await Promise.all([
        complianceApi.listTemplates(),
        complianceApi.listSubmissions(),
      ])
      setTemplates(t)
      setSubmissions(s)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load data',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleTemplateSubmit = async () => {
    const values = await templateForm.validateFields()
    try {
      if (editingTemplate) {
        await complianceApi.updateTemplate(editingTemplate.id, { ...editingTemplate, ...values })
        message.success('Template updated')
      } else {
        await complianceApi.createTemplate(values)
        message.success('Template created')
      }
      setTemplateModalOpen(false)
      templateForm.resetFields()
      setEditingTemplate(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to save template',
      )
    }
  }

  const handleDeleteTemplate = async (id: string) => {
    try {
      await complianceApi.deleteTemplate(id)
      message.success('Template deleted')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to delete template',
      )
    }
  }

  const handleCreateSubmission = async () => {
    const values = await submissionForm.validateFields()
    try {
      await complianceApi.createSubmission(
        values.templateId,
        values.periodStart.format('YYYY-MM-DD'),
        values.periodEnd.format('YYYY-MM-DD'),
      )
      message.success('Submission created')
      setSubmissionModalOpen(false)
      submissionForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to create submission',
      )
    }
  }

  const handleGenerate = async (id: string) => {
    try {
      await complianceApi.generateSubmission(id)
      message.success('Report generated')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generation failed',
      )
    }
  }

  const handleUpdateStatus = async () => {
    if (!editingSubmission) return
    const values = await statusForm.validateFields()
    try {
      await complianceApi.updateSubmissionStatus(
        editingSubmission.id,
        values.status,
        values.responseNotes,
      )
      message.success('Status updated')
      setStatusModalOpen(false)
      statusForm.resetFields()
      setEditingSubmission(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to update status',
      )
    }
  }

  const handleDownload = async (attachmentId: string, template: StatutoryReportTemplate) => {
    try {
      await complianceApi.downloadSubmission(
        attachmentId,
        `${template.code}.${template.fileFormat.toLowerCase()}`,
      )
    } catch (err) {
      message.error('Download failed')
    }
  }

  const templateColumns: ColumnsType<StatutoryReportTemplate> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Frequency',
      dataIndex: 'frequency',
      width: 120,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Format',
      dataIndex: 'fileFormat',
      width: 80,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    { title: 'Due day', dataIndex: 'dueDay', width: 90 },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: '',
      width: 150,
      render: (_, r) => (
        <Space size="small">
          <Button
            size="small"
            onClick={() => {
              setEditingTemplate(r)
              templateForm.setFieldsValue(r)
              setTemplateModalOpen(true)
            }}
          >
            Edit
          </Button>
          <Popconfirm
            title="Delete this template?"
            onConfirm={() => handleDeleteTemplate(r.id)}
          >
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const submissionColumns: ColumnsType<StatutoryReportSubmission> = [
    {
      title: 'Template',
      dataIndex: 'templateId',
      render: (tid: string) => templates.find((t) => t.id === tid)?.name ?? tid.substring(0, 8),
    },
    {
      title: 'Period',
      render: (_, r) => `${dayjs(r.periodStart).format('YYYY-MM-DD')} – ${dayjs(r.periodEnd).format('YYYY-MM-DD')}`,
      width: 200,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: SubmissionStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: 'Generated',
      dataIndex: 'generatedAt',
      width: 180,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: '',
      width: 250,
      render: (_, r) => {
        const template = templates.find((t) => t.id === r.templateId)
        return (
          <Space size="small">
            {r.status === 'DRAFT' && (
              <Button size="small" type="primary" onClick={() => handleGenerate(r.id)}>
                Generate
              </Button>
            )}
            {r.attachmentId && template && (
              <Button size="small" onClick={() => handleDownload(r.attachmentId!, template)}>
                Download
              </Button>
            )}
            <Button
              size="small"
              onClick={() => {
                setEditingSubmission(r)
                statusForm.setFieldsValue({ status: r.status, responseNotes: r.responseNotes })
                setStatusModalOpen(true)
              }}
            >
              Status
            </Button>
          </Space>
        )
      },
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Statutory Reports"
        extra={
          <Space>
            <Button type="primary" onClick={() => setTemplateModalOpen(true)}>
              New Template
            </Button>
            <Button onClick={() => setSubmissionModalOpen(true)}>New Submission</Button>
          </Space>
        }
      >
        <Collapse
          defaultActiveKey={['templates']}
          items={[
            {
              key: 'templates',
              label: 'Report Templates',
              children: (
                <Table
                  rowKey="id"
                  columns={templateColumns}
                  dataSource={templates}
                  loading={loading}
                  pagination={false}
                  size="small"
                />
              ),
            },
            {
              key: 'submissions',
              label: 'Submissions',
              children: (
                <Table
                  rowKey="id"
                  columns={submissionColumns}
                  dataSource={submissions}
                  loading={loading}
                  pagination={{ pageSize: 20 }}
                  size="small"
                />
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={editingTemplate ? 'Edit Template' : 'New Template'}
        open={templateModalOpen}
        onCancel={() => {
          setTemplateModalOpen(false)
          templateForm.resetFields()
          setEditingTemplate(null)
        }}
        onOk={handleTemplateSubmit}
        width={600}
      >
        <Form form={templateForm} layout="vertical">
          <Form.Item
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Code is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="frequency"
            label="Frequency"
            rules={[{ required: true, message: 'Frequency is required' }]}
          >
            <Select>
              <Select.Option value="MONTHLY">Monthly</Select.Option>
              <Select.Option value="QUARTERLY">Quarterly</Select.Option>
              <Select.Option value="ANNUAL">Annual</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="fileFormat"
            label="File Format"
            rules={[{ required: true, message: 'Format is required' }]}
          >
            <Select>
              <Select.Option value="XLSX">XLSX</Select.Option>
              <Select.Option value="CSV">CSV</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="dueDay"
            label="Due Day (of month)"
            rules={[{ required: true, message: 'Due day is required' }]}
          >
            <InputNumber min={1} max={31} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="New Submission"
        open={submissionModalOpen}
        onCancel={() => {
          setSubmissionModalOpen(false)
          submissionForm.resetFields()
        }}
        onOk={handleCreateSubmission}
      >
        <Form form={submissionForm} layout="vertical">
          <Form.Item
            name="templateId"
            label="Template"
            rules={[{ required: true, message: 'Template is required' }]}
          >
            <Select placeholder="Select a template">
              {templates.map((t) => (
                <Select.Option key={t.id} value={t.id}>
                  {t.code} — {t.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="periodStart"
            label="Period Start"
            rules={[{ required: true, message: 'Start date is required' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="periodEnd"
            label="Period End"
            rules={[{ required: true, message: 'End date is required' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Update Submission Status"
        open={statusModalOpen}
        onCancel={() => {
          setStatusModalOpen(false)
          statusForm.resetFields()
          setEditingSubmission(null)
        }}
        onOk={handleUpdateStatus}
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item
            name="status"
            label="Status"
            rules={[{ required: true, message: 'Status is required' }]}
          >
            <Select>
              <Select.Option value="DRAFT">DRAFT</Select.Option>
              <Select.Option value="GENERATED">GENERATED</Select.Option>
              <Select.Option value="SUBMITTED">SUBMITTED</Select.Option>
              <Select.Option value="ACCEPTED">ACCEPTED</Select.Option>
              <Select.Option value="REJECTED">REJECTED</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="responseNotes" label="Response Notes">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
